package com.tikal.angelsense.analytics.topology.bolts;

import java.util.Map;

import com.goebl.david.Webb;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import backtype.storm.task.TopologyContext;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;

public class RevGeocodeBolt extends BaseBasicBolt {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RevGeocodeBolt.class);
	
	private Webb webb;
	private String geoCoderUrl;
	

	@Override
	public void declareOutputFields(final OutputFieldsDeclarer fieldsDeclarer) {
		fieldsDeclarer.declare(new Fields("angelId","segment"));
	}
	
	@Override
	public void prepare(final Map stormConf, final TopologyContext context) {
		webb = Webb.create();
		geoCoderUrl= (String) stormConf.get("geoCoderUrl");
	}

	
	@Override
	public void execute(final Tuple tuple, final BasicOutputCollector outputCollector) {
		final Integer angelId = tuple.getIntegerByField("angelId");
		final JsonObject segment = new JsonParser().parse(tuple.getStringByField("segment")).getAsJsonObject();
		
		if(segment.get("segmentType").getAsString().equals("place") && 
				(segment.get("isNew").getAsBoolean() || !segment.get("isOpen").getAsBoolean())){
			final String latlong = segment.get("lat").getAsString()+","+segment.get("lon").getAsString();
			final String address = webb.get(geoCoderUrl).param("latlng", latlong).asString().getBody();
			segment.addProperty("address", address);
			logger.info("Enriched the segment with address. Segment is now {}",segment);
		}
		
		logger.info("Emit to Kafka the segment with address. Segment is now {}",segment);
		outputCollector.emit(new Values(angelId.toString(),segment.toString()));
		
	}
	
	


}
