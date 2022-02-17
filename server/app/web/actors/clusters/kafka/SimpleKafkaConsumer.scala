package web.actors.clusters.kafka
import java.util.Properties

import org.apache.kafka.clients.admin.KafkaAdminClient
import org.apache.kafka.clients.consumer.KafkaConsumer
import scala.jdk.CollectionConverters._

import scala.concurrent.Future
class SimpleKafkaConsumer(bootstrapServer: String, admin: KafkaAdminClient) {

  def getConsumer(): KafkaConsumer[String, String] = {
    val props = new Properties()
    props.setProperty("bootstrap.servers", bootstrapServer)
    props.setProperty("group.id", "simple-kafka-consumer")
    props.setProperty("enable.auto.commit", "true")
    props.setProperty("auto.commit.interval.ms", "1000")
    props.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    new KafkaConsumer[String, String](props)
  }


}
