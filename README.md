# FleetTracker Analytics

This microservice is the "brain" for the application. It's responsibility is to create new segments or update existing
ones according to the input GPS. The GPS it consumes are read from the "gps" Kafka topic (fed by the gps-service), and it's output goes to yet another Kafka topic named "segments" (consumed by the segments-service)/ 
As this is Streamed based calculations, we use Apache-Storm

## How to build
_______________
From the project home folder run the following command:

mvn -DskipTests clean install

This will create self contained zip, that you can unzip on host container. The output zip is located at
ft-analytics/target/anaytics.tgz

## How to run
--------------
You must run ZooKeeper and Kafka, before you run this service
You also need to run the geo-coder facade is running

Unzip the file ft-analytics/target/anaytics.tgz
cd to the created folder (anaytics)
Run the following command : 
./run-bin/analytics.sh



 

