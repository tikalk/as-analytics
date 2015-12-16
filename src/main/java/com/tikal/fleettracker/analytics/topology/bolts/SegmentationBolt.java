package com.tikal.fleettracker.analytics.topology.bolts;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import backtype.storm.task.TopologyContext;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import backtype.storm.utils.Utils;

public class SegmentationBolt extends BaseBasicBolt {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SegmentationBolt.class);
	
	private long speedTheshold;
	
	//Should use Redis...
	private final Map<Integer, JsonObject> currentSegments = new HashMap<>();
	
	
	@Override
	public void declareOutputFields(final OutputFieldsDeclarer fieldsDeclarer) {
		fieldsDeclarer.declare(new Fields("vehicleId","segment"));
	}

	@Override
	public void prepare(final Map stormConf, final TopologyContext context) {
		speedTheshold = (long) stormConf.get("speedTheshold");
	}

	@Override
	public void execute(final Tuple tuple, final BasicOutputCollector outputCollector) {
		final Integer vehicleId = tuple.getIntegerByField("vehicleId");
		final JsonObject gps = new JsonParser().parse(tuple.getStringByField("gps")).getAsJsonObject();
		final String segmentType = gps.get("speed").getAsInt()>speedTheshold?"transit":"place";
		JsonObject currentSegment = currentSegments.get(vehicleId);
		if(currentSegment==null){
			currentSegment = buildSegment(vehicleId, gps, segmentType);
			currentSegments.put(vehicleId,currentSegment);
			logger.info("Emit the first segment for vehicle {}. segment is {}",vehicleId,currentSegment);
			outputCollector.emit(new Values(vehicleId,currentSegment.toString()));
		}else{
			if(currentSegment.get("segmentType").getAsString().equals(segmentType)){
				//Should be the same segment -> Just update the last GPS and last time
				currentSegment.addProperty("endTime", gps.get("readingTime").getAsLong());
				currentSegment.addProperty("isNew", false);
				currentSegments.put(vehicleId,currentSegment);
				logger.info("Emit an update for existing segment segment for vehicle {}. segment is {}",vehicleId,currentSegment);
				outputCollector.emit(new Values(vehicleId,currentSegment.toString()));
			}else{
				//We will close current segment, and update the lat lon to the last gps, and create a new one
				currentSegment.addProperty("isOpen", false);
				currentSegment.addProperty("isNew", false);
				currentSegment.addProperty("lat", gps.get("lat").getAsDouble());
				currentSegment.addProperty("lon", gps.get("lon").getAsDouble());
				logger.info("Closing a segment for vehicle {}. segment is {}",vehicleId,currentSegment);
				outputCollector.emit(new Values(vehicleId,currentSegment.toString()));
				
				final JsonObject newSegment = buildSegment(vehicleId, gps, segmentType);				
				currentSegments.put(vehicleId,newSegment);
				logger.info("Creating a new segment for vehicle {}. segment is {}",vehicleId,newSegment);
				outputCollector.emit(new Values(vehicleId,newSegment.toString()));
			}
		}		
	}

	private JsonObject buildSegment(final Integer vehicleId, final JsonObject gps, final String segmentType) {
		JsonObject currentSegment;
		currentSegment = new JsonObject();
		currentSegment.addProperty("isOpen", true);
		currentSegment.addProperty("isNew", true);
		currentSegment.addProperty("_id", UUID.randomUUID().toString());
		currentSegment.addProperty("vehicleId", vehicleId);
		currentSegment.addProperty("startTime", gps.get("readingTime").getAsLong());
		currentSegment.addProperty("endTime", gps.get("readingTime").getAsLong());
		currentSegment.addProperty("segmentType", segmentType);
		currentSegment.addProperty("lat", gps.get("lat").getAsDouble());
		currentSegment.addProperty("lon", gps.get("lon").getAsDouble());
		return currentSegment;
	}
}
