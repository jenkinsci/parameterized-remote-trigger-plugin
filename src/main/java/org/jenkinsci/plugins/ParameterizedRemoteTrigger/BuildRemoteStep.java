package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.StringReader;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Jenkins workflow step for triggering a Jenkins job on a remote server.
 */
public class BuildRemoteStep extends AbstractStepImpl {
    private final String remoteJenkinsName;
    private final String job;
    private String token = "";
    private boolean wait = DescriptorImpl.defaultWait;
    private boolean propagate = DescriptorImpl.defaultPropagate;
    private Map<String, String> parameters = new LinkedHashMap<String, String>();

    @DataBoundConstructor
    public BuildRemoteStep(String remoteJenkinsName, String job) {
        this.remoteJenkinsName = remoteJenkinsName;
        this.job = job.trim();
    }

    public String getRemoteJenkinsName() {
        return remoteJenkinsName;
    }

    public String getJob() {
        return job;
    }

    public String getToken() {
        return token;
    }

    @DataBoundSetter
    public void setToken(String token) {
        this.token = Util.fixNull(token);
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    @DataBoundSetter
    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public boolean getWait() {
        return wait;
    }

    @DataBoundSetter
    public void setWait(boolean wait) {
        this.wait = wait;
    }

    public boolean getPropagate() {
        return propagate;
    }

    @DataBoundSetter
    public void setPropagate(boolean propagate) {
        this.propagate = propagate;
    }

    public static class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L;

        @Inject
        private transient BuildRemoteStep step;
        @StepContextParameter
        private transient Run run;
        @StepContextParameter
        private transient TaskListener listener;

        @Override
        protected Void run() throws Exception {
            RemoteBuildConfiguration remoteBuildConfiguration = new RemoteBuildConfiguration(
                    step.getRemoteJenkinsName(), !step.getPropagate(), step.getJob(), step.getToken(),
                    parametersToString(step.getParameters()), false, null, null, false, step.getPropagate(), 10);
            remoteBuildConfiguration.perform(run, null, null, listener);
            return null;
        }

        private String parametersToString(Map<String, String> parameters) {
            StringBuilder parametersBuilder = new StringBuilder();
            if (parameters != null) {
                for (Map.Entry<String, String> parameter : parameters.entrySet()) {
                    parametersBuilder.append(parameter.getKey()).append('=').append(parameter.getValue()).append('\n');
                }
            }
            return parametersBuilder.toString().trim();
        }
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public static boolean defaultWait = true;
        public static boolean defaultPropagate = true;

        @Inject
        private transient RemoteBuildConfiguration.DescriptorImpl remoteBuildConfigurationDescriptor;

        public DescriptorImpl() {
            super(Execution.class);
            load();
        }

        @Override
        public String getFunctionName() {
            return "buildRemote";
        }

        @Override
        public String getDisplayName() {
            return "Trigger a remote parameterized job";
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) throws Exception {
            Map<String, String> parameters = null;
            if (arguments.containsKey("parameters")) {
                parameters = (Map<String, String>) arguments.get("parameters");
                arguments.remove("parameters");
            }

            BuildRemoteStep step = (BuildRemoteStep)super.newInstance(arguments);

            if (parameters != null) {
                step.setParameters(parameters);
            }

            return step;
        }

        @Override
        public Step newInstance(@CheckForNull StaplerRequest req, @Nonnull JSONObject formData) throws FormException {
            String parameters = formData.getString("parameters");
            formData.remove("parameters");

            Map<String, String> parsedParameters;
            try {
                parsedParameters = parseParameters(parameters);
            } catch (IOException e) {
                throw new FormException(e, "parameters");
            }

            BuildRemoteStep step = (BuildRemoteStep)super.newInstance(req, formData);
            step.setParameters(parsedParameters);
            return step;
        }

        public ListBoxModel doFillRemoteJenkinsNameItems() {
            ListBoxModel model = new ListBoxModel();

            for (RemoteJenkinsServer site : remoteBuildConfigurationDescriptor.getRemoteSites()) {
                model.add(site.getDisplayName());
            }

            return model;
        }

        /**
         * Reads the parameters from the given string as if it where a property file.
         * @param parameters a string containing a list of properties in property file format
         * @return a map containing the parameter names and values
         * @throws IOException when the parameters could not be read
         */
        private Map<String, String> parseParameters(String parameters) throws IOException {
            Properties parameterProperties = new Properties();
            parameterProperties.load(new StringReader(parameters));

            Map<String, String> parsedParameters = new LinkedHashMap<String, String>();
            Enumeration parameterNames = parameterProperties.propertyNames();
            while (parameterNames.hasMoreElements()) {
                String parameterName = (String)parameterNames.nextElement();
                parsedParameters.put(parameterName, parameterProperties.getProperty(parameterName));
            }
            return parsedParameters;
        }
    }
}
