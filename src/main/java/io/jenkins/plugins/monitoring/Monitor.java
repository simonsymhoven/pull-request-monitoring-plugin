package io.jenkins.plugins.monitoring;

import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.IllegalClassException;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This {@link Step} is responsible for the configuration of the monitoring dashboard
 * via the corresponding Jenkinsfile.
 *
 * @author Simon Symhoven
 */
public class Monitor extends Step implements Serializable {
    private static final long serialVersionUID = -1329798203887148860L;
    private String configuration;

    /**
     * Creates a new instance of {@link Monitor}.
     */
    @DataBoundConstructor
    public Monitor() {
        super();
        this.configuration = "{\"plugins\":{}}";
    }

    /**
     * Sets the configuration for the dashboard.
     *
     * @param configuration
     *         the configuration as json
     */
    @DataBoundSetter
    public void setConfiguration(final String configuration) {
        this.configuration = configuration;
    }

    public String getConfiguration() {
        return configuration;
    }

    public List<? extends MonitorView> getAvailablePlugins() {
        return ExtensionList.lookup(MonitorView.class);
    }

    @Override
    public StepExecution start(final StepContext stepContext) throws Exception {
        return new Execution(stepContext, this);
    }

    /**
     *  The {@link Execution} routine for the monitoring step.
     */
    static class Execution extends SynchronousStepExecution<Void> {

        private static final long serialVersionUID = 1300005476208035751L;
        private final Monitor monitor;

        Execution(StepContext stepContext, Monitor monitor) {
            super(stepContext);
            this.monitor = monitor;
        }

        @Override
        public Void run() throws Exception {
            JSONObject configuration = new JSONObject(monitor.getConfiguration());
            getContext().get(TaskListener.class).getLogger()
                    .println("Configuration: " + configuration.toString(3));

            List<String> classes = monitor.getAvailablePlugins()
                    .stream()
                    .map(plugin -> plugin.getClazz().getName())
                    .collect(Collectors.toList());
            getContext().get(TaskListener.class).getLogger()
                    .println("Classes that implement 'MonitorView' interface: "
                            + StringUtils.join(classes, ","));

            for (String key : ((JSONObject) configuration.get("plugins")).keySet()) {
                if (!classes.contains(key)) {
                    getContext().get(TaskListener.class).getLogger()
                            .println("Can't find class '" + key + "' in list of available plugins!");

                    throw new IllegalClassException(String.format("Can't find class '%s' in list of available plugins!", key));
                }
            }

            final Run<?, ?> run = getContext().get(Run.class);
            if (run.getParent().getPronoun().equals("Pull Request")) {
                getContext().get(TaskListener.class).getLogger()
                        .println("Build is part of a pull request. Add monitor now.");
                run.addAction(new MonitoringBuildAction(run, monitor));
            }
            else {
                getContext().get(TaskListener.class).getLogger()
                        .println("Build is not part of a pull request. Skip adding monitor.");
            }

            return null;
        }

    }

    /**
     * A {@link Descriptor} for the monitoring step.
     */
    @Extension
    public static class Descriptor extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, Run.class);
        }

        @Override
        public String getFunctionName() {
            return "monitoring";
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return "Configure Monitoring Dashboard";
        }
    }
}
