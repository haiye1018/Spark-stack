#!/bin/sh
	if [ "hadoop" != `whoami` ]; then echo "run in hadoop user" && exit ; fi
	
	export MAVEN_REPO_DIR=/tmp/maven/repo
	rm -rf $MAVEN_REPO_DIR
	tar -xf repo.tar.gz -C /tmp/maven/
	
	tar -xvf oozie-5.0.0.tar.gz
	
	rm -rf oozie-5.0.0/pom.xml
	cp pom.xml oozie-5.0.0/
	rm -rf $MAVEN_REPO_DIR/org/apache/maven/doxia/doxia-core/1.0-alpha-9.2y
	rm -rf $MAVEN_REPO_DIR/org/apache/maven/doxia/doxia-module-twiki/1.0-alpha-9.2y
	mkdir -p $MAVEN_REPO_DIR/org/apache/maven/doxia/doxia-core/1.0-alpha-9.2y
	mkdir -p $MAVEN_REPO_DIR/org/apache/maven/doxia/doxia-module-twiki/1.0-alpha-9.2y
	cp doxia-module-twiki-1.0-alpha-9.2y.jar $MAVEN_REPO_DIR/org/apache/maven/doxia/doxia-module-twiki/1.0-alpha-9.2y/
	cp doxia-core-1.0-alpha-9.2y.jar $MAVEN_REPO_DIR/org/apache/maven/doxia/doxia-core/1.0-alpha-9.2y/
	
	cd oozie-5.0.0
	bin/mkdistro.sh  -DskipTests -Dtez.version=0.9.0 -Ptez -P'spark-2' -Puber
	