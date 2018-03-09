package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import static org.apache.commons.lang.StringUtils.trimToNull;

import java.io.PrintStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNullableByDefault;

import org.jenkinsci.plugins.ParameterizedRemoteTrigger.pipeline.Handle;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * This object wraps a {@link Run}, {@link FilePath}, {@link TaskListener} and {@link PrintStream} -
 * the typical objects passed from one method to the other in a Jenkins Builder/BuildStep implementation.<br>
 * <br>
 * The reason for wrapping is simplicity on the one hand. On the other in an asynchronous pipeline usage
 * via the {@link Handle} we might not have a {@link Run}, {@link FilePath}, {@link TaskListener}, but we still
 * want to provide a {@link PrintStream} for logging. Therefore the first three objects can be null, the {@link PrintStream}
 * must not be null.
 */
@ParametersAreNullableByDefault
public class BuildContext
{
    @Nullable
    public final Run<?, ?> run;

    @Nullable
    public final FilePath workspace;

    @Nullable
    public final TaskListener listener;

    @Nonnull
    public final PrintStream logger;

    @Nullable
    public RemoteJenkinsServer effectiveRemoteServer;

    
    /**
     * The current Item (job, pipeline,...) where the plugin is used from.
     *
     */
    @Nonnull
    public final String currentItem;

    public BuildContext(@Nullable Run<?, ?> run, @Nullable FilePath workspace, @Nullable TaskListener listener, @Nonnull PrintStream logger, @Nullable String currentItem) {
        this.run = run;
        this.workspace = workspace;
        this.listener = listener;
        this.logger = logger;
        this.currentItem = getCurrentItem(run, currentItem);
    }

    public BuildContext(@Nullable Run<?, ?> run, @Nullable FilePath workspace, @Nullable TaskListener listener, @Nonnull PrintStream logger) {
        this(run, workspace, listener, logger, null);
    }

    public BuildContext(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull TaskListener listener)
    {
        this(run, workspace, listener, listener.getLogger());
    }

    public BuildContext(@Nonnull PrintStream logger, String currentItem)
    {
        this(null, null, null, logger, currentItem);
    }

    private String getCurrentItem(Run<?, ?> run, String currentItem)
    {
        String runItem = null;
        String curItem = trimToNull(currentItem);
        if(run != null && run.getParent() != null) {
            runItem = trimToNull(run.getParent().getFullName());
        }
        if(runItem != null && curItem != null) {
            if(runItem.equals(curItem)) {
                return runItem;
            } else {
                throw new IllegalArgumentException(String.format("Current Item ('%s') and Parent Item from Run ('%s') differ!", curItem, runItem));
            }
        } else if(runItem != null) {
            return runItem;
        } else if(curItem != null) {
            return curItem;
        } else {
            throw new IllegalArgumentException("Both null, Run and Current Item!");
        }
    }
    
}
