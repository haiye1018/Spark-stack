#!/bin/sh
	if [ "hadoop" != `whoami` ]; then echo "run in hadoop user" && exit ; fi
	
	rm -rf /tmp/maven/repo
	tar -xf repo.tar.gz -C /tmp/maven/
	
	# support hive3
	#https://mvnrepository.com/artifact/org.spark-project.hive/hive-exec?repo=hortonworks-releases
	tar -xvf spark-2.4.0.tgz
	rm -rf spark-2.4.0/pom.xml
	cp pom_hive3.xml spark-2.4.0/pom.xml
	cd spark-2.4.0
	export MAVEN_OPTS="-Xmx2g -XX:ReservedCodeCacheSize=512m"
	./dev/make-distribution.sh --name hadoop3.1.2 --tgz -Phadoop-3.1 -Dhadoop.version=3.1.2 -Phive -Dhive.version=1.21.2.3.1.2.0-4 -Dhive.version.short=1.21.2.3.1.2.0-4 -Phive-thriftserver -Pyarn
	