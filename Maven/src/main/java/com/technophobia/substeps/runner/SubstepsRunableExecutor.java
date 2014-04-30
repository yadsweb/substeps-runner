package com.technophobia.substeps.runner;

import com.technophobia.substeps.execution.node.RootNode;
import com.technophobia.substeps.report.ExecutionReportBuilder;
import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;

public class SubstepsRunableExecutor implements Runnable {

    private List<ExecutionConfig> executionConfigs;

    private ExecutionReportBuilder executionReportBuilder = null;

    private Integer jmxPort;

    private final String vmArgs = null;

    private boolean runTestsInForkedVM;

    private List<String> stepImplementationArtifacts;

    private MavenProject project;

    private final BuildFailureManager buildFailureManager = new BuildFailureManager();

    private ArtifactResolver artifactResolver;

    private ArtifactFactory artifactFactory;

    private MavenProjectBuilder mavenProjectBuilder;

    private org.apache.maven.artifact.repository.ArtifactRepository localRepository;

    private List remoteRepositories;

    private List<Artifact> pluginDependencies;

    private ArtifactMetadataSource artifactMetadataSource;

    private MojoRunner runner;

    private Log log;

    private String dir;

    private String tag;

    public void setExecutionConfigs(List<ExecutionConfig> executionConfigs) {
        this.executionConfigs = executionConfigs;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public void setJmxPort(Integer jmxPort) {
        this.jmxPort = jmxPort;
    }

    public void setRunTestsInForkedVM(boolean runTestsInForkedVM) {
        this.runTestsInForkedVM = runTestsInForkedVM;
    }

    public void setStepImplementationArtifacts(List<String> stepImplementationArtifacts) {
        this.stepImplementationArtifacts = stepImplementationArtifacts;
    }

    public void setProject(MavenProject project) {
        this.project = project;
    }

    public void setArtifactResolver(ArtifactResolver artifactResolver) {
        this.artifactResolver = artifactResolver;
    }

    public void setArtifactFactory(ArtifactFactory artifactFactory) {
        this.artifactFactory = artifactFactory;
    }

    public void setMavenProjectBuilder(MavenProjectBuilder mavenProjectBuilder) {
        this.mavenProjectBuilder = mavenProjectBuilder;
    }

    public void setLocalRepository(ArtifactRepository localRepository) {
        this.localRepository = localRepository;
    }

    public void setRemoteRepositories(List remoteRepositories) {
        this.remoteRepositories = remoteRepositories;
    }

    public void setPluginDependencies(List<Artifact> pluginDependencies) {
        this.pluginDependencies = pluginDependencies;
    }

    public void setArtifactMetadataSource(ArtifactMetadataSource artifactMetadataSource) {
        this.artifactMetadataSource = artifactMetadataSource;
    }

    public void setRunner(MojoRunner runner) {
        this.runner = runner;
    }

    public void setExecutionReportBuilder(ExecutionReportBuilder executionReportBuilder) {
        this.executionReportBuilder = executionReportBuilder;
    }

    Log getLog() {
        return log;
    }

    void setLog(Log l) {
        this.log = l;
    }

    public void execute() throws MojoExecutionException, MojoFailureException {

        assertCompatibleCoreVersion();

        ensureValidConfiguration();

        this.runner = this.runTestsInForkedVM ? createForkedRunner() : createInProcessRunner();

        executeConfigs();

        processBuildData();

        this.runner.shutdown();

    }

    @Override
    public void run() {

        try {
            Logger.getLogger(SubstepsRunableExecutor.class.getName()).log(Level.INFO, "Starting execution of tests with tag: {0}", tag);
            execute();
        } catch (MojoExecutionException ex) {
            Logger.getLogger(SubstepsRunableExecutor.class.getName()).log(Level.SEVERE, "MojoExecutionException when executing tests with tag: " + tag +"{0}", ex);
        } catch (MojoFailureException ex) {
            Logger.getLogger(SubstepsRunableExecutor.class.getName()).log(Level.SEVERE, "MojoFailureException when executing tests with tag: " + tag +"{0}", ex);
        }
    }

    private void assertCompatibleCoreVersion() throws MojoExecutionException {

        CoreVersionChecker.assertCompatibleVersion(getLog(), this.artifactFactory, this.artifactResolver,
                this.remoteRepositories, this.localRepository, this.mavenProjectBuilder, this.project,
                this.pluginDependencies);
    }

    private ForkedRunner createForkedRunner() throws MojoExecutionException {

        try {

            return new ForkedRunner(getLog(), this.jmxPort, this.vmArgs, this.project.getTestClasspathElements(),
                    this.stepImplementationArtifacts, this.artifactResolver, this.artifactFactory,
                    this.mavenProjectBuilder, this.localRepository, this.remoteRepositories,
                    this.artifactMetadataSource);
        } catch (final DependencyResolutionRequiredException e) {

            throw new MojoExecutionException("Unable to resolve dependencies", e);
        }
    }

    private InProcessRunner createInProcessRunner() {

        return new InProcessRunner(getLog());
    }

    private void executeConfigs() throws MojoExecutionException {

        if (this.executionConfigs == null || this.executionConfigs.isEmpty()) {

            throw new MojoExecutionException("executionConfigs cannot be null or empty");
        }

        try {
            for (final ExecutionConfig executionConfig : this.executionConfigs) {

                runExecutionConfig(executionConfig);
            }
        } catch (final MojoExecutionException t) {

            // to cater for any odd exceptions thrown out.. at least this way
            // jvm shouldn't just die, unless it was going to die anyway
            throw new MojoExecutionException("Unhandled exception: " + t.getMessage(), t);
        }
    }

    private void runExecutionConfig(final ExecutionConfig theConfig) throws MojoExecutionException {

        theConfig.setTags(tag);

        this.runner.prepareExecutionConfig(theConfig.asSubstepsExecutionConfig());
        
        Logger.getLogger(SubstepsRunableExecutor.class.getName()).log(Level.INFO, "Currently running threads tag is {0}", theConfig.asSubstepsExecutionConfig().getTags());
        
        final RootNode rootNode = this.runner.run();

        if (theConfig.getDescription() != null) {

            rootNode.setLine(theConfig.getDescription());

        }

        addToReport(rootNode);

        this.buildFailureManager.addExecutionResult(rootNode);
    }

    private void addToReport(final RootNode rootNode) {

        if (this.executionReportBuilder != null) {
            this.executionReportBuilder.addRootExecutionNode(rootNode);
        } else {
        }
    }

    /**
     * @param data
     * @throws MojoFailureException
     */
    private void processBuildData() throws MojoFailureException {

        if (this.executionReportBuilder != null) {
            this.executionReportBuilder.setOutputDirectory(new File(dir.trim()));

            this.executionReportBuilder.buildReport();
        } else {
            getLog().info("ExecutionReportBuilder is null!");
        }

        if (this.buildFailureManager.testSuiteFailed()) {

            throw new MojoFailureException("Substep Execution failed:\n"
                    + this.buildFailureManager.getBuildFailureInfo());

        } else if (!this.buildFailureManager.testSuiteCompletelyPassed()) {
            // print out the failure string (but won't include any failures)
            getLog().info(this.buildFailureManager.getBuildFailureInfo());
        }
    }

    private void ensureValidConfiguration() throws MojoExecutionException {

        ensureForkedIfStepImplementationArtifactsSpecified();
    }

    private void ensureForkedIfStepImplementationArtifactsSpecified() throws MojoExecutionException {

        if (this.stepImplementationArtifacts != null && !this.stepImplementationArtifacts.isEmpty()
                && !this.runTestsInForkedVM) {
            throw new MojoExecutionException(
                    "Invalid configuration of substeps runner, if stepImplementationArtifacts are specified runTestsInForkedVM must be true");
        }

    }

}
