package com.d2dx.j2objc;

/*
 * (c) 2014 Vladimir Katardjiev. Licensed under 2-clause BSD.
 */

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;

import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.twdata.maven.mojoexecutor.MojoExecutor.Element;

@Mojo(name = "translate", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE, requiresDirectInvocation = false)
public class TranslateMojo extends AbstractMojo
{
    /**
     * The project currently being build.
     * 
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    @Parameter(required = true, readonly = true, defaultValue = "${project}")
    private MavenProject       mavenProject;
    
    /**
     * The current Maven session.
     * 
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    @Parameter(required = true, readonly = true, defaultValue = "${session}")
    @Component
    private MavenSession       mavenSession;
    
    /**
     * The Maven BuildPluginManager component.
     * 
     * @component
     * @required
     */
    @Parameter(required = true)
    @Component
    private BuildPluginManager pluginManager;
    
    /**
     * @parameter default-value="false"
     */
    @Parameter(defaultValue = "false")
    private boolean            includeDependencySources;
    
    /**
     * @parameter default-value="true"
     */
    @Parameter(defaultValue = "true")
    private boolean            includeClasspath;
    
    /**
     * The version of the dependency com.d2dx.j2objc.j2objc-package
     * 
     * @parameter default-value="0.9.1"
     */
    @Parameter(defaultValue = "0.9.1")
    private String             j2objcVersion;
    
    /**
     * @parameter default-value="${project.build.dir}/j2objc"
     */
    @Parameter(defaultValue = "${project.build.directory}/j2objc")
    private File               outputDirectory;
    
    /**
     * @parameter
     */
    @Parameter
    private Prefix[]           prefixes;
    
    /**
     * Specifies which source dependencies to use, if includeDependencySources is set to false.
     * 
     * @parameter
     */
    @Parameter
    Dependency[]               sourceDependencies;
    
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        
        if (this.includeDependencySources)
        {
            
            //@formatter:off
            executeMojo(
                    plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version("2.8")
                    ),
                    goal("unpack-dependencies"),
                    configuration(
                        element("classifier", "sources"),
                        element("failOnMissingClassifierArtifact", "false"),
                        element(name("outputDirectory"), "${project.build.directory}/j2objc-sources")
                    ),
                    executionEnvironment(
                        this.mavenProject,
                        this.mavenSession,
                        this.pluginManager
                    )
                );
        }

        /*
         * Extracts j2objc automagically 
         */
        //@formatter:off
        executeMojo(
                plugin(groupId("org.apache.maven.plugins"), 
                artifactId("maven-dependency-plugin"), 
                version("2.8")),
                goal("unpack"),
                configuration(
                    element(
                        "artifactItems",
                        element("artifactItem", 
                            element("groupId", "com.d2dx.j2objc"), 
                            element("artifactId", "j2objc-package"),
                            element("version", this.j2objcVersion)
                        )
                    ),
                    element(name("outputDirectory"), "${project.build.directory}/j2objc-bin")
                ), 
                executionEnvironment(this.mavenProject, this.mavenSession, this.pluginManager));
        //@formatter:on
        
        /*
         * Time to do detection and run j2objc with proper parameters.
         */
        
        this.outputDirectory.mkdirs();
        
        // Gather the source paths
        // Right now, we limit to sources in two directories: project srcdir, and additional sources.
        // Later, we should add more.
        HashSet<String> srcPaths = new HashSet<String>();
        
        String srcDir = this.mavenSession.getCurrentProject().getBuild().getSourceDirectory();
        srcPaths.add(srcDir);
        
        String buildDir = this.mavenSession.getCurrentProject().getBuild().getDirectory();
        String addtlSrcDir = buildDir + "/j2objc-sources";
        srcPaths.add(addtlSrcDir);
        
        // Gather sources.
        HashSet<File> srcFiles = new HashSet<File>();
        
        for (String path : srcPaths)
        {
            File sdFile = new File(path);
            Collection<File> scanFiles = FileUtils.listFiles(sdFile, new String[] { "java" }, true);
            srcFiles.addAll(scanFiles);
        }
        
        // Gather prefixes into a file
        FileOutputStream fos;
        OutputStreamWriter out;
        
        if (this.prefixes != null)
        {
            try
            {
                fos = new FileOutputStream(new File(this.outputDirectory, "prefixes.properties"));
                out = new OutputStreamWriter(fos);
                
                for (Prefix p : this.prefixes)
                {
                    out.write(p.javaPrefix);
                    out.write(": ");
                    out.write(p.objcPrefix);
                    out.write("\n");
                }
                
                out.flush();
                out.close();
                fos.close();
            }
            catch (FileNotFoundException e)
            {
                throw new MojoExecutionException("Could not create prefixes file");
            }
            catch (IOException e1)
            {
                throw new MojoExecutionException("Could not create prefixes file");
            }
            
        }
        // We now have:
        // Sources, source directories, and prefixes.
        // Call the maven-exec-plugin with the new environment!
        
        LinkedList<Element> args = new LinkedList<Element>();
        
        if (this.includeClasspath)
        {
            args.add(new Element("argument", "-cp"));
            args.add(new Element("classpath", ""));
        }
        
        String srcDirsArgument = "";
        
        for (String p : srcPaths)
            srcDirsArgument += ":" + p;
        
        // Crop the first colon
        if (srcDirsArgument.length() > 0)
            srcDirsArgument = srcDirsArgument.substring(1);
        
        args.add(new Element("argument", "-sourcepath"));
        args.add(new Element("argument", srcDirsArgument));
        
        if (this.prefixes != null)
        {
            args.add(new Element("argument", "--prefixes"));
            args.add(new Element("argument", this.outputDirectory.getAbsolutePath() + "/prefixes.properties"));
        }
        
        args.add(new Element("argument", "-d"));
        args.add(new Element("argument", this.outputDirectory.getAbsolutePath()));

        for (File f : srcFiles)
            args.add(new Element("argument", f.getAbsolutePath()));
        
        try
        {
            Runtime.getRuntime().exec("chmod u+x " + this.mavenProject.getBuild().getDirectory() + "/j2objc-bin/j2objc");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        //@formatter:off
        executeMojo(
                plugin(groupId("org.codehaus.mojo"), 
                artifactId("exec-maven-plugin"), 
                version("1.3")),
                goal("exec"),
                configuration(
                    element("arguments", args.toArray(new Element[0])),
                    element("executable", this.mavenProject.getBuild().getDirectory() + "/j2objc-bin/j2objc"),
                    element("workingDirectory", this.mavenProject.getBuild().getDirectory() + "/j2objc-bin/")
                ), 
                executionEnvironment(this.mavenProject, this.mavenSession, this.pluginManager));
        //@formatter:on
        
    }
    
}
