package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.Run;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class BuildInfoExporterAction implements EnvironmentContributingAction {

    public static final String JOB_NAME_VARIABLE = "LAST_TRIGGERED_JOB_NAME";
    public static final String ALL_JOBS_NAME_VARIABLE = "TRIGGERED_JOB_NAMES";
    public static final String BUILD_NUMBER_VARIABLE_PREFIX = "TRIGGERED_BUILD_NUMBER_";
    public static final String ALL_BUILD_NUMBER_VARIABLE_PREFIX = "TRIGGERED_BUILD_NUMBERS_";
    public static final String BUILD_RESULT_VARIABLE_PREFIX = "TRIGGERED_BUILD_RESULT_";
    public static final String BUILD_RUN_COUNT_PREFIX = "TRIGGERED_BUILD_RUN_COUNT_";
    public static final String RUN = "_RUN_";

    private List<BuildReference> builds;

    public BuildInfoExporterAction(Run<?, ?> parentBuild, BuildReference buildRef) {
        super();

        this.builds = new ArrayList<BuildReference>();
        this.builds.add(buildRef);
    }

    static BuildInfoExporterAction addBuildInfoExporterAction(Run<?, ?> parentBuild, String triggeredProject, int buildNumber, Result buildResult) {
        BuildReference reference = new BuildReference(triggeredProject, buildNumber, buildResult);

        BuildInfoExporterAction action = parentBuild.getAction(BuildInfoExporterAction.class);
        if (action == null) {
            action = new BuildInfoExporterAction(parentBuild, reference);
            parentBuild.getActions().add(action);
        } else {
            action.addBuildReference(reference);
        }
        return action;
    }

    public void addBuildReference(BuildReference buildRef) {
        this.builds.add(buildRef);
    }

    public static class BuildReference {
        public final String projectName;
        public final int buildNumber;
        public final Result buildResult;

        public BuildReference(String projectName, int buildNumber, Result buildResult) {
            this.projectName = projectName;
            this.buildNumber = buildNumber;
            this.buildResult = buildResult;
        }
    }

    public String getIconFileName() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getDisplayName() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getUrlName() {
        // TODO Auto-generated method stub
        return null;
    }

    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
        for (String project : getProjectsWithBuilds()) {
            String sanatizedBuildName = project.replaceAll("[^a-zA-Z0-9]+", "_");
            List<BuildReference> refs = getBuildRefs(project);

            env.put(ALL_BUILD_NUMBER_VARIABLE_PREFIX + sanatizedBuildName, getBuildNumbersString(refs, ","));
            env.put(BUILD_RUN_COUNT_PREFIX + sanatizedBuildName, Integer.toString(refs.size()));
            for (BuildReference br : refs) {
                if (br.buildNumber != 0) {
                    String tiggeredBuildRunResultKey = BUILD_RESULT_VARIABLE_PREFIX + sanatizedBuildName + RUN + Integer.toString(br.buildNumber);
                    env.put(tiggeredBuildRunResultKey, br.buildResult.toString());
                }
            }
            BuildReference lastBuild = null;
            for (int i = (refs.size()); i > 0; i--) {
                if (refs.get(i - 1).buildNumber != 0) {
                    lastBuild = refs.get(i - 1);
                    break;
                }
            }
            if (lastBuild != null) {
                env.put(BUILD_NUMBER_VARIABLE_PREFIX + sanatizedBuildName, Integer.toString(lastBuild.buildNumber));
                env.put(BUILD_RESULT_VARIABLE_PREFIX + sanatizedBuildName, lastBuild.buildResult.toString());
            }
        }
    }

    private List<BuildReference> getBuildRefs(String project) {
        List<BuildReference> refs = new ArrayList<BuildReference>();
        for (BuildReference br : builds) {
            if (br.projectName.equals(project)) refs.add(br);
        }
        return refs;
    }

    /**
     * Gets a string for all of the build numbers
     *
     * @param refs List of build references to process.
     * @param separator
     * @return String containing all the build numbers from refs, never null but
     * can be empty
     */
    private String getBuildNumbersString(List<BuildReference> refs, String separator) {
        StringBuilder buf = new StringBuilder();
        boolean first = true;

        for (BuildReference s : refs) {
            if (s.buildNumber != 0) {
                if (first) {
                    first = false;
                } else {
                    buf.append(separator);
                }
                buf.append(s.buildNumber);
            }
        }
        return buf.toString();
    }

    /**
     * Gets the unique set of project names that have a linked build.
     *
     * @return Set of project names that have at least one build linked.
     */
    private Set<String> getProjectsWithBuilds() {
        Set<String> projects = new HashSet<String>();

        for (BuildReference br : this.builds) {
            if (br.buildNumber != 0) {
                projects.add(br.projectName);
            }
        }
        return projects;
    }
}