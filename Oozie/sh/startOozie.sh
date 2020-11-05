#!/bin/sh
nodeArray="app-11"
for node in $nodeArray
do
	ssh $node "cd /hadoop/Oozie/oozie-5.0.0 && bin/oozied.sh start " 
  sleep 1m
	ssh $node "cd /hadoop/Oozie/oozie-5.0.0 && bin/oozie admin -oozie http://$node:11000/oozie -status " 
done
