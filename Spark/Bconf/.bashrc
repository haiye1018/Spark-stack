# .bashrc

# Source global definitions
if [ -f /etc/bashrc ]; then
	. /etc/bashrc
fi

# Uncomment the following line if you don't like systemctl's auto-paging feature:
# export SYSTEMD_PAGER=

# User specific aliases and functions
export JAVA_HOME=/hadoop/JDK/jdk1.8.0_131
export JRE_HOME=${JAVA_HOME}/jre
export CLASSPATH=.:${JAVA_HOME}/lib:${JRE_HOME}/lib
export PATH=${JAVA_HOME}/bin:$PATH
export PROTOBUF_HOME=/hadoop/tools/protobuf-2.5.0
export PATH=${PROTOBUF_HOME}/bin:$PATH
export ZOOKEEPER_HOME=/hadoop/ZooKeeper/zookeeper-3.4.10
export PATH=${ZOOKEEPER_HOME}/bin:$PATH
export HADOOP_HOME=/hadoop/Hadoop/hadoop-3.1.2
export PATH=${HADOOP_HOME}/bin:${HADOOP_HOME}/sbin:${HADOOP_HOME}/lib:$PATH
export HIVE_HOME=/hadoop/Hive/apache-hive-3.1.1-bin
export PATH=${HIVE_HOME}/bin:$PATH
export SCALA_HOME=/hadoop/Scala/scala-2.11.12
export PATH=${SCALA_HOME}/bin:$PATH
export SPARK_HOME=/hadoop/Spark/spark-2.4.0-bin-hadoop3.1.2
export PATH=${SPARK_HOME}/bin:$PATH
