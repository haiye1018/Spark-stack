#!/bin/sh
	if [ "hadoop" != `whoami` ]; then echo "run in hadoop user" && exit ; fi
	
	SRC_PATH=org/apache/tez/examples
	MAIN_CLASS=org.apache.tez.examples.OrderedWordCount
	SRC_TAR_NAME=org.tar
	BUILD_JAR_NAME=app.jar
	export HADOOP_CLASSPATH=$JAVA_HOME/lib/tools.jar
	rm -rf $BUILD_JAR_NAME
	rm -rf $(echo $SRC_PATH|sed -r 's@/.*@@g')
	tar -xf $SRC_TAR_NAME
	hadoop com.sun.tools.javac.Main $SRC_PATH/*.java
	echo "Manifest-Version: 1.0" > $SRC_PATH/MANIFEST.MF
	echo "Main-Class: $MAIN_CLASS" >> $SRC_PATH/MANIFEST.MF
	echo "Class-Path: " >> $SRC_PATH/MANIFEST.MF
	echo "" >> $SRC_PATH/MANIFEST.MF
	jar cvfm $BUILD_JAR_NAME $SRC_PATH/MANIFEST.MF $SRC_PATH/*.class
	
	# hadoop jar app.jar org.apache.tez.examples.OrderedWordCount /installTest/tez/data /installTest/tez/output2
	# |
	# |
	# V
	# hadoop jar app.jar /installTest/hadoop/data /installTest/tez/output3
	if [ $# -ge 1 ]; then
        case "$1" in
			run)
				hdfs dfs -rm -r -f /installTest/tez
				hdfs dfs -mkdir -p /installTest/tez/data
				hdfs dfs -put $HADOOP_HOME/etc/hadoop/*.xml  /installTest/tez/data
				hadoop jar $BUILD_JAR_NAME /installTest/tez/data /installTest/tez/output
				hdfs dfs -cat /installTest/tez/output/part-v002-o000-r-00000
				;;
			*)
				echo "unknown parameter $1."
				;;
        esac
	fi
	