package org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils;

import hudson.Plugin;
import hudson.PluginWrapper;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Optional;

public class OtelUtils {

	private static String TRACE_PARENT_VERSION = "00";
	private static String TRACE_PARENT_TRACE_FLAG = "01";

	public static String getTraceParent() {
		return Optional.ofNullable(Span.fromContextOrNull(Context.current()))
				.map(OtelUtils::genTraceParent)
				.orElse(null);

	}


	public static AutoCloseable activeSpanIfAvailable(StepContext stepContext) {
		try {
			FlowNode flowNode = stepContext.get(FlowNode.class);
			Run run = stepContext.get(Run.class);
			return Optional.ofNullable(Jenkins.get().getExtensionList(OtelTraceService.class))
					.filter(list -> list.size()>0)
					.map(list -> list.get(0))
					.map(otelTraceServices -> otelTraceServices.getSpan(run, flowNode))
					.map(Span::makeCurrent)
					.map(AutoCloseable.class::cast)
					.orElseGet(OtelUtils::noop);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static AutoCloseable activeSpanIfAvailable(Run run) {
		return Optional.ofNullable(Jenkins.get().getExtensionList(OtelTraceService.class))
				.filter(list -> list.size()>0)
				.map(list -> list.get(0))
				.map(otelTraceServices -> otelTraceServices.getSpan(run))
				.map(Span::makeCurrent)
				.map(AutoCloseable.class::cast)
				.orElseGet(OtelUtils::noop);
	}

	@NonNull
	public static AutoCloseable noop() {
		return () -> {
		};
	}

	@NonNull
	public static boolean isOpenTelemetryAvailable() {
		return Optional.ofNullable(Jenkins.get().getPlugin("opentelemetry"))
				.map(Plugin::getWrapper)
				.map(PluginWrapper::isActive)
				.orElse(false);
	}

	@NonNull
	private static String genTraceParent(Span span) {
		return TRACE_PARENT_VERSION + "-" + span.getSpanContext().getTraceId() + "-" + span.getSpanContext().getSpanId() + "-" + TRACE_PARENT_TRACE_FLAG;
	}


}
