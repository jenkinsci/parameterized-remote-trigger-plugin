package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import hudson.model.FreeStyleProject;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import io.jenkins.plugins.opentelemetry.OpenTelemetryConfiguration;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.mock.Expectation;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.Parameter.param;


public class OpenTelemeterTest {
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();


    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this);

    private MockServerClient mockServerClient;

    private void disableAuth() {
        jenkinsRule.jenkins.setAuthorizationStrategy(AuthorizationStrategy.Unsecured.UNSECURED);
        jenkinsRule.jenkins.setSecurityRealm(SecurityRealm.NO_AUTHENTICATION);
        jenkinsRule.jenkins.setCrumbIssuer(null);
    }

    @Test
    public void testRemoteBuild() throws Exception {
        disableAuth();
        initOpenTelemetry();
        String[] allExpectation = setupRemoteJenkinsMock();
        FreeStyleProject project = createProjectTriggerFrom();
        //Trigger build
        jenkinsRule.waitUntilNoActivity();
        jenkinsRule.buildAndAssertSuccess(project);
        mockServerClient.verify(allExpectation);
    }

    @NonNull
    private FreeStyleProject createProjectTriggerFrom() throws IOException {
        FreeStyleProject project = jenkinsRule.createFreeStyleProject();
        RemoteBuildConfiguration configuration = new RemoteBuildConfiguration();
        configuration.setJob(createJobUrl());
        configuration.setPreventRemoteBuildQueue(false);
        configuration.setBlockBuildUntilComplete(true);
        configuration.setPollInterval(1);
        configuration.setHttpGetReadTimeout(1000);
        configuration.setHttpPostReadTimeout(1000);
        configuration.setUseCrumbCache(false);
        configuration.setUseJobInfoCache(false);
        configuration.setEnhancedLogging(true);
        configuration.setTrustAllCertificates(true);
        project.getBuildersList().add(configuration);
        return project;
    }

    @NonNull
    private String[] setupRemoteJenkinsMock() {
        Expectation[] metaExp = mockServerClient.when(
                request()
                        .withMethod("GET")
                        .withPath("/job/remote1/api/json")
                        .withQueryStringParameters(
                                param("tree", "actions[parameterDefinitions],property[parameterDefinitions],name,fullName,displayName,fullDisplayName,url")
                        )
        ).respond(
                response()
                        .withBody(json(ImmutableMap.of(
                                "_class", "org.jenkinsci.plugins.workflow.job.WorkflowJob",
                                "actions", ImmutableList.of(ImmutableMap.of("_class", "someactionclass")),
                                "displayName", "remote1",
                                "fullDisplayName", "remote1",
                                "fullName", "remote1",
                                "name", "remote1",
                                "url", createJobUrl(),
                                "property", ImmutableList.of(ImmutableMap.of("_class", "somepropertyclass"))
                        )))
        );
        String jobQueue = "http://localhost:" + mockServerClient.getPort() + "/queue/item/311/";
        Expectation[] jobBuildExp = mockServerClient.when(
                request()
                        .withMethod("POST")
                        .withPath("/job/remote1/build")
                        .withQueryStringParameters(
                                param("delay", "0")
                        )
                        .withHeader("traceparent", "00-[0-9A-F]{32}-[0-9A-F]{16}-01") //https://www.w3.org/TR/trace-context/#traceparent-header-field-values

        ).respond(
                response()
                        .withHeader("location", jobQueue)
        );

        Map<String, Object> mockQueue = ImmutableMap.of(
                "_class", "hudson.model.Queue$LeftItem",
                "blocked", false,
                "buildable", false,
                "id", 311,
                "executable", ImmutableMap.of(
                        "_class", "org.jenkinsci.plugins.workflow.job.WorkflowRun",
                        "number", 34,
                        "url", "https://jenkins-himalia.aws-devops.itsma-ng.net/job/test1/34/"
                )

        );
        Expectation[] queueExp = mockServerClient.when(
                request()
                        .withMethod("GET")
                        .withPath("/queue/item/311/api/json/")


        ).respond(
                response()
                        .withBody(json(mockQueue))
        );


        Expectation[] jobResultExp = mockServerClient.when(
                request()
                        .withMethod("GET")
                        .withPath("/job/test1/34/api/json/")
                        .withQueryStringParameter("tree", "result,building")


        ).respond(
                response()
                        .withBody(json(ImmutableMap.of("_class", "org.jenkinsci.plugins.workflow.job.WorkflowRun",
                                "building", false,
                                "result", "SUCCESS")))
        );


        Expectation[] progressiveTextExp = mockServerClient.when(
                request()
                        .withMethod("GET")
                        .withPath("/job/test1/34/logText/progressiveText")


        ).respond(
                response()
                        .withBody("job output")
        );
        String[] allExp = Stream.of(metaExp, jobBuildExp, queueExp, jobResultExp, progressiveTextExp)
                .flatMap(Arrays::stream)
                .map(Expectation::getId)
                .toArray(String[]::new);
        return allExp;
    }

    @NonNull
    private String createJobUrl() {
        return "http://localhost:" + mockServerClient.getPort() + "/job/remote1";
    }

    private void initOpenTelemetry() {
        OpenTelemetrySdkProvider openTelemetrySdkProviders = jenkinsRule.getInstance().getExtensionList(OpenTelemetrySdkProvider.class).get(0);
        String mockOtelUrl = "http://localhost:" + mockServerClient.getPort() + "/otel/";
        OpenTelemetryConfiguration config = new OpenTelemetryConfiguration(
                Optional.of(mockOtelUrl),
                Optional.empty(),
                Optional.empty(),
                Optional.of(1000),
                Optional.of(1000),
                Optional.of("jenkins"),
                Optional.of("jenkins"),
                Optional.empty(),
                ImmutableMap.of("otel.exporter.otlp.protocol", "http/protobuf")
        );

        openTelemetrySdkProviders.initialize(config);
    }
}
