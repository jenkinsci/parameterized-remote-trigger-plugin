package org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Run;

public class BuildInfoExporterAction implements EnvironmentContributingAction {

    public static final String JOB_NAME_VARIABLE = "LAST_TRIGGERED_JOB_NAME";
    public static final String ALL_JOBS_NAME_VARIABLE = "TRIGGERED_JOB_NAMES";
    public static final String BUILD_URL_VARIABLE_PREFIX = "TRIGGERED_BUILD_URL_";
    public static final String BUILD_NUMBER_VARIABLE_PREFIX = "TRIGGERED_BUILD_NUMBER_";
    public static final String ALL_BUILD_NUMBER_VARIABLE_PREFIX = "TRIGGERED_BUILD_NUMBERS_";
    public static final String BUILD_RESULT_VARIABLE_PREFIX = "TRIGGERED_BUILD_RESULT_";
    public static final String BUILD_RUN_COUNT_PREFIX = "TRIGGERED_BUILD_RUN_COUNT_";
    public static final String RUN = "_RUN_";

    private List<BuildReference> builds;

    public BuildInfoExporterAction(Run<?, ?> parentBuild, BuildReference buildRef) {
        super();

        this.builds = new ArrayList<BuildReference>();
        addBuildReferenceSafe(buildRef);
    }

    public static BuildInfoExporterAction addBuildInfoExporterAction(@Nonnull Run<?, ?> parentBuild, String triggeredProjectName, int buildNumber, URL jobURL, BuildStatus buildResult) {
        BuildReference reference = new BuildReference(triggeredProjectName, buildNumber, jobURL, buildResult);

        BuildInfoExporterAction action;
        synchronized(parentBuild) {
            action = parentBuild.getAction(BuildInfoExporterAction.class);
            if (action == null) {
                action = new BuildInfoExporterAction(parentBuild, reference);
                parentBuild.addAction(action);
            } else {
                action.addBuildReference(reference);
            }
        }
        return action;
    }

    /**
     * Prevents duplicate build refs. The latest BuildReference wins (to reflect the latest Result).
     */
    private void addBuildReferenceSafe(BuildReference buildRef)
    {
        synchronized (builds) {
            removeDuplicates(builds, buildRef);
            builds.add(buildRef);
        }
    }

    /**
     * Finds and removes duplicates of <code>buildRef</code> in the <code>buildRefList</code> based on the <code>projectName</code> and <code>buildNumber</code> (only).
     * @return true if duplicates found and removed, false if nothing found
     */
    private boolean removeDuplicates(List<BuildReference> buildRefList, BuildReference buildRef) {
        List<BuildReference> duplicates = new ArrayList<BuildReference>();
        for(BuildReference build : buildRefList) {
            if(build.projectName.equals(buildRef.projectName) && build.buildNumber == buildRef.buildNumber) {
                duplicates.add(build);
            }
        }
        if(duplicates.size() > 0) {
            buildRefList.removeAll(duplicates);
            return true;
        } else {
            return false;
        }
    }

    public void addBuildReference(BuildReference buildRef) {
        addBuildReferenceSafe(buildRef);
    }

    public static class BuildReference {
        public final String projectName;
        public final int buildNumber;
        public final BuildStatus buildResult;
        public final URL jobURL;

        public BuildReference(String projectName, int buildNumber, URL jobURL, BuildStatus buildResult) {
            this.projectName = projectName;
            this.buildNumber = buildNumber;
            this.buildResult = buildResult;
            this.jobURL = jobURL;
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
            String sanatizedProjectName = sanitizeProjectName(project);
            List<BuildReference> refs = getBuildRefs(project);

            env.put(ALL_BUILD_NUMBER_VARIABLE_PREFIX + sanatizedProjectName, getBuildNumbersString(refs, ","));
            env.put(BUILD_RUN_COUNT_PREFIX + sanatizedProjectName, Integer.toString(refs.size()));
            for (BuildReference br : refs) {
                if (br.buildNumber != 0) {
                    String tiggeredBuildRunResultKey = BUILD_RESULT_VARIABLE_PREFIX + sanatizedProjectName + RUN + Integer.toString(br.buildNumber);
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
                env.put(JOB_NAME_VARIABLE, lastBuild.projectName);
                env.put(BUILD_NUMBER_VARIABLE_PREFIX + sanatizedProjectName, Integer.toString(lastBuild.buildNumber));
                env.put(BUILD_URL_VARIABLE_PREFIX + sanatizedProjectName, lastBuild.jobURL.toString());
                env.put(BUILD_RESULT_VARIABLE_PREFIX + sanatizedProjectName, lastBuild.buildResult.toString());
            }
        }
    }

    public static String sanitizeProjectName(String project)
    {
        if(project == null) return null;
        return project.replaceAll("[^a-zA-Z0-9]+", "_");
    }

    private List<BuildReference> getBuildRefs(String project) {
        List<BuildReference> refs = new ArrayList<BuildReference>();
        synchronized (builds) {
            for (BuildReference br : builds) {
                if (br.projectName.equals(project)) refs.add(br);
            }
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
     * Gets the unique set of project names that have a linked build.<br>
     * The later triggered jobs are later in the list. E.g.<br>
     * C, A, B -&gt; C, A, B <br>
     * C, A, B, A, C -&gt; B, A, C <br>
     *
     * @return Set of project names that have at least one build linked.
     */
    protected Set<String> getProjectsWithBuilds() {
        Set<String> projects = new LinkedHashSet<String>();
        synchronized (builds) {
            for (BuildReference br : this.builds) {
                if (br.buildNumber != 0) {
                    if(projects.contains(br.projectName)) projects.remove(br.projectName); //Move to the end
                    projects.add(br.projectName);
                }
            }
        }
        return projects;
    }
}