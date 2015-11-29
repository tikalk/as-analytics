package com.tikal.angelsense.analytics.topology;

import java.util.Properties;

import com.tikal.angelsense.analytics.topology.bolts.GpsParserBolt;
import com.tikal.angelsense.analytics.topology.bolts.SegmentationBolt;
import com.tikal.angelsense.analytics.topology.spout.TextFileSpout;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.spout.SchemeAsMultiScheme;
import backtype.storm.topology.IRichSpout;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.tuple.Fields;
import storm.kafka.KafkaSpout;
import storm.kafka.SpoutConfig;
import storm.kafka.StringScheme;
import storm.kafka.ZkHosts;
import storm.kafka.bolt.KafkaBolt;
import storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import storm.kafka.bolt.selector.DefaultTopicSelector;
import storm.kafka.trident.TridentKafkaState;


public class LocalTopologyRunner {
	final static int intervalWindow = 2;
	
	final static String zkHosts = "localhost";
	final static String kafkaGpsTopicName="as-gps";
	final static String kafkaSegmentsTopicName="as-segments";
	private static int speedTheshold = 5;
	
	private static String redisHost = "localhost";
	
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LocalTopologyRunner.class);
	public static void main(final String[] args) {
		
		final TopologyBuilder builder = buildTopolgy();


		final Config config = new Config();
		config.setDebug(false);

		final LocalCluster localCluster = new LocalCluster();
		localCluster.submitTopology("local-gps-anaytics", config, builder.createTopology());
		logger.info("##############Topology submitted################");
	}

	private static TopologyBuilder buildTopolgy() {
		
		

		final Config segmentationConfig = new Config();
		segmentationConfig.put(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS, intervalWindow);
		segmentationConfig.put("speedTheshold", speedTheshold );
		segmentationConfig.put("redisHost", redisHost );
		
		
		final TopologyBuilder builder = new TopologyBuilder();

//		builder.setSpout("gpsSpout", getKafkaSpout());
		builder.setSpout("gpsSpout", getTextFileSpoutSpout());
		builder.setBolt("gpsParserBolt", new GpsParserBolt()).shuffleGrouping("gpsSpout");
		builder.setBolt("segmentation-bolt", new SegmentationBolt()).fieldsGrouping("gpsParserBolt",new Fields("angelId")).addConfigurations(segmentationConfig);
		builder.setBolt("kafkaProducer",getKafkaBolt()).fieldsGrouping("segmentation-bolt",new Fields("angelId")).addConfigurations(getKafkaBoltConfig());
		

		return builder;
	}

	private static IRichSpout getTextFileSpoutSpout() {
		final TextFileSpout textFileSpout = new TextFileSpout();
		return textFileSpout;
	}

	private static IRichSpout getKafkaSpout() {
		final SpoutConfig kafkaConfig = new SpoutConfig(new ZkHosts(zkHosts), kafkaGpsTopicName, "", "storm");
		kafkaConfig.startOffsetTime=kafka.api.OffsetRequest.LatestTime();
		kafkaConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
		final KafkaSpout kafkaSpout = new KafkaSpout(kafkaConfig);
		return kafkaSpout;
	}

	private static KafkaBolt<String, String> getKafkaBolt() {
		return new KafkaBolt<String,String>()
		.withTopicSelector(new DefaultTopicSelector(kafkaSegmentsTopicName))
		.withTupleToKafkaMapper(new FieldNameBasedTupleToKafkaMapper<String, String>("angelId","segment"));
	}
	
//	private static KafkaBolt<String, String> getKafkaBolt() {
//		return new KafkaBolt<String,String>()
//		.withTopicSelector(new DefaultTopicSelector("locations-topic"))
//		.withTupleToKafkaMapper(new TupleToKafkaMapper<String, String>() {			
//			@Override
//			public String getMessageFromTuple(final Tuple tuple) {
//				try {
//					final String key = "checkins-" +tuple.getLongByField("time-interval")+"@"+tuple.getStringByField("city");
//					return key+"="+objectMapper.writeValueAsString(tuple.getValueByField("locationsList"));
//				} catch (final IOException e) {
//					throw new RuntimeException(e);
//				}
//			}			
//			@Override
//			public String getKeyFromTuple(final Tuple tuple) {
//				return "checkins-" +tuple.getLongByField("time-interval")+"@"+tuple.getStringByField("city");				
//			}
//		});
//	}

//	private static SpoutConfig getSoutConfig() {
//		final SpoutConfig kafkaConfig = new SpoutConfig(new ZkHosts(zkHosts), kafkaGpsTopicName, "", "storm");
//		kafkaConfig.startOffsetTime=kafka.api.OffsetRequest.LatestTime();
//		return kafkaConfig;
//	}
	
	
	private static Config getKafkaBoltConfig() {
		final Config config = new Config();
		final Properties props = new Properties();
        props.put("metadata.broker.list", "localhost:9092");
        props.put("request.required.acks", "1");
        props.put("serializer.class", "kafka.serializer.StringEncoder");
        config.put(TridentKafkaState.KAFKA_BROKER_PROPERTIES, props);
        return config;
    }
}