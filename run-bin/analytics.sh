#!/bin/bash

export JAVA_OPTS="-Dkafka_brokers_lists=kafka:9092"
export JAVA_OPTS="$JAVA_OPTS -DzkHosts=zookeeper"
export JAVA_OPTS="$JAVA_OPTS -DkafkaGpsTopicName=as-gps"
export JAVA_OPTS="$JAVA_OPTS -DkafkaSegmentsTopicName=as-segments"
export JAVA_OPTS="$JAVA_OPTS -DredisHost=redis"
export JAVA_OPTS="$JAVA_OPTS -DspeedTheshold=2"
export JAVA_OPTS="$JAVA_OPTS -DgeoCoderUrl=http://as-geocoder-facade:7080/api/v1/address"

echo "Starting VehiclesSense Analytics with $JAVA_OPTS"

DIRNAME=`dirname $0`
APP_HOME=`cd $DIRNAME/..;pwd;`
export APP_HOME;




java $JAVA_OPTS -jar $APP_HOME/target/as-analytics-1.0.0-jar-with-dependencies.jar