/*
 * Copyright (c) 2013-2016 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.collectors
package scalastream
package sinks

import java.util.Properties

import org.apache.kafka.clients.producer._

import model._

/**
 * Kafka Sink for the Scala collector
 */
class KafkaSink(config: SinkConfig, inputType: InputType.InputType) extends Sink {

  // Records must not exceed MaxBytes - 1MB
  val MaxBytes = 1000000L

  private val topicName = inputType match {
    case InputType.Good => config.kafka.topic.good
    case InputType.Bad  => config.kafka.topic.bad
  }

  private var kafkaProducer = createProducer

  /**
   * Creates a new Kafka Producer with the given
   * configuration options
   *
   * @return a new Kafka Producer
   */
  private def createProducer: KafkaProducer[String, Array[Byte]] = {

    log.info(s"Create Kafka Producer to brokers: ${config.kafka.brokers}")

    val props = new Properties()
    props.put("bootstrap.servers", config.kafka.brokers)
    props.put("acks", "all")
    props.put("retries", "0")
    props.put("batch.size", config.buffer.byteLimit.toString)
    props.put("linger.ms", config.buffer.timeLimit.toString)
    props.put("key.serializer", 
      "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", 
      "org.apache.kafka.common.serialization.ByteArraySerializer")

    new KafkaProducer[String, Array[Byte]](props)
  }

  /**
   * Store raw events to the topic
   *
   * @param events The list of events to send
   * @param key The partition key to use
   */
  override def storeRawEvents(events: List[Array[Byte]], key: String): List[Array[Byte]] = {
    log.debug(s"Writing ${events.size} Thrift records to Kafka topic ${topicName} at key ${key}")
    events.foreach { event =>
      kafkaProducer.send(
        new ProducerRecord(topicName, key, event),
        new Callback {
          override def onCompletion(metadata: RecordMetadata, e: Exception): Unit =
            if (e != null) log.error(s"Sending event failed: ${e.getMessage}")
        }
      )
    }
    Nil
  }

  override def getType = Kafka
}
