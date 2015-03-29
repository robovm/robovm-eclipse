/*
 * Copyright (C) 2015 RoboVM AB
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
package org.robovm.eclipse.internal.ib;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.robovm.eclipse.RoboVMPlugin;

/**
 * 
 */
public class IBIntegratorManager implements IResourceChangeListener {
    private static boolean hasIBIntegrator;
    private static IBIntegratorManager instance;

    private Map<String, IBIntegratorProxy> daemons = new HashMap<String, IBIntegratorProxy>();

    static {
        try {
            IBIntegratorProxy.getIBIntegratorClass();
            hasIBIntegrator = true;
        } catch (Throwable t) {
            hasIBIntegrator = false;
            RoboVMPlugin.getConsoleLogger().warn(t.getMessage());
        }
    }

    public static IBIntegratorManager getInstance() {
        if (instance == null) {
            instance = new IBIntegratorManager();
        }
        return instance;
    }

    public IBIntegratorProxy getIBIntegrator(IProject project) {
        return daemons.get(project.getName());
    }

    public void start() {
        if (!System.getProperty("os.name").toLowerCase().contains("mac os x")) {
            return;
        }
        for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (p.isOpen()) {
                try {
                    projectChanged(p);
                } catch (CoreException e) {
                    RoboVMPlugin.log(e);
                }
            }
        }
        ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
    }

    private void projectChanged(IProject project) throws CoreException {
        if (!RoboVMPlugin.isRoboVMIOSProject(project)) {
            shutdownDaemonIfRunning(project);
            return;
        }

        String name = project.getName();
        IBIntegratorProxy proxy = daemons.get(name);
        if (proxy == null) {
            try {
                File dir = RoboVMPlugin.getBuildDir(name);
                dir.mkdirs();
                RoboVMPlugin.consoleDebug("Starting Interface Builder integrator daemon for project %s", name);
                proxy = new IBIntegratorProxy(RoboVMPlugin.getConsoleLogger(), name, dir);
                proxy.start();
                daemons.put(name, proxy);
            } catch (RuntimeException e) {
                if (e.getClass().getSimpleName().equals("UnlicensedException")) {
                    RoboVMPlugin.getConsoleLogger().warn("Failed to start Interface Builder "
                            + "integrator for project " + name + ": " + e.getMessage());
                }
            }
        }

        if (proxy != null) {
            Set<File> outputPaths = new HashSet<>();
            IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
            IJavaProject javaProject = JavaCore.create(project);
            if (javaProject.getOutputLocation() != null) {
                outputPaths.add(root.findMember(javaProject.getOutputLocation()).getLocation().toFile());
            }
            for (IClasspathEntry cpe : javaProject.getRawClasspath()) {
                if (cpe.getOutputLocation() != null) {
                    outputPaths.add(root.findMember(cpe.getOutputLocation()).getLocation().toFile());
                }
            }
            proxy.addSourceFolders(outputPaths.toArray(new File[outputPaths.size()]));
            Collection<File> resourcePaths = RoboVMPlugin.getRoboVMProjectResourcePaths(project);
            proxy.addResourceFolders(resourcePaths.toArray(new File[resourcePaths.size()]));
        }
    }

    private void shutdownDaemonIfRunning(IProject project) {
        String name = project.getName();
        IBIntegratorProxy proxy = daemons.remove(name);
        if (proxy != null) {
            RoboVMPlugin.consoleDebug("Shutting down Interface Builder integrator daemon for project %s", name);
            proxy.shutDown();
        }
    }

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        if (event == null || event.getDelta() == null) {
            return;
        }

        if (!hasIBIntegrator) {
            return;
        }

        try {

            event.getDelta().accept(new IResourceDeltaVisitor() {
                public boolean visit(final IResourceDelta delta) throws CoreException {
                    IResource resource = delta.getResource();
                    if ((resource.getType() & IResource.PROJECT) != 0) {
                        IProject project = (IProject) resource;
                        String name = project.getName();

                        if (project.isOpen()) {
                            if ((delta.getFlags() & IResourceDelta.OPEN) != 0) {
                                // Could be a RoboVM project that just opened.
                                projectChanged(project);
                            }
                        } else if (daemons.containsKey(name)) {
                            // Project was closed. Stop the daemon.
                            shutdownDaemonIfRunning(project);
                        }
                    } else if ((resource.getType() & IResource.FILE) != 0) {
                        if ("robovm.xml".equals(resource.getName())) {
                            // A robovm.xml has been modified in some way. Could
                            // be a change to the resource folders.
                            projectChanged(resource.getProject());
                        }
                    }
                    return true;
                }
            });

        } catch (Throwable t) {
            RoboVMPlugin.log(t);
        }
    }

}
