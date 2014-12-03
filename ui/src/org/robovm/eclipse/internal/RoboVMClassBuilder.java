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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.robovm.compiler.AppCompiler;
import org.robovm.compiler.ClassCompilerListener;
import org.robovm.compiler.Version;
import org.robovm.compiler.clazz.Clazz;
import org.robovm.compiler.config.Config;
import org.robovm.compiler.config.Config.Home;
import org.robovm.compiler.config.Config.TargetType;
import org.robovm.eclipse.RoboVMPlugin;

/**
 *
 * @version $Id$
 */
public class RoboVMClassBuilder extends IncrementalProjectBuilder {

    public static final String ID = "org.robovm.eclipse.RoboVMClassBuilder";

    @Override
    protected IProject[] build(int kind, Map<String, String> args,
            IProgressMonitor monitor) throws CoreException {

        List<IPath> changedClassFiles = new ArrayList<IPath>();
        findChangedClassFiles(getProject(), kind == FULL_BUILD, changedClassFiles);
        if (changedClassFiles.isEmpty()) {
            return null;
        }
        
        IJavaProject javaProject = JavaCore.create(getProject());
        List<IPath> outputPaths = new ArrayList<IPath>();
        IWorkspaceRoot root = getProject().getWorkspace().getRoot();
        if (javaProject.getOutputLocation() != null) {
            outputPaths.add(root.getFile(javaProject.getOutputLocation()).getLocation());
        }
        for (IClasspathEntry entry : javaProject.getResolvedClasspath(false)) {
            if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
                if (entry.getOutputLocation() != null) {
                    outputPaths.add(root.getFile(entry.getOutputLocation()).getLocation());
                }
            }
        }

        List<String> changedClasses = new ArrayList<String>();
        for (IPath f : changedClassFiles) {
            for (IPath outputPath : outputPaths) {
                if (outputPath.isPrefixOf(f)) {
                    String className = f.makeRelativeTo(outputPath).toString();
                    className = className.substring(0, className.length() - ".class".length());
                    className = className.replace('/', '.');
                    changedClasses.add(className);
                }
            }
        }
        
        try {
            if (!tryBuild(monitor, javaProject, outputPaths, root, changedClasses, true)) {
                // Debug build failed. Retry with release build.
                tryBuild(monitor, javaProject, outputPaths, root, changedClasses, false);
            }
            
            RoboVMPlugin.consoleInfo(monitor.isCanceled() ? "Build canceled" : "Build done");
        } catch (Exception e) {
            RoboVMPlugin.consoleError("Build failed");
            throw new CoreException(new Status(IStatus.ERROR, RoboVMPlugin.PLUGIN_ID,
                    "Build failed. Check the RoboVM console for more information.", e));
        } finally {
            monitor.done();
        }
        
        return null;
    }

    private boolean tryBuild(IProgressMonitor monitor, IJavaProject javaProject, List<IPath> outputPaths,
            IWorkspaceRoot root, List<String> changedClasses, boolean debug) throws IOException, CoreException,
            JavaModelException, Exception {

        Config config = createConfig(javaProject, outputPaths, root, debug);
        monitor.beginTask("Incremental build of changed classes", changedClasses.size());
        RoboVMPlugin.consoleInfo("Building %d changed classes for target (%s %s %s)", 
                changedClasses.size(), config.getOs(), config.getArch(), debug ? "debug" : "release");
        final List<Clazz> compileClasses = new ArrayList<Clazz>();
        for (String c : changedClasses) {
            compileClasses.add(config.getClazzes().load(c.replace('.', '/')));
        }
        
        AppCompilerThread thread = new AppCompilerThread(new AppCompiler(config), monitor) {
            @Override
            protected void doCompile() throws Exception {
                compiler.compile(compileClasses, false, new ClassCompilerListener() {
                    @Override
                    public void success(Clazz clazz) {
                        monitor.worked(1);
                    }
                    @Override
                    public void failure(Clazz clazz, Throwable t) {
                    }
                });
            }
        };
        try {
            thread.compile();
        } catch (Exception e) {
            if (debug && e.getClass().getSimpleName().equals("UnlicensedException")) {
                // Debug builds are not allowed, Retry with release build.
                monitor.done();
                RoboVMPlugin.consoleWarn(e.getMessage());
                RoboVMPlugin.consoleWarn("Incremental debug build failed. Will try with release build instead.");
                return false;
            }
            throw e;
        }
         
        /*
         * Set the modified time of the object files for all compiled
         * class to 'now'. Since we don't compile classes in the order of 
         * dependency without this some classes may have to be recompiled 
         * on the next launch since if depend on classes that were compiled 
         * later by this builder.
         */
        long lastModified = System.currentTimeMillis();
        for (Clazz clazz : compileClasses) {
            File oFile = config.getOFile(clazz);
            if (oFile.exists()) {
                oFile.setLastModified(lastModified);
            }
        }
        return true;
    }

    private Config createConfig(IJavaProject javaProject, List<IPath> outputPaths, IWorkspaceRoot root, boolean debug)
            throws IOException, CoreException, JavaModelException {
        Home home = RoboVMPlugin.getRoboVMHome();
        Config.Builder configBuilder = new Config.Builder();
        configBuilder.skipLinking(true);
        configBuilder.skipRuntimeLib(true);
        configBuilder.debug(debug);
        configBuilder.addPluginArgument("debug:sourcepath=" + RoboVMPlugin.getSourcePaths(javaProject));
        if (home.isDev()) {
            configBuilder.dumpIntermediates(true);
        }
        
        // Use console target always since we're only going to compile anyway and not link.
        configBuilder.targetType(TargetType.console);
        configBuilder.os(RoboVMPlugin.getOS(getProject()));
        configBuilder.arch(RoboVMPlugin.getArch(getProject()));
        
        configBuilder.logger(RoboVMPlugin.getConsoleLogger());
        
        for (IClasspathEntry entry : javaProject.getResolvedClasspath(false)) {
            if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
                IPath path = entry.getPath();
                IResource member = root.findMember(path);
                if (member != null) {
                    configBuilder.addClasspathEntry(member.getLocation().toFile());
                } else {
                    if (path.toString().endsWith("/robovm-rt.jar") 
                            || path.toString().endsWith("/robovm-rt-" + Version.getVersion() + ".jar")
                            || home.getRtPath().equals(path.toFile())) {
                        configBuilder.addBootClasspathEntry(path.toFile());
                    } else {
                        configBuilder.addClasspathEntry(path.toFile());
                    }
                }
            }
        }
        for (IPath outputPath : outputPaths) {
            configBuilder.addClasspathEntry(outputPath.toFile());
        }
        
        configBuilder.home(home);
        Config config = configBuilder.build();
        return config;
    }
    
    private void findChangedClassFiles(IProject project, boolean full, final List<IPath> files) throws CoreException {
        IResourceDelta delta = full ? null : getDelta(project);
        final IResourceVisitor visitor = new IResourceVisitor() {
            public boolean visit(IResource resource) throws CoreException {
                if ("class".equals(resource.getFileExtension()) && resource.exists()) {
                    // TODO: Handle deleted class files?
                    files.add(resource.getLocation());
                }
                return true;
            }
        };
        if (delta == null) {
            project.accept(visitor);
        } else {
            delta.accept(new IResourceDeltaVisitor() {
                public boolean visit(IResourceDelta d) throws CoreException {
                    return visitor.visit(d.getResource());
                }
            });
        }
    }
}
