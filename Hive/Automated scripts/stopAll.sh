#!/bin/sh
	export LOCAL_DIR=$(pwd)
	source $LOCAL_DIR/config.conf
	
	if [ "hadoop" != `whoami` ]; then echo "run in hadoop user" && exit ; fi
	#stop submarine docker
	if [ "$DOCKER_IS_INSTALL" = "True" ]; then
		cd /hadoop/tools && ./stopDockerDaemon.sh
	fi
	#stop oozie
	if [ "$OOZIE_IS_INSTALL" = "True" ]; then
		cd /hadoop/tools && ./stopOozie.sh
	fi
	#stop spark
	if [ "$SPARK_IS_INSTALL" = "True" ]; then
		ssh app-13 "cd /hadoop/Spark/spark-2.4.0-bin-hadoop3.1.2/sbin && ./stop-all.sh"
	fi
	#stop hive
	if [ "$HIVE_IS_INSTALL" = "True" ]; then
		ssh app-12 "pid=\$(ps x|grep hive|awk '{print \$1}') && for i in \$pid; do kill -9 \$i; done"
	fi
	#stop mysql
	if [ "$MYSQL_IS_INSTALL" = "True" ]; then
		/hadoop/tools/expect/bin/expect /hadoop/tools/remoteSSH.exp root Yhf_1018 app-12 "systemctl stop mysqld.service"
	fi
	#stop hadoop
	if [ "$HADOOP_IS_INSTALL" = "True" ]; then
		cd /hadoop/Hadoop/hadoop-3.1.2/sbin && ./stop-all.sh
		/hadoop/tools/expect/bin/expect /hadoop/tools/remoteSSHNOTroot.exp hadoop Yhf_1018 app-12 "mapred --daemon stop historyserver"
	fi
	#stop zookeeper
	if [ "$ZOOKEEPER_IS_INSTALL" = "True" ]; then
		cd /hadoop/tools && ./stopZookeeper.sh
	fi
	
	echo "stopAll cluster finished"