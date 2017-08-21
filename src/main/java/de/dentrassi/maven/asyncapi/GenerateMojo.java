/*
 * Copyright (C) 2017 Jens Reimann <jreimann@redhat.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.dentrassi.maven.asyncapi;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.build.incremental.BuildContext;

import de.dentrassi.asyncapi.AsyncApi;
import de.dentrassi.asyncapi.ValidationException;
import de.dentrassi.asyncapi.Validator.Marker;
import de.dentrassi.asyncapi.generator.java.Generator;
import de.dentrassi.asyncapi.generator.java.gson.GsonGeneratorExtension;
import de.dentrassi.asyncapi.generator.java.jms.JmsGeneratorExtension;
import de.dentrassi.asyncapi.internal.parser.YamlParser;

/**
 * Generate sources from AsyncAPI definition
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresProject = true, threadSafe = false)
public class GenerateMojo extends AbstractMojo {

    @Parameter(property = "asyncapi.definition", required = true, defaultValue = "${project.basedir}/src/main/asyncapi.yaml")
    private File definitionFile;

    @Parameter(property = "asyncapi.ignoreMissingDefinition", required = false, defaultValue = "true")
    private boolean ignoreMissingDefinition = true;

    /**
     * Allows to skip the whole generation step
     */
    @Parameter(property = "asyncapi.skip", required = false, defaultValue = "false")
    private boolean skip;

    /**
     * The character set to use for generated resources
     */
    @Parameter(property = "asyncapi.generator.charset", required = true, defaultValue = "${project.build.sourceEncoding}")
    private String characterSet;

    /**
     * The Java base package for generated
     * <p>
     * Will use the {@code baseTopic} prefix from the API model is unspecified
     * </p>
     */
    @Parameter(property = "asyncapi.generator.packageBase", required = false)
    private String packageBase;

    /**
     * The path to generate the sources to
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}/generated-sources/asyncapi")
    private File targetPath;

    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    @Parameter
    private Set<String> extensions;

    @Component
    private BuildContext buildContext;

    public void setDefinitionFile(final File definitionFile) {
        this.definitionFile = definitionFile;
    }

    public void setIgnoreMissingDefinition(final boolean ignoreMissingDefinition) {
        this.ignoreMissingDefinition = ignoreMissingDefinition;
    }

    public void setSkip(final boolean skip) {
        this.skip = skip;
    }

    public void setCharacterSet(final String characterSet) {
        this.characterSet = characterSet;
    }

    public void setPackageBase(final String packageBase) {
        this.packageBase = packageBase;
    }

    public void setProject(final MavenProject project) {
        this.project = project;
    }

    public void setBuildContext(final BuildContext buildContext) {
        this.buildContext = buildContext;
    }

    public void setExtensions(final Set<String> extensions) {
        this.extensions = extensions;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (this.skip) {
            return;
        }

        if (!this.definitionFile.exists() && this.ignoreMissingDefinition) {
            getLog().debug(String.format("Skipping generation. Definition file '%s' is missing and we ignore this", this.definitionFile));
            return;
        }

        // load

        getLog().info(String.format("Reading definition: %s", this.definitionFile));

        final AsyncApi api;

        try (final InputStream in = Files.newInputStream(this.definitionFile.toPath())) {

            api = new YamlParser(in).parse();

        } catch (final ValidationException e) {

            getLog().error("Model validation failed: ");
            for (final Marker marker : e.getMarkers()) {
                getLog().error(marker.toString());
            }
            throw new MojoFailureException("Invalid API model", e);

        } catch (final Exception e) {

            throw new MojoFailureException("Failed to read definition", e);

        }

        // extract info

        String title = api.getInformation().getTitle();
        if (title == null) {
            title = "unknown";
        }
        String version = api.getInformation().getVersion();
        if (version == null) {
            version = "unknown";
        }

        // generate

        getLog().info(String.format("Generating API: %s:%s", title, version));
        getLog().debug(String.format("    Output: %s", this.targetPath));

        final Generator.Builder generator = Generator.newBuilder();
        generator.characterSet(Charset.forName(this.characterSet));
        generator.basePackage(this.packageBase);
        generator.targetPath(this.targetPath.toPath());

        addNamedExtensions(generator);

        try {
            generator.build(api).generate();
        } catch (final Exception e) {
            throw new MojoFailureException("Failed to generate API", e);
        }

        // add sources

        this.project.addCompileSourceRoot(this.targetPath.getAbsolutePath());
        this.buildContext.refresh(this.targetPath.getAbsoluteFile());
    }

    private void addNamedExtensions(final Generator.Builder generator) throws MojoExecutionException {

        if (this.extensions == null) {
            this.extensions = new HashSet<>(Arrays.asList("jms", "gson"));
            getLog().info("Using default extensions: " + this.extensions);
        }

        for (final String extension : this.extensions) {
            switch (extension.toLowerCase()) {
            case "jms":
                generator.addExtension(new JmsGeneratorExtension());
                break;
            case "gson":
                generator.addExtension(new GsonGeneratorExtension());
                break;
            default:
                throw new MojoExecutionException(String.format("Unknown generator extension '%s'", extension));
            }
        }
    }

}
