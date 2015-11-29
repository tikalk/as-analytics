package com.tikal.angelsense.analytics.topology.bolts;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
import redis.clients.jedis.Jedis;

public class SegmentationBolt extends BaseBasicBolt {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SegmentationBolt.class);
	
	private long speedTheshold;
	private Jedis jedis;
	
	
	//Should use Redis order by ReadingTime
//	private final Map<Integer, List<String>> lastLocationsForAngels = new ConcurrentHashMap<>();

	@Override
	public void declareOutputFields(final OutputFieldsDeclarer fieldsDeclarer) {
		fieldsDeclarer.declare(new Fields("angelId","segment"));
	}

	@Override
	public void prepare(final Map stormConf, final TopologyContext context) {
		jedis = new Jedis((String)stormConf.get("redisHost"));
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
			final double readingTime = gps.get("readingTime").getAsDouble();
			jedis.zadd("last.intervalgps.angel."+angelId, readingTime, gps.toString());
//			List<String> gpsListForAngel = lastLocationsForAngels.get(angelId);
//			if(gpsListForAngel==null){
//				gpsListForAngel = new LinkedList<>();
//				lastLocationsForAngels.put(angelId,gpsListForAngel);
//			}
//			gpsListForAngel.add(gps.toString());			
		}
	}
	
	private void calculateSegments(final BasicOutputCollector outputCollector) {
		logger.debug("Calc segments...");
		final Set<String> keys = jedis.keys("last.intervalgps.angel.*");
		keys.stream().forEach(key->calcSegment(key,outputCollector));
		logger.debug("Finished Calc segments.");
		
	}

	private void calcSegment(final String key, final BasicOutputCollector outputCollector) {
		String segmentType;
		final Long zcount = jedis.zcount(key, "-inf", "+inf");		
		final JsonObject firstGPS = new JsonParser().parse(jedis.zrange(key, 0, 1).iterator().next()).getAsJsonObject();
		final int angelId = firstGPS.get("angelId").getAsInt();
		JsonObject lastGPS;
		if(zcount==1){
			logger.info("Only one point for angelId {} - Declare it as Place",angelId);
			lastGPS = firstGPS;
			segmentType = "place";
		} else{
			lastGPS = new JsonParser().parse(jedis.zrange(key, zcount-1, zcount).iterator().next()).getAsJsonObject();
			final double speed = calcSpeed(firstGPS, lastGPS);
			if(speed > speedTheshold){
				segmentType = "transit";
			}else{
				segmentType = "place";
			}		
			jedis.zremrangeByRank(key, 0, -2);
		}
		
		final JsonObject segmentInterval = new JsonObject();
		segmentInterval.addProperty("id", UUID.randomUUID().toString());
		segmentInterval.addProperty("angelId", angelId);
		segmentInterval.addProperty("segmentType", segmentType);
		segmentInterval.add("firstGPSInInterval", firstGPS);
		segmentInterval.add("lastGPSInInterval", lastGPS);
		
		logger.info("Emitting the angelId {} and segment-interval {}",angelId,segmentInterval.toString());
//		outputCollector.emit(new Values(angelId,gson.toString()));
	}

	private double calcSpeed(final JsonObject first, final JsonObject last) {
		final double distInMeters = Math.abs((DistanceCalculator.distance(
				first.get("lat").getAsDouble(), first.get("lon").getAsDouble(), 
				last.get("lat").getAsDouble(), last.get("lon").getAsDouble(), "K"))/1000d);
		
		final double deltaTime = last.get("readingTime").getAsLong() - first.get("readingTime").getAsLong();
			
		final double speed = distInMeters/deltaTime;
		
		logger.info("distance is {}, delta-time is {}, speed is {}",distInMeters,deltaTime,speed);
		
		return speed;
	}

	private boolean isTickTuple(final Tuple tuple) {
		final String sourceComponent = tuple.getSourceComponent();
		final String sourceStreamId = tuple.getSourceStreamId();
		return sourceComponent.equals(Constants.SYSTEM_COMPONENT_ID)
				&& sourceStreamId.equals(Constants.SYSTEM_TICK_STREAM_ID);
	}


}
