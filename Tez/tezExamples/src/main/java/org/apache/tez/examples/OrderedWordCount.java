/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tez.examples;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.tez.client.CallerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.tez.runtime.library.api.KeyValueReader;
import org.apache.tez.runtime.library.api.KeyValueWriter;
import org.apache.tez.runtime.library.api.KeyValuesReader;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.ToolRunner;
import org.apache.tez.client.TezClient;
import org.apache.tez.dag.api.DAG;
import org.apache.tez.dag.api.DataSinkDescriptor;
import org.apache.tez.dag.api.DataSourceDescriptor;
import org.apache.tez.dag.api.Edge;
import org.apache.tez.dag.api.ProcessorDescriptor;
import org.apache.tez.dag.api.TezConfiguration;
import org.apache.tez.dag.api.Vertex;
import org.apache.tez.examples.WordCount.TokenProcessor;
import org.apache.tez.mapreduce.input.MRInput;
import org.apache.tez.mapreduce.output.MROutput;
import org.apache.tez.mapreduce.processor.SimpleMRProcessor;
import org.apache.tez.runtime.api.ProcessorContext;
import org.apache.tez.runtime.library.api.KeyValueWriter;
import org.apache.tez.runtime.library.api.KeyValuesReader;
import org.apache.tez.runtime.library.conf.OrderedPartitionedKVEdgeConfig;
import org.apache.tez.runtime.library.partitioner.HashPartitioner;
import org.apache.tez.runtime.library.processor.SimpleProcessor;

import org.apache.commons.cli.Options;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.tez.common.TezUtilsInternal;
import org.apache.tez.hadoop.shim.DefaultHadoopShim;
import org.apache.tez.hadoop.shim.HadoopShim;
import org.apache.tez.hadoop.shim.HadoopShimsLoader;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceAudience.Private;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.tez.dag.api.TezException;
import org.apache.tez.dag.api.client.DAGClient;
import org.apache.tez.dag.api.client.DAGStatus;
import org.apache.tez.dag.api.client.StatusGetOpts;
import org.apache.tez.runtime.library.api.TezRuntimeConfiguration;

import com.google.common.base.Preconditions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;

/**
 * Simple example that extends the WordCount example to show a chain of processing.
 * The example extends WordCount by sorting the words by their count.
 */
public class OrderedWordCount extends Configured implements Tool {
  
  private static String SORTER = "Sorter";
  private static final Logger LOG = LoggerFactory.getLogger(OrderedWordCount.class);
  private static String INPUT = "Input";
  private static String OUTPUT = "Output";
  private static String TOKENIZER = "Tokenizer";
  private static String SUMMATION = "Summation";
  private HadoopShim hadoopShim;
  private boolean disableSplitGrouping = false;
  private boolean generateSplitInClient = false;
  /*
   * Example code to write a processor in Tez.
   * Processors typically apply the main application logic to the data.
   * TokenProcessor tokenizes the input data.
   * It uses an input that provide a Key-Value reader and writes
   * output to a Key-Value writer. The processor inherits from SimpleProcessor
   * since it does not need to handle any advanced constructs for Processors.
   */
  public static class TokenProcessor extends SimpleProcessor {
    IntWritable one = new IntWritable(1);
    Text word = new Text();

    public TokenProcessor(ProcessorContext context) {
      super(context);
    }

    @Override
    public void run() throws Exception {
      Preconditions.checkArgument(getInputs().size() == 1);
      Preconditions.checkArgument(getOutputs().size() == 1);
      // the recommended approach is to cast the reader/writer to a specific type instead
      // of casting the input/output. This allows the actual input/output type to be replaced
      // without affecting the semantic guarantees of the data type that are represented by
      // the reader and writer.
      // The inputs/outputs are referenced via the names assigned in the DAG.
      KeyValueReader kvReader = (KeyValueReader) getInputs().get(INPUT).getReader();
      KeyValueWriter kvWriter = (KeyValueWriter) getOutputs().get(SUMMATION).getWriter();
      while (kvReader.next()) {
        StringTokenizer itr = new StringTokenizer(kvReader.getCurrentValue().toString());
        while (itr.hasMoreTokens()) {
          word.set(itr.nextToken());
          // Count 1 every time a word is observed. Word is the key a 1 is the value
          kvWriter.write(word, one);
        }
      }
    }

  }
  /*
   * SumProcessor similar to WordCount except that it writes the count as key and the 
   * word as value. This is because we can and ordered partitioned key value edge to group the 
   * words with the same count (as key) and order the counts.
   */
  public static class SumProcessor extends SimpleProcessor {
    public SumProcessor(ProcessorContext context) {
      super(context);
    }

    @Override
    public void run() throws Exception {
      Preconditions.checkArgument(getInputs().size() == 1);
      Preconditions.checkArgument(getOutputs().size() == 1);
      // the recommended approach is to cast the reader/writer to a specific type instead
      // of casting the input/output. This allows the actual input/output type to be replaced
      // without affecting the semantic guarantees of the data type that are represented by
      // the reader and writer.
      // The inputs/outputs are referenced via the names assigned in the DAG.
      KeyValueWriter kvWriter = (KeyValueWriter) getOutputs().get(SORTER).getWriter();
      KeyValuesReader kvReader = (KeyValuesReader) getInputs().get(TOKENIZER).getReader();
      while (kvReader.next()) {
        Text word = (Text) kvReader.getCurrentKey();
        int sum = 0;
        for (Object value : kvReader.getCurrentValues()) {
          sum += ((IntWritable) value).get();
        }
        // write the sum as the key and the word as the value
        kvWriter.write(new IntWritable(sum), word);
      }
    }
  }
  
  /**
   * No-op sorter processor. It does not need to apply any logic since the ordered partitioned edge 
   * ensures that we get the data sorted and grouped by the the sum key.
   */
  public static class NoOpSorter extends SimpleMRProcessor {

    public NoOpSorter(ProcessorContext context) {
      super(context);
    }

    @Override
    public void run() throws Exception {
      Preconditions.checkArgument(getInputs().size() == 1);
      Preconditions.checkArgument(getOutputs().size() == 1);
      KeyValueWriter kvWriter = (KeyValueWriter) getOutputs().get(OUTPUT).getWriter();
      KeyValuesReader kvReader = (KeyValuesReader) getInputs().get(SUMMATION).getReader();
      while (kvReader.next()) {
        Object sum = kvReader.getCurrentKey();
        for (Object word : kvReader.getCurrentValues()) {
          kvWriter.write(word, sum);
        }
      }
      // deriving from SimpleMRProcessor takes care of committing the output
    }
  }
  
  public static DAG createDAG(TezConfiguration tezConf, String inputPath, String outputPath,
      int numPartitions, boolean disableSplitGrouping, boolean isGenerateSplitInClient, String dagName) throws IOException {

    DataSourceDescriptor dataSource = MRInput.createConfigBuilder(new Configuration(tezConf),
        TextInputFormat.class, inputPath).groupSplits(!disableSplitGrouping)
          .generateSplitsInAM(!isGenerateSplitInClient).build();

    DataSinkDescriptor dataSink = MROutput.createConfigBuilder(new Configuration(tezConf),
        TextOutputFormat.class, outputPath).build();

    Vertex tokenizerVertex = Vertex.create(TOKENIZER, ProcessorDescriptor.create(
        TokenProcessor.class.getName()));
    tokenizerVertex.addDataSource(INPUT, dataSource);

    // Use Text key and IntWritable value to bring counts for each word in the same partition
    // The setFromConfiguration call is optional and allows overriding the config options with
    // command line parameters.
    OrderedPartitionedKVEdgeConfig summationEdgeConf = OrderedPartitionedKVEdgeConfig
        .newBuilder(Text.class.getName(), IntWritable.class.getName(),
            HashPartitioner.class.getName())
        .setFromConfiguration(tezConf)
        .build();

    // This vertex will be reading intermediate data via an input edge and writing intermediate data
    // via an output edge.
    Vertex summationVertex = Vertex.create(SUMMATION, ProcessorDescriptor.create(
        SumProcessor.class.getName()), numPartitions);
    
    // Use IntWritable key and Text value to bring all words with the same count in the same 
    // partition. The data will be ordered by count and words grouped by count. The
    // setFromConfiguration call is optional and allows overriding the config options with
    // command line parameters.
    OrderedPartitionedKVEdgeConfig sorterEdgeConf = OrderedPartitionedKVEdgeConfig
        .newBuilder(IntWritable.class.getName(), Text.class.getName(),
            HashPartitioner.class.getName())
        .setFromConfiguration(tezConf)
        .build();

    // Use 1 task to bring all the data in one place for global sorted order. Essentially the number
    // of partitions is 1. So the NoOpSorter can be used to produce the globally ordered output
    Vertex sorterVertex = Vertex.create(SORTER, ProcessorDescriptor.create(
        NoOpSorter.class.getName()), 1);
    sorterVertex.addDataSink(OUTPUT, dataSink);

    // No need to add jar containing this class as assumed to be part of the tez jars.
    
    DAG dag = DAG.create(dagName);
    dag.addVertex(tokenizerVertex)
        .addVertex(summationVertex)
        .addVertex(sorterVertex)
        .addEdge(
            Edge.create(tokenizerVertex, summationVertex,
                summationEdgeConf.createDefaultEdgeProperty()))
        .addEdge(
            Edge.create(summationVertex, sorterVertex, sorterEdgeConf.createDefaultEdgeProperty()));
    return dag;  
  }
  /**
   * @param dag           the dag to execute
   * @param printCounters whether to print counters or not
   * @param logger        the logger to use while printing diagnostics
   * @return Zero indicates success, non-zero indicates failure
   * @throws TezException
   * @throws InterruptedException
   * @throws IOException
   */
  public int runDag(DAG dag, TezClient tezClient, Logger logger) throws TezException,
      InterruptedException, IOException {
    tezClient.waitTillReady();

    CallerContext callerContext = CallerContext.create("TezExamples",
        "Tez Example DAG: " + dag.getName());
    ApplicationId appId = tezClient.getAppMasterApplicationId();
    if (hadoopShim == null) {
      Configuration conf = (getConf() == null ? new Configuration(false) : getConf());
      hadoopShim = new HadoopShimsLoader(conf).getHadoopShim();
    }

    if (appId != null) {
      TezUtilsInternal.setHadoopCallerContext(hadoopShim, appId);
      callerContext.setCallerIdAndType(appId.toString(), "TezExampleApplication");
    }
    dag.setCallerContext(callerContext);

    DAGClient dagClient = tezClient.submitDAG(dag);
    Set<StatusGetOpts> getOpts = Sets.newHashSet();
    DAGStatus dagStatus;
    dagStatus = dagClient.waitForCompletionWithStatusUpdates(getOpts);

    if (dagStatus.getState() != DAGStatus.State.SUCCEEDED) {
      logger.info("DAG diagnostics: " + dagStatus.getDiagnostics());
      return -1;
    }
    return 0;
  }
  
  protected int runJob(String[] args, TezConfiguration tezConf,
      TezClient tezClient) throws Exception {
    DAG dag = createDAG(tezConf, args[0], args[1],
        1,
        disableSplitGrouping,
        generateSplitInClient, 
		"OrderedWordCount");
    LOG.info("Running OrderedWordCount");
    return runDag(dag, tezClient, LOG);
  }
  
  @Override
  public final int run(String[] args) throws Exception {
    Configuration conf = getConf();
    String[] otherArgs = new GenericOptionsParser(conf,args).getRemainingArgs();
	
	// shims相关类是用来兼容不同的hadoop版本的
    hadoopShim = new HadoopShimsLoader(conf).getHadoopShim();

    int result = validateArgs(otherArgs);
    if (result != 0) {
	  printUsage();
      return result;
    }

    TezConfiguration tezConf = new TezConfiguration(getConf());
    UserGroupInformation.setConfiguration(tezConf);
	
	// the tez client instance to use to run the DAG if any custom monitoring is required.
	TezClient tezClient = TezClient.create(getClass().getSimpleName(), tezConf);
    tezClient.start();
	
    try {
      return runJob(otherArgs, tezConf, tezClient);
    } finally {
       tezClient.stop();
    }
  }
  
  protected void printUsage() {
    System.err.println("Usage: " + " in out [numPartitions]");
  }

  protected int validateArgs(String[] otherArgs) {
    if (otherArgs.length < 2 || otherArgs.length > 3) {
      return 2;
    }
    return 0;
  }
  public static void main(String[] args) throws Exception {
    int res = ToolRunner.run(new Configuration(), new OrderedWordCount(), args);
    System.exit(res);
  }
}
