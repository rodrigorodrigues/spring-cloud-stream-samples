package kafka.simple.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Sink;

@SpringBootApplication
@EnableBinding(Sink.class)
public class KafkaSimpleConsumerApplication {
    private static final Logger logger = LoggerFactory.getLogger(KafkaSimpleConsumerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(KafkaSimpleConsumerApplication.class, args);
    }

    @StreamListener("input")
    public void unknownWords(String input) {
        logger.info("unknownWords: {}", input);
    }
}
