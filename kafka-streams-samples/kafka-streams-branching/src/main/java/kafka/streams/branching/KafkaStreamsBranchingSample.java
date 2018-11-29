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

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.handler.annotation.SendTo;

import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@SpringBootApplication
public class KafkaStreamsBranchingSample {

	public static void main(String[] args) {
		SpringApplication.run(KafkaStreamsBranchingSample.class, args);
	}

	private static final Predicate<Object, WordCount> isEnglish = (k, v) -> {
		boolean english = v.word.equals("english");
		if (english) {
			log.info("Send message for English Topic");
		}
		return english;
	};
	private static final Predicate<Object, WordCount> isFrench =  (k, v) -> v.word.equals("french");
	private static final Predicate<Object, WordCount> isSpanish = (k, v) -> v.word.equals("spanish");
	private static final Predicate<Object, WordCount> isUnknownWord = (k, v) -> !(isEnglish.test(k, v) && isSpanish.test(k, v) && isFrench.test(k, v));

	@EnableBinding(KStreamProcessorX.class)
	public static class WordCountProcessorApplication {

		@Autowired
		private TimeWindows timeWindows;

		@StreamListener("input")
		@SendTo({"output1","output2","output3","output4"})
		public KStream<?, WordCount>[] process(KStream<Object, String> input) {
			return input
					.flatMapValues(value -> Arrays.asList(value.toLowerCase().split("\\W+")))
					.groupBy((key, value) -> value)
					.windowedBy(timeWindows)
					.count(Materialized.as("WordCounts-1"))
					.toStream()
					.map((key, value) -> new KeyValue<>(null, new WordCount(key.key(), value, new Date(key.window().start()), new Date(key.window().end()))))
					.branch(isEnglish, isFrench, isSpanish, isUnknownWord);
		}

		//@StreamListener("inputAggregation")
		public void processAggregation(KStream<Object, String> input) {
			input
					.flatMapValues(value -> Arrays.asList(value.toLowerCase().split("\\W+")))
					.groupBy((key, value) -> value)
					.windowedBy(timeWindows)
					.aggregate(WordAggregate::new, aggregateValues())
					.toStream((key, value) -> new WordCount(value.getWordType().name(), value.getCount()))
					.filter(((key, value) -> isEnglish.test(value, key)))
					.to("output1");
		}

		private Aggregator<String, String, WordAggregate> aggregateValues() {
			return (key, value, aggregate) -> {
				if (isFrench.test(key, new WordCount(value))) {
					aggregate = createWordAggregate(aggregate, WordType.FRENCH);
				} else if (isSpanish.test(key, new WordCount(value))) {
					aggregate = createWordAggregate(aggregate, WordType.SPANISH);
				} else if (isEnglish.test(key, new WordCount(value))) {
					aggregate = createWordAggregate(aggregate, WordType.ENGLISH);
				} else {
					aggregate = createWordAggregate(aggregate, WordType.UNKNOWN);
				}
				aggregate.increment();
				log.debug("AggregateValues: {}", aggregate);
				return aggregate;
			};
		}

		private WordAggregate createWordAggregate(WordAggregate wordAggregate, WordType wordType) {
			if (wordAggregate.getWordType() != wordType) {
				wordAggregate = new WordAggregate(wordType);
			}
			return wordAggregate;
		}
	}

	@StreamListener("input4")
	public void unknownWords(String input) {
		log.info("unknownWords: {}", input);
	}

	interface KStreamProcessorX {

		@Input("input")
		KStream<?, ?> input();

		@Input("inputAggregation")
		KStream<?, ?> inputAggregation();

		@Output("output1")
		KStream<?, ?> output1();

		@Output("output2")
		KStream<?, ?> output2();

		@Output("output3")
		KStream<?, ?> output3();

		@Output("output4")
		KStream output4();

		@Input("input4")
		SubscribableChannel input4();
	}

	enum WordType {
		FRENCH,
		ENGLISH,
		SPANISH,
		UNKNOWN;
	}

	@NoArgsConstructor
	@RequiredArgsConstructor
	static class WordAggregate {

		@NonNull
		@Setter
		@Getter
		private WordType wordType;

		private AtomicLong counter = new AtomicLong();

		public void increment() {
			counter.incrementAndGet();
		}

		public Long getCount() {
			return counter.get();
		}
	}

	static class WordCount {

		private String word;

		private long count;

		private Date start;

		private Date end;

		WordCount(String word) {
			this.word = word;
		}

		WordCount(String word, long count) {
			this(word);
			this.count = count;
		}

		WordCount(String word, long count, Date start, Date end) {
			this.word = word;
			this.count = count;
			this.start = start;
			this.end = end;
		}

		public String getWord() {
			return word;
		}

		public void setWord(String word) {
			this.word = word;
		}

		public long getCount() {
			return count;
		}

		public void setCount(long count) {
			this.count = count;
		}

		public Date getStart() {
			return start;
		}

		public void setStart(Date start) {
			this.start = start;
		}

		public Date getEnd() {
			return end;
		}

		public void setEnd(Date end) {
			this.end = end;
		}
	}
}
