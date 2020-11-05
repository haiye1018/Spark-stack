#!/bin/sh
nodeArray="app-11"
for node in $nodeArray
do
	ssh $node "cd /hadoop/Oozie/oozie-5.0.0 && bin/oozied.sh stop " 
done
