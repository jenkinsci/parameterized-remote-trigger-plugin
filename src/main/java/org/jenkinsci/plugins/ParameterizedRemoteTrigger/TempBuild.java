package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

import hudson.FilePath;
import hudson.model.Build;

public class TempBuild extends Build<TempProject, TempBuild> {

	public TempBuild(TempProject job, Calendar timestamp) {
		super(job, timestamp);
	}

	public TempBuild(TempProject project, File buildDir) throws IOException {
		super(project, buildDir);
	}

	public TempBuild(TempProject job) throws IOException {
		super(job);
	}

	protected TempBuild(TempProject job, FilePath workspace) {
		super(job, new GregorianCalendar());
		setWorkspace(workspace);
	}

	/*
	 * return -1 to make duration be N/A otherwise
	 * 		null pointer exception if @Override is removed
	 * @see hudson.model.Run#getEstimatedDuration()
	 */
	@Override
	public long getEstimatedDuration() {
		return -1;
	};

	@Override
	public void run() {}
}