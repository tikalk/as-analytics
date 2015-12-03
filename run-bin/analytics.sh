#!/bin/bash

echo "Starting AngelsSense Analytics"

DIRNAME=`dirname $0`
APP_HOME=`cd $DIRNAME/..;pwd;`
export APP_HOME;

java -jar $APP_HOME/target/as-analytics-1.0.0-jar-with-dependencies.jar