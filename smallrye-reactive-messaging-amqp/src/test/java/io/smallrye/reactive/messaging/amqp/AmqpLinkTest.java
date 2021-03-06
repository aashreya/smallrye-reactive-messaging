package io.smallrye.reactive.messaging.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorLiteral;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.After;
import org.junit.Test;

import io.smallrye.config.SmallRyeConfigProviderResolver;

public class AmqpLinkTest extends AmqpTestBase {

    private WeldContainer container;

    @After
    public void cleanup() {
        if (container != null) {
            container.shutdown();
        }

        MapBasedConfig.clear();
        SmallRyeConfigProviderResolver.instance().releaseConfig(ConfigProvider.getConfig());
    }

    @Test
    public void test() {
        Weld weld = new Weld();

        weld.addBeanClass(MyProducer.class);
        weld.addBeanClass(MyConsumer.class);

        new MapBasedConfig()
                .put("mp.messaging.outgoing.people-out.connector", AmqpConnector.CONNECTOR_NAME)
                .put("mp.messaging.outgoing.people-out.address", "people")
                .put("mp.messaging.outgoing.people-out.link-name", "people")
                .put("mp.messaging.outgoing.people-out.host", host)
                .put("mp.messaging.outgoing.people-out.port", port)
                .put("mp.messaging.outgoing.people-out.durable", true)
                .put("amqp-username", username)
                .put("amqp-password", password)

                .put("mp.messaging.incoming.people-in.connector", AmqpConnector.CONNECTOR_NAME)
                .put("mp.messaging.incoming.people-in.address", "people")
                .put("mp.messaging.incoming.people-in.link-name", "people")
                .put("mp.messaging.incoming.people-in.host", host)
                .put("mp.messaging.incoming.people-in.port", port)
                .put("mp.messaging.incoming.people-in.durable", true)

                .write();

        container = weld.initialize();
        AmqpConnector connector = container.getBeanManager().createInstance().select(AmqpConnector.class,
                ConnectorLiteral.of(AmqpConnector.CONNECTOR_NAME)).get();
        await().until(() -> connector.isReady("people-in"));

        MyProducer producer = container.getBeanManager().createInstance().select(MyProducer.class).get();
        producer.run();

        MyConsumer consumer = container.getBeanManager().createInstance().select(MyConsumer.class).get();
        await()
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> consumer.list().size() == 3);
        assertThat(consumer.list()).containsExactly("Luke", "Leia", "Han");
    }

    @ApplicationScoped
    public static class MyProducer {

        @Inject
        @Channel("people-out")
        Emitter<String> emitter;

        public void run() {
            emitter.send("Luke");
            emitter.send("Leia");
            emitter.send("Han");
        }
    }

    @ApplicationScoped
    public static class MyConsumer {

        private List<String> list = new CopyOnWriteArrayList<>();

        @Incoming("people-in")
        public void getPeople(String s) {
            list.add(s);
        }

        public List<String> list() {
            return list;
        }
    }

}
