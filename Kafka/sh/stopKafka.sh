#!/bin/sh
nodeArray="app-13 app-12 app-11 "
for node in $nodeArray
do
	/hadoop/tools/expect/bin/expect /hadoop/tools/remoteSSHNOTroot.exp hadoop Yhf_1018 $node "kafka-server-stop.sh"
done
