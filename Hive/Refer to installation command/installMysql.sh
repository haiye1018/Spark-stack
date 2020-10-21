#!/bin/sh
	mount -t iso9660 /dev/cdrom /mnt
# 安装mysql
	yum remove -y *mariadb*
	yum remove -y *mysql-community*
	yum localinstall -y $LOCAL_DIR/mysql-community-common-5.7.18-1.el7.x86_64.rpm
	yum localinstall -y $LOCAL_DIR/mysql-community-libs-5.7.18-1.el7.x86_64.rpm
	yum localinstall -y $LOCAL_DIR/mysql-community-libs-compat-5.7.18-1.el7.x86_64.rpm
	yum localinstall -y $LOCAL_DIR/mysql-community-client-5.7.18-1.el7.x86_64.rpm
	yum localinstall -y $LOCAL_DIR/mysql-community-devel-5.7.18-1.el7.x86_64.rpm
	yum localinstall -y $LOCAL_DIR/mysql-community-server-5.7.18-1.el7.x86_64.rpm

	systemctl start mysqld.service

	grep "A temporary password is generated for" /var/log/mysqld.log
	# ALTER_MYSQL_PASSWD_EXP_CMD
	ALTER USER 'root'@'localhost' IDENTIFIED BY 'Yhf_1018';
	GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' IDENTIFIED BY 'Yhf_1018' with grant option;
	FLUSH privileges;
	quit;
#安装Hive
	mkdir -p /hadoop/Hive
	tar -xf -C 
	upload hive-site.xml hive-log4j2.properties
	#Exception in thread "main" java.lang.RuntimeException: com.ctc.wstx.exc.WstxParsingException: Illegal character entity: expansion character (code 0x8
	# at [row,col,system-id]: [3210,96,"file:/hadoop/Hive/apache-hive-3.1.1-bin/conf/hive-site.xml"]
	sed -i -E '3210d' /hadoop/Hive/apache-hive-3.1.1-bin/conf/hive-site.xml
	
	#zookeeper
	#metastore
	#tmpdir
	
	mkdir /hadoop/Hive/apache-hive-3.1.1-bin/{tmp,log}
	
	export HIVE_HOME=/hadoop/Hive/apache-hive-3.1.1-bin
	export PATH=${HIVE_HOME}/bin:$PATH
	
	drop database if exists hive
	create database hive
	
	upload mysql-connector-java-5.1.46.jar ./lib/
	
	cd /hadoop/Hive/apache-hive-3.1.1-bin/bin && ./schematool -dbType mysql -initSchema
	cd /hadoop/Hive/apache-hive-3.1.1-bin/bin && nohup ./hive --service metastore > /hadoop/Hive/apache-hive-3.1.1-bin/log/metastore.log 2>&1 &
	