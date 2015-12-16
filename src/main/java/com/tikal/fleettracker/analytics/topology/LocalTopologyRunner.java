package com.tikal.fleettracker.analytics.topology;

import java.util.Properties;

import com.tikal.fleettracker.analytics.topology.bolts.GpsParserBolt;
import com.tikal.fleettracker.analytics.topology.bolts.RevGeocodeBolt;
import com.tikal.fleettracker.analytics.topology.bolts.SegmentationBolt;
import com.tikal.fleettracker.analytics.topology.spout.TextFileSpout;

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
//	final static int intervalWindow = 2;
	
	private static final String kafka_brokers_lists = System.getProperty("kafka_brokers_lists");
	private final static String zkHosts = System.getProperty("zkHosts");
	private final static String kafkaGpsTopicName=System.getProperty("kafkaGpsTopicName");
	private final static String kafkaSegmentsTopicName=System.getProperty("kafkaSegmentsTopicName");
	private final static String geoCoderUrl = System.getProperty("geoCoderUrl");
	private static int speedTheshold = Integer.valueOf(System.getProperty("speedTheshold"));
	
	private static String redisHost = System.getProperty("redisHost");
	
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
//		segmentationConfig.put(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS, intervalWindow);
		segmentationConfig.put("speedTheshold", speedTheshold );
		segmentationConfig.put("redisHost", redisHost );
		segmentationConfig.put("geoCoderUrl", geoCoderUrl );
		
		
		
		final TopologyBuilder builder = new TopologyBuilder();

		builder.setSpout("gpsSpout", getKafkaSpout());
//		builder.setSpout("gpsSpout", getTextFileSpoutSpout());
		builder.setBolt("gpsParserBolt", new GpsParserBolt()).shuffleGrouping("gpsSpout");
		builder.setBolt("segmentation-bolt", new SegmentationBolt()).fieldsGrouping("gpsParserBolt",new Fields("vehicleId")).addConfigurations(segmentationConfig);
		builder.setBolt("reverse-geocode-bolt", new RevGeocodeBolt()).fieldsGrouping("segmentation-bolt",new Fields("vehicleId")).addConfigurations(segmentationConfig);		
		builder.setBolt("kafkaProducer",getKafkaBolt()).fieldsGrouping("reverse-geocode-bolt",new Fields("vehicleId")).addConfigurations(getKafkaBoltConfig());
		

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
		.withTupleToKafkaMapper(new FieldNameBasedTupleToKafkaMapper<String, String>("vehicleId","segment"));
	}
	

	private static Config getKafkaBoltConfig() {
		final Config config = new Config();
		final Properties props = new Properties();
        props.put("metadata.broker.list", kafka_brokers_lists);
        props.put("request.required.acks", "1");
        props.put("serializer.class", "kafka.serializer.StringEncoder");
        config.put(TridentKafkaState.KAFKA_BROKER_PROPERTIES, props);
        return config;
    }
}