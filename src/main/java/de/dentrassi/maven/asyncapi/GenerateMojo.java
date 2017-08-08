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
import de.dentrassi.asyncapi.generator.java.Generator;
import de.dentrassi.asyncapi.parser.YamlParser;

/**
 * Generate sources from AsyncAPI definition
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresProject = true, threadSafe = false)
public class GenerateMojo extends AbstractMojo {

    @Parameter(property = "asyncapi.definition", required = true, defaultValue = "${project.basedir}/src/main/asyncapi.yaml")
    private File definitionFile;

    @Parameter(property = "asyncapi.ignoreMissingDefinition", required = false, defaultValue = "false")
    private boolean ignoreMissingDefinition;

    @Parameter(property = "asyncapi.skip", required = false, defaultValue = "false")
    private boolean skip;

    @Parameter(property = "asyncapi.generator.charset", required = true, defaultValue = "${project.build.sourceEncoding}")
    private String characterSet;

    @Parameter(property = "asyncapi.generator.packageBase", required = false)
    private String packageBase;

    @Parameter(required = true, defaultValue = "${project.build.directory}/generated-sources/asyncapi")
    private File targetPath;

    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (this.skip) {
            return;
        }

        if (!this.definitionFile.exists() && this.ignoreMissingDefinition) {
            getLog().debug(String.format("Skipping. Definition file '%s' is missing but we ignore this", this.definitionFile));
            return;
        }

        // load

        getLog().info(String.format("Reading definition: %s", this.definitionFile));

        final AsyncApi api;
        try (final InputStream in = Files.newInputStream(this.definitionFile.toPath())) {
            api = new YamlParser(in).parse();
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

        final Generator generator = new Generator(api);
        generator.characterSet(Charset.forName(this.characterSet));
        generator.basePackage(this.packageBase);
        generator.target(this.targetPath.toPath());
        try {
            generator.generate();
        } catch (final Exception e) {
            throw new MojoFailureException("Failed to generate API", e);
        }

        // add sources

        this.project.addCompileSourceRoot(this.targetPath.getAbsolutePath());
        this.buildContext.refresh(this.targetPath.getAbsoluteFile());
    }

}
