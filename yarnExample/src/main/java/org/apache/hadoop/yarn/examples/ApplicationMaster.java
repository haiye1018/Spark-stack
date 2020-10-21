package org.apache.hadoop.yarn.examples;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.util.ClassUtil;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.ApplicationConstants.Environment;
import org.apache.hadoop.yarn.api.protocolrecords.RegisterApplicationMasterResponse;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.ContainerStatus;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.timeline.TimelineEntity;
import org.apache.hadoop.yarn.api.records.timeline.TimelineEvent;
import org.apache.hadoop.yarn.api.records.timeline.TimelinePutResponse;
import org.apache.hadoop.yarn.client.api.AMRMClient.ContainerRequest;
import org.apache.hadoop.yarn.client.api.TimelineClient;
import org.apache.hadoop.yarn.client.api.async.AMRMClientAsync;
import org.apache.hadoop.yarn.client.api.async.NMClientAsync;
import org.apache.hadoop.yarn.client.api.async.impl.NMClientAsyncImpl;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.ConverterUtils;

public class ApplicationMaster {
	private final AtomicInteger sleepSeconds = new AtomicInteger(0);
	private class LaunchContainerTask implements Runnable {
		Container container;
		public LaunchContainerTask(Container container) {
			this.container = container;
		}
		private void addToLocalResources(FileSystem fs, String fileSrcPath,
				String fileDstPath, String appId,
				Map<String, LocalResource> localResources)
				throws IllegalArgumentException, IOException {
			String suffix = "mytest" + "/" + appId + "/" + fileDstPath;
			Path dst = new Path(fs.getHomeDirectory(), suffix);
			FileStatus scFileStatus = fs.getFileStatus(dst);
			LocalResource scRsrc = LocalResource.newInstance(
					ConverterUtils.getYarnUrlFromPath(dst), LocalResourceType.FILE,
					LocalResourceVisibility.APPLICATION, scFileStatus.getLen(),
					scFileStatus.getModificationTime());

			localResources.put(fileDstPath, scRsrc);

		}
		public void run() {
			Map<String, String> env = new HashMap<String, String>();
			env.put(Environment.CLASSPATH.name(), System.getenv(Environment.CLASSPATH.name()));
			
			Map<String, LocalResource> localResources = new HashMap<String, LocalResource>();
			String thisJar = ClassUtil.findContainingJar(ApplicationMaster.class);
			String thisJarBaseName = FilenameUtils.getName(thisJar);
			String appId = System.getenv("appId");
			Configuration conf = new Configuration();
			FileSystem fs;
			try {
				fs = FileSystem.get(conf);
				addToLocalResources(fs, thisJar, thisJarBaseName, appId.toString(),
						localResources);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			List<String> commands = new LinkedList<String>();
			//commands.add("sleep " + sleepSeconds.addAndGet(1));
			StringBuilder command = new StringBuilder();
			command.append(Environment.JAVA_HOME.$$()).append("/bin/java  ");
			command.append("-Dlog4j.configuration=container-log4j.properties ");
			command.append("-Dyarn.app.container.log.dir=" +
					ApplicationConstants.LOG_DIR_EXPANSION_VAR + " ");
			command.append("-Dyarn.app.container.log.filesize=0 ");
			command.append("-Dhadoop.root.logger=INFO,CLA ");
			command.append("org.apache.hadoop.yarn.examples.JavaPi ");
			command.append("1>>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/stdout ");
			command.append("2>>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/stderr ");
			commands.add(command.toString());
			
			ContainerLaunchContext ctx = ContainerLaunchContext.newInstance(
					localResources, env, commands, null, null, null);
			amNMClient.startContainerAsync(container, ctx);
			LOG.info("Container start... ");
		}
	}

	private class RMCallbackHandler implements AMRMClientAsync.CallbackHandler {
		public void onContainersCompleted(List<ContainerStatus> statuses) {
			for (ContainerStatus status : statuses) {
				LOG.info("Container Completed: " + status.getContainerId().toString() 
						+ " exitStatus="+ status.getExitStatus());
				if (status.getExitStatus() != 0) {
					// restart
				}
				ContainerId id = status.getContainerId();
				runningContainers.remove(id);
				numCompletedConatiners.addAndGet(1);
			}
		}

		public void onContainersAllocated(List<Container> containers) {
			for (Container c : containers) {
				LOG.info("Container Allocated"
						+ ", id=" + c.getId() 
						+ ", containerNode=" + c.getNodeId());
				exeService.submit(new LaunchContainerTask(c));
				runningContainers.put(c.getId(), c);
			}
		}

		public void onShutdownRequest() {
		}

		public void onNodesUpdated(List<NodeReport> updatedNodes) {

		}

		public float getProgress() {
			float progress = 0;
			return progress;
		}

		public void onError(Throwable e) {
			amRMClient.stop();
		}

	}

	private class NMCallbackHandler implements NMClientAsync.CallbackHandler {

		public void onContainerStarted(ContainerId containerId,
				Map<String, ByteBuffer> allServiceResponse) {
			LOG.info("Container Stared " + containerId.toString());

		}

		public void onContainerStatusReceived(ContainerId containerId,
				ContainerStatus containerStatus) {

		}

		public void onContainerStopped(ContainerId containerId) {
			// TODO Auto-generated method stub

		}

		public void onStartContainerError(ContainerId containerId, Throwable t) {
			// TODO Auto-generated method stub

		}

		public void onGetContainerStatusError(ContainerId containerId,
				Throwable t) {
			// TODO Auto-generated method stub

		}

		public void onStopContainerError(ContainerId containerId, Throwable t) {
			// TODO Auto-generated method stub

		}

	}


	
	
	@SuppressWarnings("rawtypes")
	AMRMClientAsync amRMClient = null;
	NMClientAsyncImpl amNMClient = null;
	
	AtomicInteger numTotalContainers = new AtomicInteger(10);
	AtomicInteger numCompletedConatiners = new AtomicInteger(0);
	ExecutorService exeService = Executors.newCachedThreadPool();
	Map<ContainerId, Container> runningContainers = new ConcurrentHashMap<ContainerId, Container>();
	
	private static final Log LOG = LogFactory.getLog(ApplicationMaster.class);

	@SuppressWarnings("unchecked")
	void run() throws YarnException, IOException {

		logInformation();
		Configuration conf = new Configuration();

		// 1. create amRMClient
		
		amRMClient = AMRMClientAsync.createAMRMClientAsync(
				1000, new RMCallbackHandler());
		amRMClient.init(conf);
		amRMClient.start();
		// 2. Create nmClientAsync
		amNMClient = new NMClientAsyncImpl(new NMCallbackHandler());
		amNMClient.init(conf);
		amNMClient.start();

		// 3. register with RM and this will heartbeating to RM
		RegisterApplicationMasterResponse response = amRMClient
				.registerApplicationMaster(NetUtils.getHostname(), -1, "");

		// 4. Request containers
		response.getContainersFromPreviousAttempts();
		int numContainers = 10;

		for (int i = 0; i < numTotalContainers.get(); i++) {
			ContainerRequest containerAsk = new ContainerRequest(
					//100*10M + 1vcpu
					Resource.newInstance(100, 1), null, null,
					Priority.newInstance(0));
			amRMClient.addContainerRequest(containerAsk);
		}
	}
	
	void waitComplete() throws YarnException, IOException{
		while(numTotalContainers.get() != numCompletedConatiners.get()){
			try{
				Thread.sleep(1000);
				LOG.info("waitComplete" + 
					", numTotalContainers=" + numTotalContainers.get() +
					", numCompletedConatiners=" + numCompletedConatiners.get());
			} catch (InterruptedException ex){}
		}
		LOG.info("ShutDown exeService Start");
		exeService.shutdown();
		LOG.info("ShutDown exeService Complete");
		amNMClient.stop();
		LOG.info("amNMClient  stop  Complete");
		amRMClient.unregisterApplicationMaster(FinalApplicationStatus.SUCCEEDED, "dummy Message", null);
		LOG.info("unregisterApplicationMaster  Complete");
		amRMClient.stop();
		LOG.info("amRMClient  stop Complete");
	}

	void logInformation() {
		System.out.println("This is System.out.println");
		System.err.println("This is System.err.println");
		System.out.println(ApplicationConstants.LOG_DIR_EXPANSION_VAR);

		String containerIdStr = System
				.getenv(ApplicationConstants.Environment.CONTAINER_ID.name());

		LOG.info("containerIdStr " + containerIdStr);

		ContainerId containerId = ConverterUtils.toContainerId(containerIdStr);
		ApplicationAttemptId appAttemptId = containerId
				.getApplicationAttemptId();
		LOG.info("appAttemptId " + appAttemptId.toString());
	}

	public static void main(String[] args) throws Exception {
		ApplicationMaster am = new ApplicationMaster();
		am.run();
		am.waitComplete();
	}
}