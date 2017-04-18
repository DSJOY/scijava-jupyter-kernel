/*
 * Copyright 2017 SciJava.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.scijava.jupyter.service;

import static com.twosigma.beaker.jupyter.Utils.uuid;
import com.twosigma.jupyter.KernelRunner;
import com.twosigma.jupyter.KernelSocketsFactoryImpl;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.jupyter.kernel.configuration.ScijavaKernelConfigurationFile;
import org.scijava.jupyter.commands.InstallScijavaKernel;
import org.scijava.jupyter.kernel.evaluator.ScijavaEvaluator;
import org.scijava.jupyter.kernel.ScijavaKernel;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;

/**
 *
 * @author Hadrien Mary
 */
@Plugin(type = Service.class)
public class DefaultJupyterService extends AbstractService implements JupyterService {

    @Parameter
    private transient LogService log;

    @Parameter
    private transient Context context;

    @Parameter
    private transient CommandService command;

    /* Install kernel */
    @Override
    public void installKernel(String scriptLanguage, String logLevel, String pythonBinaryPath) {
        installKernel(scriptLanguage, logLevel, Paths.get(pythonBinaryPath));
    }

    @Override
    public void installKernel(String scriptLanguage, String logLevel, Path pythonBinaryPath) {
        installKernel(scriptLanguage, logLevel, pythonBinaryPath.toFile());
    }
    
    @Override
    public void installKernel(String scriptLanguage, String logLevel, File pythonBinaryPath) {
        installKernel(scriptLanguage, logLevel, pythonBinaryPath, false);
    }
    
    @Override
    public void installKernel(String scriptLanguage, String logLevel, String pythonBinaryPath, boolean installAllKernels) {
        installKernel(scriptLanguage, logLevel, Paths.get(pythonBinaryPath), installAllKernels);
    }

    @Override
    public void installKernel(String scriptLanguage, String logLevel, Path pythonBinaryPath, boolean installAllKernels) {
        installKernel(scriptLanguage, logLevel, pythonBinaryPath.toFile(), installAllKernels);
    }

    @Override
    public void installKernel(String scriptLanguage, String logLevel, File pythonBinaryPath, boolean installAllKernels) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("scriptLanguage", scriptLanguage);
        parameters.put("logLevel", logLevel);
        parameters.put("pythonBinaryPath", pythonBinaryPath);
         parameters.put("installAllKernels", installAllKernels);
        command.run(InstallScijavaKernel.class, true, parameters);
    }

    /* Run kernel */
    @Override
    public void runKernel(String... args) {
        Map<String, String> parameters = parseArguments(args);
        // TODO : Ensure parameters contains the appropriate keys.

        runKernel(parameters.get("scriptLanguage"),
                parameters.get("logLevel"),
                parameters.get("connectionFile"));
    }

    @Override
    public void runKernel(String scriptLanguage, String logLevel, String connectionFile) {
        runKernel(scriptLanguage, logLevel, Paths.get(connectionFile));
    }

    @Override
    public void runKernel(String scriptLanguage, String logLevel, File connectionFile) {
        runKernel(scriptLanguage, logLevel, connectionFile.toPath());
    }

    @Override
    public void runKernel(String scriptLanguage, String logLevel, Path connectionFile) {

        KernelRunner.run(() -> {
            String id = uuid();

            // Setup configuration
            ScijavaKernelConfigurationFile config = new ScijavaKernelConfigurationFile(this.context,
                    scriptLanguage,
                    logLevel,
                    connectionFile);

            // Setup the socket
            KernelSocketsFactoryImpl kernelSocketsFactory = new KernelSocketsFactoryImpl(config);

            // Setup the evaluator
            ScijavaEvaluator evaluator = new ScijavaEvaluator(context, id, id, config.getLanguageName());

            // Ugly. Curtis is not going to like it :-(
            ScijavaKernel.scriptLanguage = evaluator.getScriptLanguage();

            // Launch the kernel
            return new ScijavaKernel(context, id, evaluator, config, kernelSocketsFactory);
        });
    }

    /* Helpers private method */
    private Map<String, String> parseArguments(final String... args) {
        if (args.length > 0) {
            try {

                Options options = new Options();
                options.addOption("connectionFile", true, "Connection File Path");
                options.addOption("language", true, "Language Name");
                options.addOption("verbose", true, "Verbose Mode");

                CommandLineParser parser = new DefaultParser();
                CommandLine cmd = parser.parse(options, args);

                Map<String, String> parameters = new HashMap<>();

                parameters.put("connectionFile", cmd.getOptionValue("connectionFile"));
                parameters.put("scriptLanguage", cmd.getOptionValue("language"));
                parameters.put("logLevel", cmd.getOptionValue("verbose"));

                return parameters;

            } catch (ParseException ex) {
                log.error("Error parsing arguments : " + ex.toString());
            }
        } else {
            log.error("No parameters passed to the Scijava kernel.");
        }
        return null;
    }
}