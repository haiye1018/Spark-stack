
from __future__ import print_function

from pyspark import SparkContext
from pyspark import SparkConf
from os.path import expanduser, join, abspath
from pyspark.sql import SparkSession
from pyspark.sql import Row
from pyspark.sql.types import *
from pyspark.sql.types import StructField
from pyspark.sql.functions import udf, col

import matplotlib.pyplot as plt
import matplotlib.dates as mdate
import numpy as np
import pandas as pd
# $example off:spark_hive$


if __name__ == "__main__":
	warehouse_location = abspath('/user/hive/warehouse')
	spark = SparkSession \
        .builder \
        .appName("Python Spark SQL Hive integration example") \
        .config("spark.sql.warehouse.dir", warehouse_location) \
        .master("spark://app-13:7077") \
        .getOrCreate()
	df = spark.read.format("CSV").option("header","true").\
	option("timestampFormat ","yyyy-MM-dd'T'HH").\
	schema("user_id int,item_id int,behavior_type int,user_geohash string,item_category int,create_time string").\
	load("/user/hive/warehouse/test.db/tianchi/tianchi_mobile_recommend_train_user.csv") 
	df.createOrReplaceTempView("taobao")
	spark.sql("SELECT count(*) FROM taobao").show()
	
	spark.conf.set("spark.sql.execution.arrow.enabled", "true")
	
	# 用户操作数按日期统计
	#spark.sql("SELECT max(date(to_timestamp(create_time))) FROM taobao").show()
	#spark.sql("SELECT max(hour(to_timestamp(create_time))) FROM taobao").show()
	sqlDF = spark.sql("SELECT date(to_timestamp(create_time)) as create_day, count(*) as pbcf FROM taobao group by date(to_timestamp(create_time)) order by date(to_timestamp(create_time))")
	sqlDF.cache()
	sqlDF.createOrReplaceTempView("pbcf")
	sqlPandasDF = spark.sql("select cast(create_day as string)as create_day, pbcf  from pbcf")
	pbcfPandasDF = sqlPandasDF.toPandas()
	
	fig=plt.figure(figsize=(30,6))
	ax=fig.add_subplot(1,1,1)
	ax.xaxis.set_major_formatter(mdate.DateFormatter('%Y-%m-%d'))
	date_series=pd.date_range(pbcfPandasDF['create_day'][0],pbcfPandasDF['create_day'][30],freq='D')
	plt.xticks(date_series,rotation=45)
	ax.plot(date_series,pbcfPandasDF.set_index('create_day'))
	plt.show()
	# plt.savefig('/tmp/userOpByDate.png', dpi=200)
	spark.catalog.clearCache()
	
	# 用户操作数按时段统计
	sqlDF = spark.sql("SELECT hour(to_timestamp(create_time)) as create_hour, count(*) as pbcf FROM taobao group by hour(to_timestamp(create_time)) order by hour(to_timestamp(create_time))")
	sqlDF.cache()
	pbcfPandasDF = sqlDF.toPandas()
	
	fig=plt.figure(figsize=(30,6))
	ax=fig.add_subplot(1,1,1)
	date_series=pbcfPandasDF['create_hour']
	plt.xticks(date_series)
	ax.plot(date_series,pbcfPandasDF.set_index('create_hour'))
	plt.show()
	# plt.savefig('/tmp/userOpByHour.png', dpi=200)
	spark.catalog.clearCache()
	
	# 用户数按日期统计
	sqlDF = spark.sql("SELECT date(to_timestamp(create_time)) as create_day, count(distinct user_id) as pbcfd, count(user_id) as pbcfa FROM taobao group by date(to_timestamp(create_time)) order by date(to_timestamp(create_time))")
	sqlDF.cache()
	sqlDF.createOrReplaceTempView("pbcf")
	sqlPandasDF = spark.sql("select cast(create_day as string)as create_day, pbcfd, pbcfa  from pbcf")
	pbcfPandasDF = sqlPandasDF.toPandas()
	
	pbcfPandasDF.set_index('create_day').plot(kind='bar')
	
	fig=plt.figure(figsize=(30,6))
	ax=fig.add_subplot(1,1,1)
	ax.xaxis.set_major_formatter(mdate.DateFormatter('%Y-%m-%d'))
	date_series=pd.date_range(pbcfPandasDF['create_day'][0],pbcfPandasDF['create_day'][30],freq='D')
	plt.xticks(date_series,rotation=45)
	# axis=0: row; axis=1: col
	ax.plot(date_series,pbcfPandasDF.drop(['pbcfa'], axis=1).set_index('create_day'))
	plt.show()
	# plt.savefig('/tmp/userOpByDate.png', dpi=200)
	spark.catalog.clearCache()
	
	# 用户客单特征分析
	sqlDF = spark.sql("SELECT date(to_timestamp(create_time)) as create_day,count(distinct user_id) as pbcfd,count(behavior_type) as buy FROM taobao where behavior_type=4 group by date(to_timestamp(create_time)) order by date(to_timestamp(create_time))")
	sqlDF.cache()
	sqlDF.createOrReplaceTempView("pbcf")
	sqlPandasDF = spark.sql("select cast(create_day as string)as create_day, pbcfd, buy from pbcf")
	pbcfPandasDF = sqlPandasDF.toPandas()
	
	fig=plt.figure(figsize=(30,6))
	ax=fig.add_subplot(1,1,1)
	ax.xaxis.set_major_formatter(mdate.DateFormatter('%Y-%m-%d'))
	date_series=pd.date_range(pbcfPandasDF['create_day'][0],pbcfPandasDF['create_day'][30],freq='D')
	plt.xticks(date_series,rotation=45)
	# axis=0: row; axis=1: col
	pbcfPandasDF['perBuy'] = pbcfPandasDF['buy'] / pbcfPandasDF['pbcfd']
	ax.plot(date_series,pbcfPandasDF.drop(['pbcfd','buy'], axis=1).set_index('create_day'))
	plt.show()
	# plt.savefig('/tmp/userOpByDate.png', dpi=200)
	spark.catalog.clearCache()
	
	# pv转化率
	spark.sql("SELECT count(distinct item_category) from taobao").show()
	spark.sql("SELECT count(distinct item_id) from taobao").show()
	spark.sql("SELECT item_id,behavior_type,count(*) as cnt from taobao group by item_id,behavior_type order by behavior_type desc,cnt desc").show()
	
	sqlDFRaw = spark.sql("""SELECT item_id,
(case behavior_type 
      when 1 then 'pv'
      when 2 then 'fav'
      when 3 then 'cart'
      when 4 then 'buy'
      END)behavior,
  count(*) as cnt
FROM taobao group by item_id,behavior_type order by behavior_type, cnt desc
""")
	sqlDFCol = sqlDFRaw.groupBy("item_id").pivot("behavior", ["pv", "fav", "cart", "buy"]).sum("cnt")
	sqlDFCol.cache()
	sqlDFCol.show()
	sqlDFCol.where(sqlDFCol.pv.isNull()).show()
	sqlDFCol.createOrReplaceTempView("pbcf")
	# 单品的pv转化率太高, 有刷单嫌疑
	sqlDFI = spark.sql("SELECT item_id, buy/pv as frac from pbcf where pv is not null and buy is not null order by frac desc")
	sqlDFI.show()
	spark.sql("SELECT * FROM pbcf where item_id=95756656").show()
	spark.sql("SELECT * FROM taobao where item_id=95756656").show()
	# 总体转化率
	sqlDF = spark.sql("SELECT count(pv) as cpv, count(cart) as ccart, count(fav) as cfav, count(buy) as cbuy from pbcf")
	sqlDF.show()
	pbcfPandasDF = sqlDF.toPandas()
	# Python实现漏斗图的绘制 https://blog.csdn.net/qq_41080850/article/details/83933017
	from pyecharts import Funnel
	attrs = pd.Series(['pv','cart','fav','buy']).tolist()
	attr_value = pd.Series([100,pbcfPandasDF.iloc[0]['ccart']/pbcfPandasDF.iloc[0]['cpv']*100,pbcfPandasDF.iloc[0]['cfav']/pbcfPandasDF.iloc[0]['ccart']*100,pbcfPandasDF.iloc[0]['cbuy']/pbcfPandasDF.iloc[0]['ccart']*100]).tolist()
	funnel1 = Funnel("总体转化漏斗图一",width=800, height=400, title_pos='center')
	funnel1.add(name="商品交易行环节",       # 指定图例名称
				attr=attrs,                # 指定属性名称
				value = attr_value,        # 指定属性所对应的值
				is_label_show=True,        # 指定标签是否显示
				label_formatter='{c}%',    # 指定标签显示的格式
				label_pos="inside",        # 指定标签的位置
				legend_orient='vertical',  # 指定图例的方向
				legend_pos='left',         # 指定图例的位置
				is_legend_show=True)       # 指定图例是否显示
	funnel1.render()
	funnel1
	
	spark.catalog.clearCache()
	
	spark.stop()
