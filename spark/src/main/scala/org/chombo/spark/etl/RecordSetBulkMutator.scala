/*
 * chombo-spark: etl on spark
 * Author: Pranab Ghosh
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.chombo.spark.etl

import org.chombo.spark.common.JobConfiguration
import org.apache.spark.SparkContext
import scala.collection.JavaConverters._
import org.apache.spark.rdd.RDD
import org.chombo.util.BasicUtils
import org.chombo.spark.common.Record

/**
 * bulk data mutation
 * @author pranab
 */
object RecordSetBulkMutator extends JobConfiguration {

   /**
  * @param args
  * @return
  */
   def main(args: Array[String]) {
	   val appName = "normalizer"
	   val Array(inputPath: String, outputPath: String, configFile: String) = getCommandLineArgs(args, 3)
	   val config = createConfig(configFile)
	   val sparkConf = createSparkConf(appName, config, false)
	   val sparkCntxt = new SparkContext(sparkConf)
	   val appConfig = config.getConfig(appName)
	   
	   //configuration params
	   val fieldDelimIn = getStringParamOrElse(appConfig, "field.delim.in", ",")
	   val fieldDelimOut = getStringParamOrElse(appConfig, "field.delim.out",  ",")
	   val syncMode = getStringParamOrElse(appConfig, "sync.mode",  "partial")
	   val mutOp = getOptionalStringParam(appConfig, "mutation.op")
	   val incrFilePath = getMandatoryStringParam(appConfig, "incr.filePath", "missing incremental file path")
	   val keyFieldsOrdinals = getMandatoryIntListParam(appConfig, "id.fieldOrdinals", "missing key field ordinals").asScala.toArray
	   val seqFieldOrd = getMandatoryIntParam(appConfig, "seq.fieldOrdinal", "missing sequence filed ordinal")
	   val baseRecPrefix = "$base"
	   val incrRecPrefix = "$incr"
	   val delRecPrefix = "$del"
	   val prefixLen = baseRecPrefix.length()
	   val debugOn = getBooleanParamOrElse(appConfig, "debug.on", false)
	   val saveOutput = getBooleanParamOrElse(appConfig, "save.output", true)
	   
	   //base and incremental data
	   val baseData = sparkCntxt.textFile(inputPath)
	   val keyedBaseRecs =  getKeyedRecs(baseData, fieldDelimIn, keyFieldsOrdinals, mutOp, baseRecPrefix)
	   keyedBaseRecs.cache
	   
	   //incremental keyed data
	   val incrData = sparkCntxt.textFile(incrFilePath)
	   val keyedIncrRecs =  getKeyedRecs(incrData, fieldDelimIn, keyFieldsOrdinals, mutOp, incrRecPrefix)
	   
	   //merge
	   val keyedRecs = keyedBaseRecs ++ keyedIncrRecs
	   

	   val updatedRecs = 
	     mutOp match {
	       case Some(op:String) => {
	         //mutation operation specified
		     val recs = if (op.equals("upsert")) {
			   //insert and update
	           keyedRecs.map(v => {
	             val recs = v._2.toSeq
	             recs.sortBy(line => {
	               //descending order
	               val fields = BasicUtils.getTrimmedFields(line, fieldDelimIn)
	               -fields(seqFieldOrd).toLong
	             })
	             recs(0)
	           })
	         } else {
	           //delete
	           keyedRecs.filter(v => v._2.toSeq.length == 1).map(v => v._2.toSeq(0))
	         }
		     recs
	       }
	       case None => {
	         //automatic
	         val recs = keyedRecs.map(v => {
	           val recs = v._2.toSeq
	           if (recs.length > 1) {
	             //update
	             recs.sortBy(line => {
	               //descending order
	               val fields = BasicUtils.getTrimmedFields(line, fieldDelimIn)
	               -fields(seqFieldOrd).toLong
	             })
	             recs(0).substring(prefixLen)
	           } else {
	             val prefix = recs(0).substring(0, prefixLen)
	             val rec = recs(0).substring(prefixLen)
	             if (prefix.equals(baseRecPrefix)) {
	               //delete or leave alone
	               if (syncMode.equals("partial")) rec else delRecPrefix + rec
	             } else {
	               //insert
	               rec
	             }
	           }
	         })
	         recs.filter(r => !r.startsWith(delRecPrefix))
	       }
	   }
	   
	  if (debugOn) {
	     updatedRecs.collect.foreach(s => println(s))
	  }
	   
	  if (saveOutput) {
	     updatedRecs.saveAsTextFile(outputPath)
	  }
	   
   }
   
   /**
   * @param data
   * @param fieldDelimIn
   * @param keyFieldsOrdinals
   * @param mutOp
   * @param recPrefix
   * @return
   */  
   def getKeyedRecs(data:RDD[String], fieldDelimIn:String, keyFieldsOrdinals:Array[Integer],  
     mutOp:Option[String], recPrefix:String) : RDD[(Record, Iterable[String])] = {
     data.map(line => {
		   val fields = BasicUtils.getTrimmedFields(line, fieldDelimIn)
		   val key = Record(fields, keyFieldsOrdinals)
		   val value = mutOp match {
		     case Some(op:String) => line
		     case None => recPrefix + line
		   }
		   (key, value)
	   }).groupByKey()
   }
}