package com.tikal.fleettracker.analytics.topology.bolts;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tikal.fleettracker.analytics.utils.DistanceCalculator;

import backtype.storm.Constants;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;

public class GpsParserBolt extends BaseBasicBolt {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GpsParserBolt.class);
	

	@Override
	public void declareOutputFields(final OutputFieldsDeclarer fieldsDeclarer) {
		fieldsDeclarer.declare(new Fields("vehicleId","gps"));
	}

	
	@Override
	public void execute(final Tuple tuple, final BasicOutputCollector outputCollector) {
//		logger.info("Got:"+tuple);
		final String str = tuple.getStringByField("str");
		logger.info(str);
		final JsonObject gps = new JsonParser().parse(str).getAsJsonObject();
		outputCollector.emit(new Values(gps.get("vehicleId").getAsInt(),gps.toString()));
		
	}
	
	


}
