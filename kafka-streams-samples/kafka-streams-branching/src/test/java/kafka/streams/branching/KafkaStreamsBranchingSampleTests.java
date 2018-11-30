/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kafka.streams.branching;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.AbstractAssert;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.rule.EmbeddedKafkaRule;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Java6Assertions.assertThat;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
		"spring.cloud.stream.kafka.streams.timeWindow.length=60000",
		"logging.level.kafka.streams.branching=debug"
})
@DirtiesContext
public class KafkaStreamsBranchingSampleTests {

	@ClassRule
	public static EmbeddedKafkaRule embeddedKafkaRule = new EmbeddedKafkaRule(1, false, 1, "english-counts", "french-counts",
			"spanish-counts", "unknown-word", "words", "aggregation-words");

	@Autowired
	private KafkaTemplate kafkaTemplate;

	@ClassRule
	public static OutputCapture outputCapture = new OutputCapture();

	@After
	public void tearDown() {
		outputCapture.reset();
	}

	@BeforeClass
	public static void initClass() {
		EmbeddedKafkaBroker embeddedKafka = embeddedKafkaRule.getEmbeddedKafka();
		System.setProperty("BROKERS", embeddedKafka.getBrokersAsString());
		System.setProperty("ZKNODES", embeddedKafka.getZookeeperConnectionString());
	}

	@AfterClass
	public static void endClass() {
		embeddedKafkaRule.getEmbeddedKafka().destroy();
	}

	@Test
	public void shouldTestCountWordsTopicWithKafkaStream() throws Exception {
		kafkaTemplate.send("words", "English Spanish Portuguese Test");

		waitAssert(() -> assertThat(outputCapture.toString()).contains("Send message for English Topic"));
		waitAssert(() -> assertThat(outputCapture.toString()).contains("unknownWords: {\"word\":\"portuguese\""));
		waitAssert(() -> assertThat(outputCapture.toString()).contains("unknownWords: {\"word\":\"test\""));
	}

	@Test
	public void shouldTestAggregationWordsTopicWithKafkaStream() throws Exception {
		kafkaTemplate.send("words", "English Spanish Portuguese Test");

		waitAssert(() -> assertThat(outputCapture.toString()).contains("Send message for English Topic"));
		waitAssert(() -> assertThat(outputCapture.toString()).contains("unknownWords: {\"word\":\"portuguese,test\""));
	}

	private void waitAssert(Supplier<AbstractAssert> supplier) throws InterruptedException {
		int count = 0;
		while (true) {
			try {
				supplier.get();
				break;
			} catch (Throwable e) {
				if (count++ >= 3) {
					throw e;
				}
				TimeUnit.MILLISECONDS.sleep(500);
				log.debug("Retry assert after: {}", new Date());
			}
		}
	}

}
