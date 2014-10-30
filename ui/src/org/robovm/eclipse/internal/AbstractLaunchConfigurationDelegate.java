/*
 * Copyright (C) 2012 Trillian Mobile AB
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/gpl-2.0.html>.
 */
package org.robovm.eclipse.internal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.io.FileUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.robovm.compiler.AppCompiler;
import org.robovm.compiler.config.Arch;
import org.robovm.compiler.config.Config;
import org.robovm.compiler.config.Config.Home;
import org.robovm.compiler.config.OS;
import org.robovm.compiler.target.LaunchParameters;
import org.robovm.compiler.util.io.Fifos;
import org.robovm.compiler.util.io.OpenOnReadFileInputStream;
import org.robovm.eclipse.RoboVMPlugin;

/**
 *
 */
public abstract class AbstractLaunchConfigurationDelegate extends AbstractJavaLaunchConfigurationDelegate {

    protected abstract Arch getArch(ILaunchConfiguration configuration, String mode) throws CoreException;

    protected abstract OS getOS(ILaunchConfiguration configuration, String mode) throws CoreException;

    protected abstract Config configure(Config.Builder configBuilder, ILaunchConfiguration configuration, String mode)
            throws IOException, CoreException;

    protected void customizeLaunchParameters(LaunchParameters launchParameters, ILaunchConfiguration configuration,
            String mode) throws IOException, CoreException {
        launchParameters.setStdoutFifo(Fifos.mkfifo("stdout"));
        launchParameters.setStderrFifo(Fifos.mkfifo("stderr"));
    }

    protected boolean isTestConfiguration() {
        return false;
    }

    @Override
    public void launch(ILaunchConfiguration configuration, String mode,
            ILaunch launch, IProgressMonitor monitor) throws CoreException {

        if (monitor == null) {
            monitor = new NullProgressMonitor();
        }

        monitor.beginTask(configuration.getName() + "...", 6);
        if (monitor.isCanceled()) {
            return;
        }

        try {
            monitor.subTask("Verifying launch attributes");

            String mainTypeName = getMainTypeName(configuration);
            File workingDir = getWorkingDirectory(configuration);
            String[] envp = getEnvironment(configuration);
            String pgmArgs = getProgramArguments(configuration);
            String vmArgs = getVMArguments(configuration);
            String[] classpath = getClasspath(configuration);
            String[] bootclasspath = getBootpath(configuration);
            IJavaProject javaProject = getJavaProject(configuration);

            if (monitor.isCanceled()) {
                return;
            }

            // Verification done
            monitor.worked(1);

            RoboVMPlugin.consoleInfo("Building executable");

            monitor.subTask("Creating source locator");
            setDefaultSourceLocator(launch, configuration);
            monitor.worked(1);

            monitor.subTask("Creating build configuration");
            Config.Builder configBuilder;
            try {
                configBuilder = new Config.Builder();
            } catch (IOException e) {
                throw new CoreException(new Status(IStatus.ERROR, RoboVMPlugin.PLUGIN_ID,
                        "Launch failed. Check the RoboVM console for more information.", e));
            }
            configBuilder.logger(RoboVMPlugin.getConsoleLogger());

            File projectRoot = getJavaProject(configuration).getProject().getLocation().toFile();
            RoboVMPlugin.loadConfig(configBuilder, projectRoot, isTestConfiguration());

            Arch arch = getArch(configuration, mode);
            OS os = getOS(configuration, mode);

            configBuilder.os(os);
            configBuilder.arch(arch);

            File tmpDir = RoboVMPlugin.getBuildDir(getJavaProjectName(configuration));
            tmpDir = new File(tmpDir, configuration.getName());
            tmpDir = new File(new File(tmpDir, os.toString()), arch.toString());
            if (mainTypeName != null) {
                tmpDir = new File(tmpDir, mainTypeName);
            }

            if (ILaunchManager.DEBUG_MODE.equals(mode)) {
                configBuilder.debug(true);
                String sourcepaths = RoboVMPlugin.getSourcePaths(javaProject);
                configBuilder.addPluginArgument("debug:sourcepath=" + sourcepaths);
            }

            if (bootclasspath != null) {
                configBuilder.skipRuntimeLib(true);
                for (String p : bootclasspath) {
                    configBuilder.addBootClasspathEntry(new File(p));
                }
            }
            for (String p : classpath) {
                configBuilder.addClasspathEntry(new File(p));
            }
            if (mainTypeName != null) {
                configBuilder.mainClass(mainTypeName);
            }
            configBuilder.tmpDir(tmpDir);
            configBuilder.skipInstall(true);

            Config config = null;
            AppCompiler compiler = null;
            try {
                RoboVMPlugin.consoleInfo("Cleaning output dir " + tmpDir.getAbsolutePath());
                FileUtils.deleteDirectory(tmpDir);
                tmpDir.mkdirs();

                Home home = RoboVMPlugin.getRoboVMHome();
                if (home.isDev()) {
                    configBuilder.useDebugLibs(true);
                    configBuilder.dumpIntermediates(true);
                }
                configBuilder.home(home);
                config = configure(configBuilder, configuration, mode);
                compiler = new AppCompiler(config);
                if (monitor.isCanceled()) {
                    return;
                }
                monitor.worked(1);

                monitor.subTask("Building executable");
                AppCompilerThread thread = new AppCompilerThread(compiler, monitor);
                thread.compile();
                if (monitor.isCanceled()) {
                    RoboVMPlugin.consoleInfo("Build canceled");
                    return;
                }
                monitor.worked(1);
                RoboVMPlugin.consoleInfo("Build done");
            } catch (InterruptedException e) {
                RoboVMPlugin.consoleInfo("Build canceled");
                return;
            } catch (IOException e) {
                RoboVMPlugin.consoleError("Build failed");
                throw new CoreException(new Status(IStatus.ERROR, RoboVMPlugin.PLUGIN_ID,
                        "Build failed. Check the RoboVM console for more information.", e));
            }

            try {
                RoboVMPlugin.consoleInfo("Launching executable");
                monitor.subTask("Launching executable");
                
                List<String> runArgs = new ArrayList<String>();
                runArgs.addAll(splitArgs(vmArgs));
                runArgs.addAll(splitArgs(pgmArgs));
                LaunchParameters launchParameters = config.getTarget().createLaunchParameters();
                launchParameters.setArguments(runArgs);
                launchParameters.setWorkingDirectory(workingDir);
                launchParameters.setEnvironment(envToMap(envp));
                customizeLaunchParameters(launchParameters, configuration, mode);
                String label = String.format("%s (%s)", mainTypeName,
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(new Date()));
                // launch plugin may proxy stdout/stderr fifo, which
                // it then writes to. Need to save the original fifos
                File stdOutFifo = launchParameters.getStdoutFifo();
                File stdErrFifo = launchParameters.getStderrFifo();
                PipedInputStream pipedIn = new PipedInputStream();
                PipedOutputStream pipedOut = new PipedOutputStream(pipedIn);
                Process process = compiler.launchAsync(launchParameters, pipedIn);
                if (stdOutFifo != null || stdErrFifo != null) {
                    InputStream stdoutStream = null;
                    InputStream stderrStream = null;
                    if (launchParameters.getStdoutFifo() != null) {
                        stdoutStream = new OpenOnReadFileInputStream(stdOutFifo);
                    }
                    if (launchParameters.getStderrFifo() != null) {
                        stderrStream = new OpenOnReadFileInputStream(stdErrFifo);
                    }
                    process = new ProcessProxy(process, pipedOut, stdoutStream, stderrStream, compiler);
                }

                DebugPlugin.newProcess(launch, process, label);
                RoboVMPlugin.consoleInfo("Launch done");

                if (monitor.isCanceled()) {
                    process.destroy();
                    return;
                }
                monitor.worked(1);
            } catch (Throwable t) {
                RoboVMPlugin.consoleError("Launch failed");
                throw new CoreException(new Status(IStatus.ERROR, RoboVMPlugin.PLUGIN_ID,
                        "Launch failed. Check the RoboVM console for more information.", t));
            }

        } finally {
            monitor.done();
        }
    }

    private Map<String, String> envToMap(String[] envp) throws IOException {
        if (envp == null) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<String, String>();
        for (int i = 0; i < envp.length; i++) {
            int index = envp[i].indexOf('=');
            if (index != -1) {
                result.put(envp[i].substring(0, index), envp[i].substring(index + 1));
            }
        }
        return result;
    }

    private String unquoteArg(String arg) {
        if (arg.startsWith("\"") && arg.endsWith("\"")) {
            return arg.substring(1, arg.length() - 1);
        }
        return arg;
    }

    private List<String> splitArgs(String args) {
        if (args == null || args.trim().length() == 0) {
            return Collections.emptyList();
        }
        String[] parts = CommandLine.parse("foo " + args).toStrings();
        if (parts.length <= 1) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>(parts.length - 1);
        for (int i = 1; i < parts.length; i++) {
            result.add(unquoteArg(parts[i]));
        }
        return result;
    }

    private static class ProcessProxy extends Process {
        private final Process target;
        private final OutputStream outputStream;
        private final InputStream inputStream;
        private final InputStream errorStream;
        private final AppCompiler appCompiler;
        private volatile boolean cleanedUp = false;

        ProcessProxy(Process target, OutputStream outputStream, InputStream inputStream, InputStream errorStream,
                AppCompiler appCompiler) {
            this.target = target;
            this.outputStream = outputStream;
            this.inputStream = inputStream;
            this.errorStream = errorStream;
            this.appCompiler = appCompiler;
        }

        public void destroy() {
            synchronized(this) {
                if(!cleanedUp) {
                    appCompiler.launchAsyncCleanup();
                    cleanedUp = true;
                }
            }            
            target.destroy();
        }

        public boolean equals(Object obj) {
            return target.equals(obj);
        }

        public int exitValue() {
            return target.exitValue();
        }

        public InputStream getErrorStream() {
            if (errorStream != null) {
                return errorStream;
            }
            return target.getErrorStream();
        }

        public InputStream getInputStream() {
            if (inputStream != null) {
                return inputStream;
            }
            return target.getInputStream();
        }

        public OutputStream getOutputStream() {
            if (outputStream != null) {
                return outputStream;
            }
            return target.getOutputStream();
        }

        public int hashCode() {
            return target.hashCode();
        }

        public String toString() {
            return target.toString();
        }

        public int waitFor() throws InterruptedException {
            try {
                return target.waitFor();
            } catch (Throwable t) {
                synchronized(this) {
                    if(!cleanedUp) {
                        appCompiler.launchAsyncCleanup();
                        cleanedUp = true;
                    }
                } 
                throw new RuntimeException(t);
            }
        }
    }
}
