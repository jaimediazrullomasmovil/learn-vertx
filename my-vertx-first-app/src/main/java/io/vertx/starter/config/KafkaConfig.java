package io.vertx.starter.config;

import java.util.HashMap;
import java.util.Map;

import static org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.*;
import static org.apache.kafka.clients.producer.ProducerConfig.*;

public class KafkaConfig {


    public static final String BOOTSTRAP_SERVERS = "localhost:9092";
    public static final String KEY_DESERIALIZER = org.apache.kafka.common.serialization.StringDeserializer.class.getCanonicalName();
    public static final String VALUE_DESERIALIZER = io.vertx.kafka.client.serialization.JsonObjectDeserializer.class.getCanonicalName();
    public static final String KEY_SERIALIZER = org.apache.kafka.common.serialization.StringSerializer.class.getCanonicalName();
    public static final String VALUE_SERIALIZER = io.vertx.kafka.client.serialization.JsonObjectSerializer.class.getCanonicalName();

    public static Map<String, String> producer() {
        Map<String, String> config = new HashMap<>();
        config.put(BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        config.put(KEY_SERIALIZER_CLASS_CONFIG, KEY_SERIALIZER);
        config.put(VALUE_SERIALIZER_CLASS_CONFIG, VALUE_SERIALIZER);
        config.put(ACKS_CONFIG, "1");
        return config;
    }

    public static Map<String, String> consumer(String group) {
        Map<String, String> config = new HashMap<>();
        config.put(BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        config.put(KEY_DESERIALIZER_CLASS_CONFIG, KEY_DESERIALIZER);
        config.put(VALUE_DESERIALIZER_CLASS_CONFIG, VALUE_DESERIALIZER);
        config.put(AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ENABLE_AUTO_COMMIT_CONFIG, "true");
        config.put(GROUP_ID_CONFIG, group);
        return config;
    }
}
