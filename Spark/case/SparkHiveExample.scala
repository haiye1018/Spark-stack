
// $example on:spark_hive$
import java.io.File
import org.apache.spark.sql.{Row, SaveMode, SparkSession}
val warehouseLocation = new File("/user/hive/warehouse").getAbsolutePath
val spark = SparkSession.builder().appName("Spark Hive Example").config("spark.sql.warehouse.dir", warehouseLocation).enableHiveSupport().getOrCreate()

import spark.implicits._
import spark.sql

sql("SELECT * FROM test.employee").show()
sql("SELECT COUNT(*) FROM test.employee").show()
spark.stop()

