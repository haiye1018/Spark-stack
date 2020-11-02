CREATE DATABASE IF NOT EXISTS test;
USE test;
DROP TABLE IF EXISTS `TIANCHI`;
--100509623,266020206,3,tfvomgk,4923,2014-12-08 17
CREATE TABLE IF NOT EXISTS `TIANCHI` (
  `user_id` bigint,
  `item_id` bigint,
  `behavior_type` bigint,
  `user_geohash` string,
  `item_category` bigint,
  `create_time` struct<create_day:string, create_hour:string>
)
ROW FORMAT DELIMITED FIELDS TERMINATED BY ',' COLLECTION ITEMS TERMINATED BY ' ' 
tblproperties(
"skip.header.line.count"="1"--, --skip head one line
--"skip.footer.line.count"="1" --skip tail one line
);
--STORED BY 'org.apache.hive.storage.jdbc.JdbcStorageHandler'

LOAD DATA LOCAL INPATH '/tmp/spark/tianchi_mobile_recommend_train_user.csv' OVERWRITE INTO TABLE TIANCHI;

--summary
--set mapreduce.map.memory.mb=2048;
--set mapreduce.reduce.memory.mb=2048;

--select count(*) from TIANCHI;
--select create_hour from (select distinct create_time.create_hour from tianchi)u order by u.create_hour;
--select create_day from (select distinct create_time.create_day from tianchi)u order by u.create_day;
--select create_time.create_day,count(*) from tianchi group by create_time.create_day order by create_time.create_day;