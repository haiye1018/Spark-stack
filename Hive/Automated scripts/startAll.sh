#!/bin/sh
	export LOCAL_DIR=$(pwd)
	source $LOCAL_DIR/config.conf
	
	if [ "hadoop" != `whoami` ]; then echo "run in hadoop user" && exit ; fi
	#start zookeeper
	if [ "$ZOOKEEPER_IS_INSTALL" = "True" ]; then
		cd /hadoop/tools && ./startZookeeper.sh
	fi
	#start hadoop
	if [ "$HADOOP_IS_INSTALL" = "True" ]; then
		cd /hadoop/Hadoop/hadoop-3.1.2/sbin && ./start-all.sh
		/hadoop/tools/expect/bin/expect /hadoop/tools/remoteSSHNOTroot.exp hadoop Yhf_1018 app-12 "mapred --daemon start historyserver"
	fi
	#start mysql
	if [ "$MYSQL_IS_INSTALL" = "True" ]; then
		/hadoop/tools/expect/bin/expect /hadoop/tools/remoteSSH.exp root Yhf_1018 app-12 "systemctl start mysqld.service"
	fi
	#start hive
	if [ "$HIVE_IS_INSTALL" = "True" ]; then
		/hadoop/tools/expect/bin/expect /hadoop/tools/remoteSSHNOTroot.exp hadoop Yhf_1018 app-12 "cd /hadoop/Hive/apache-hive-3.1.1-bin/bin && nohup ./hive --service metastore > /hadoop/Hive/apache-hive-3.1.1-bin/log/metastore.log 2>&1 &"
	fi
	#start spark
	if [ "$SPARK_IS_INSTALL" = "True" ]; then
		ssh app-13 "cd /hadoop/Spark/spark-2.4.0-bin-hadoop3.1.2/sbin && ./start-all.sh"
	fi
	#start oozie
	if [ "$OOZIE_IS_INSTALL" = "True" ]; then
		cd /hadoop/tools && ./startOozie.sh
	fi
	#start submarine docker
	if [ "$DOCKER_IS_INSTALL" = "True" ]; then
		cd /hadoop/tools && ./startDockerDaemon.sh
	fi
	echo "startAll cluster finished"
	