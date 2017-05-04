package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import java.io.IOException;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.Project;
import hudson.model.TaskListener;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;

public class TempProject extends Project<TempProject, TempBuild> {
	private String targets;

	private String antName;
	private String antOpts;
	private String buildFile;
	private String properties;

	private Launcher launcher;
	private TaskListener listener;
	FilePath projectWorkspace;

	protected TempProject(ItemGroup parent, String name) {
		super(parent, name);

		this.disabled = false;
	}

	@Override
	protected TempBuild newBuild() throws IOException {
		return new TempBuild(this, this.getProjectWorkspace());
	}

	@Override
	public DescribableList<Publisher, Descriptor<Publisher>> getPublishersList() {
		return null;
	}

	@Override
	protected Class<TempBuild> getBuildClass() {
		return TempBuild.class;
	}

	@Override
	public boolean isFingerprintConfigured() {
		return false;
	}

	@Override
	protected void buildDependencyGraph(DependencyGraph paramDependencyGraph) {}

	public String getTargets() {
		return targets;
	}

	public void setTargets(String targets) {
		this.targets = targets;
	}

	public String getAntName() {
		return antName;
	}

	public void setAntName(String antName) {
		this.antName = antName;
	}

	public String getAntOpts() {
		return antOpts;
	}

	public void setAntOpts(String antOpts) {
		this.antOpts = antOpts;
	}

	public String getBuildFile() {
		return buildFile;
	}

	public void setBuildFile(String buildFile) {
		this.buildFile = buildFile;
	}

	public String getPropertiesStr() {
		return properties;
	}

	public void setPropertiesStr(String properties) {
		this.properties = properties;
	}

	public Launcher getLauncher() {
		return launcher;
	}

	public void setLauncher(Launcher launcher) {
		this.launcher = launcher;
	}

	public TaskListener getListener() {
		return listener;
	}

	public void setListener(TaskListener listener) {
		this.listener = listener;
	}

	public FilePath getProjectWorkspace() {
		return projectWorkspace;
	}

	public void setProjectWorkspace(FilePath projectWorkspace) {
		this.projectWorkspace = projectWorkspace;
	}
}