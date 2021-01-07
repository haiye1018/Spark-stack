#!/bin/sh
nodeArray="app-13 app-12 app-11 "
for node in $nodeArray
do
	/hadoop/tools/expect/bin/expect /hadoop/tools/remoteSSHNOTroot.exp hadoop Yhf_1018 $node "kafka-server-start.sh -daemon /hadoop/Kafka/kafka_2.11-2.2.0/config/server.properties"
	/hadoop/tools/expect/bin/expect /hadoop/tools/remoteSSHNOTroot.exp hadoop Yhf_1018 $node "jps"
done
