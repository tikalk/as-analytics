package com.tikal.angelsense.analytics.topology.bolts;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tikal.angelsense.analytics.utils.DistanceCalculator;

import backtype.storm.Constants;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;

public class SegmentationBolt extends BaseBasicBolt {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SegmentationBolt.class);
	
	private long speedTheshold;
	
	private final Map<Integer, List<String>> lastLocationsForAngels = new ConcurrentHashMap<>();

	@Override
	public void declareOutputFields(final OutputFieldsDeclarer fieldsDeclarer) {
		fieldsDeclarer.declare(new Fields("angelId","segment"));
	}

	@Override
	public void prepare(final Map stormConf, final TopologyContext context) {
		speedTheshold = (long) stormConf.get("speedTheshold");
	}

	@Override
	public void execute(final Tuple tuple, final BasicOutputCollector outputCollector) {
		logger.debug("Got:"+tuple);
		if (isTickTuple(tuple)) {
			calculateSegments(outputCollector);
		} else {
			final Integer angelId = tuple.getIntegerByField("angelId");
			final JsonObject gps = new JsonParser().parse(tuple.getStringByField("gps")).getAsJsonObject();
			List<String> gpsListForAngel = lastLocationsForAngels.get(angelId);
			if(gpsListForAngel==null){
				gpsListForAngel = new LinkedList<>();
				lastLocationsForAngels.put(angelId,gpsListForAngel);
			}
			gpsListForAngel.add(gps.toString());			
		}
	}
	
	private void calculateSegments(final BasicOutputCollector outputCollector) {
		logger.debug("Calc segments...");
		lastLocationsForAngels.values().stream().forEach(l->calcSegment(l,outputCollector));
		logger.debug("Finished Calc segments.");
		
	}

	private void calcSegment(final List<String> list, final BasicOutputCollector outputCollector) {
		final JsonObject gps = new JsonParser().parse(list.get(0)).getAsJsonObject();
		final int angelId = gps.get("angelId").getAsInt();
		String segmentType;
		logger.info("Calc segment for angel {}",angelId);		
		if(list.size()==1){
			logger.info("Only one point for angel {} - Declare it as Place",angelId);
			segmentType = "place";
		}else{		
			final JsonObject first = gps;
			final JsonObject last = new JsonParser().parse(list.get(list.size()-1)).getAsJsonObject();
			final double speed = calcSpeed(first, last);			
			if(speed > speedTheshold){
				segmentType = "transit";
			}else{
				segmentType = "place";
			}			
		}
		lastLocationsForAngels.remove(angelId);
		final JsonObject gson = new JsonObject();
		gson.addProperty("id", UUID.randomUUID().toString());
		gson.addProperty("angelId", angelId);
		gson.addProperty("segmentType", segmentType);
		gson.addProperty("gpsId", gps.get("id").getAsString());
		gson.addProperty("startTime", gps.get("readingTime").getAsLong());
		
		logger.info("Emitting the angelId {} and segment {}",angelId,gson.toString());
		outputCollector.emit(new Values(String.valueOf(angelId),gson.toString()));
	}

	private double calcSpeed(final JsonObject first, final JsonObject last) {
		final double distInMeters = Math.abs((DistanceCalculator.distance(
				first.get("lat").getAsDouble(), first.get("lon").getAsDouble(), 
				last.get("lat").getAsDouble(), last.get("lon").getAsDouble(), "K"))/1000d);
		
		final double deltaTime = last.get("readingTime").getAsLong() - first.get("readingTime").getAsLong();
		
		final double speed = distInMeters/deltaTime;
		return speed;
	}

	private boolean isTickTuple(final Tuple tuple) {
		final String sourceComponent = tuple.getSourceComponent();
		final String sourceStreamId = tuple.getSourceStreamId();
		return sourceComponent.equals(Constants.SYSTEM_COMPONENT_ID)
				&& sourceStreamId.equals(Constants.SYSTEM_TICK_STREAM_ID);
	}


}
