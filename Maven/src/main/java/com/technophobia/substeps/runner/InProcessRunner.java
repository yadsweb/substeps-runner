package com.technophobia.substeps.runner;

import java.util.List;

import org.apache.maven.plugin.logging.Log;

import com.technophobia.substeps.execution.ExecutionNode;

public class InProcessRunner implements MojoRunner {

    SubstepsRunner executionNodeRunner = ExecutionNodeRunnerFactory.createRunner();
    private Log log;

    InProcessRunner(Log log) {

        this.log = log;
    }

    public List<SubstepExecutionFailure> run() {
        log.info("Running substeps tests in process");
        return executionNodeRunner.run();
    }

    public void prepareExecutionConfig(SubstepsExecutionConfig theConfig) {

        executionNodeRunner.prepareExecutionConfig(theConfig);
    }

    public ExecutionNode getRootNode() {
        return executionNodeRunner.getRootNode();
    }

    public void addNotifier(INotifier notifier) {
        executionNodeRunner.addNotifier(notifier);
    }

    public void shutdown() {
        // nop
    }

}