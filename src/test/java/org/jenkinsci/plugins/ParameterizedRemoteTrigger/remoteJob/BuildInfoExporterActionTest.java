package org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import jenkins.model.Jenkins;

public class BuildInfoExporterActionTest {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();

	private final static int PARALLEL_JOBS = 100;
	private final static int POOL_SIZE = 50;

	/**
	 * Same as {@link #testAddBuildInfoExporterAction_parallel()} but sequentially.
	 * @throws IOException
	 */
	@Test
	public void testAddBuildInfoExporterAction_sequential() throws IOException {
		Run<?, ?> parentBuild = new FreeStyleBuild(new FreeStyleProject((ItemGroup<TopLevelItem>) Jenkins.getInstance(), "ParentJob"));
		for (int i = 1; i <= PARALLEL_JOBS; i++) {
			BuildInfoExporterAction.addBuildInfoExporterAction(parentBuild, "Job" + i, i, new URL("http://jenkins/jobs/Job" + i), BuildStatus.SUCCESS);
		}
		BuildInfoExporterAction action = parentBuild.getAction(BuildInfoExporterAction.class);
		EnvVars env = new EnvVars();
		action.buildEnvVars(null, env);
		checkEnv(env);
	}

	/**
	 * We had ConcurrentModificationExceptions in the past. This test executes {@link BuildInfoExporterAction#addBuildInfoExporterAction(Run, String, int, URL, BuildStatus)}
	 * and  {@link BuildInfoExporterAction#buildEnvVars(hudson.model.AbstractBuild, EnvVars)} in parallel to provoke a ConcurrentModificationException (which should not occur anymore).
	 */
	@Test
	public void testAddBuildInfoExporterAction_parallel() throws IOException, InterruptedException, ExecutionException {
		Run<?, ?> parentBuild = new FreeStyleBuild(new FreeStyleProject((ItemGroup<TopLevelItem>) Jenkins.getInstance(), "ParentJob"));
		ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE);

		//Start parallel threads adding BuildInfoExporterActions AND one thread reading in parallel
		Future<?>[] addFutures = new Future<?>[PARALLEL_JOBS]; 
		for (int i = 1; i <= PARALLEL_JOBS; i++) {
			addFutures[i-1] = executor.submit(new AddActionCallable(parentBuild, i));
		}
		Future<?> envFuture = executor.submit(new BuildEnvVarsCallable(parentBuild));
		
		//Wait until all finished
		while(!isDone(addFutures) && !envFuture.isDone()) sleep(100);
		
		//Check result
		EnvVars env = (EnvVars)envFuture.get();
		checkEnv(env);
	}


	/**
	 * Sleeps millis millisseconds and swallows any InterruptedExceptions.
	 * @param millis
	 */
	private void sleep(int millis) {
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Checks if all futures are done. Additionally calls {@link Future#get()} to check if an Exception occured.
	 * @param addFutures
	 * @return
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	private boolean isDone(Future<?>[] addFutures) throws InterruptedException, ExecutionException {
		boolean done = true;
		for(Future<?> addFuture : addFutures) {
			if(!addFuture.isDone()) {
				done = false;
			} else {
				//Test get to check for exceptions
				addFuture.get();
			}
		}
		return done;
	}
	
	/**
	 * Checks if the env contains all expected variables
	 * @param env
	 */
	private void checkEnv(EnvVars env) {
		for(int i = 1; i <= PARALLEL_JOBS; i++) {
			Assert.assertEquals("TRIGGERED_BUILD_NUMBERS_Job"+i, ""+i, env.get("TRIGGERED_BUILD_NUMBERS_Job"+i));
			Assert.assertEquals("TRIGGERED_BUILD_NUMBERS_Job"+i, ""+i, env.get("TRIGGERED_BUILD_NUMBERS_Job"+i));
			Assert.assertEquals("TRIGGERED_BUILD_RESULT_Job"+i, "SUCCESS", env.get("TRIGGERED_BUILD_RESULT_Job"+i));
			Assert.assertEquals("TRIGGERED_BUILD_RESULT_Job" + i + "_RUN_"+i, "SUCCESS", env.get("TRIGGERED_BUILD_RESULT_Job" + i + "_RUN_"+i));
			Assert.assertEquals("TRIGGERED_BUILD_RUN_COUNT_Job"+i, "1", env.get("TRIGGERED_BUILD_RUN_COUNT_Job"+i));
			Assert.assertEquals("TRIGGERED_BUILD_URL_Job"+i, "http://jenkins/jobs/Job"+i, env.get("TRIGGERED_BUILD_URL_Job"+i));
		}
	}
	
	/**
	 * Calls {@link BuildInfoExporterAction#addBuildInfoExporterAction(Run, String, int, URL, BuildStatus)} a single time.
	 * This Callable is typically executed multiple tiles in parallel to provoke a ConcurrentModificationException (which should not occur anymore).
	 */
	private static class AddActionCallable implements Callable<Boolean> {
		Run<?, ?> parentBuild;
		private int i;

		public AddActionCallable(Run<?, ?> parentBuild, int i) {
			this.parentBuild = parentBuild;
			this.i = i;
		}

		public Boolean call() throws MalformedURLException {
			String jobName = "Job" + i;
			BuildInfoExporterAction.addBuildInfoExporterAction(parentBuild, jobName, i,
					new URL("http://jenkins/jobs/Job" + i), BuildStatus.SUCCESS);
			System.out.println("AddActionCallable finished for Job" + i);

			BuildInfoExporterAction action = parentBuild.getAction(BuildInfoExporterAction.class);
			Set<String> projectsWithBuilds = action.getProjectsWithBuilds();
			boolean success = projectsWithBuilds.contains(jobName);
			String message = String.format("AddActionCallable %s for %s (projects in list: %s)",
					(success ? "was successful " : "failed"), "Job"+i, projectsWithBuilds.size()) ;
			System.out.println(message);
			if(!success) Assert.fail(message);
			return success;
		}
	}

	/**
	 * Calls  {@link BuildInfoExporterAction#buildEnvVars(hudson.model.AbstractBuild, EnvVars)} repeatedly until all AddActionCallables finished.
	 * This way we try to provoke a ConcurrentModificationException (which should not occur anymore).
	 */
	private static class BuildEnvVarsCallable implements Callable<EnvVars> {
		Run<?, ?> parentBuild;

		public BuildEnvVarsCallable(Run<?, ?> parentBuild) {
			this.parentBuild = parentBuild;
		}

		public EnvVars call() throws MalformedURLException, InterruptedException, TimeoutException {
			BuildInfoExporterAction action = parentBuild.getAction(BuildInfoExporterAction.class);
			EnvVars env = new EnvVars();
			long startTime = System.currentTimeMillis();
			while (action == null || action.getProjectsWithBuilds().size() < PARALLEL_JOBS) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {}
				action = parentBuild.getAction(BuildInfoExporterAction.class);
				if (action != null) {
					//Provoke ConcurrentModificationException
					action.buildEnvVars(null, env);
				}
				if(System.currentTimeMillis() - startTime > 120000) throw new TimeoutException("Only " + action.getProjectsWithBuilds().size() + " of " + PARALLEL_JOBS + " jobs");
			}
			action.buildEnvVars(null, env);
			return env;
		}
	}
}
