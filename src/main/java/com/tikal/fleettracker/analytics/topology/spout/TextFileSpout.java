package com.tikal.fleettracker.analytics.topology.spout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;

public class TextFileSpout extends BaseRichSpout {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TextFileSpout.class);

	private String fileName;
	private SpoutOutputCollector collector;
	private BufferedReader reader;
	
	private final String gpsFileName = "/sample-gps.txt";
	
	

	
	@Override
	public void open(final Map conf, final TopologyContext context, final SpoutOutputCollector collector) {
		this.collector = collector;
		try {
			final InputStream is = getClass().getResourceAsStream(gpsFileName);
//			fileName = (String) conf.get("gps.filename");
			reader = new BufferedReader(new InputStreamReader(is));
			// read and ignore the header if one exists
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void deactivate() {
		try {
			reader.close();
		} catch (final IOException e) {
			logger.warn("Problem closing file");
		}
	}

	@Override
	public void nextTuple() {
		try {
			final String line = reader.readLine();
			if (line != null){
				logger.info("line--->"+line);
				collector.emit(new Values(line));
			}
			else
				logger.info("Finished reading file");
			Thread.sleep(1000);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	
	@Override
	public void declareOutputFields(final OutputFieldsDeclarer declarer) {
		// read csv header to get field info
		declarer.declare(new Fields("str"));
	}

}
