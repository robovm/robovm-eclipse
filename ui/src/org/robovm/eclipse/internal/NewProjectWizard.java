/*
 * Copyright (C) 2012 RoboVM AB
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
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ui.actions.OpenJavaPerspectiveAction;
import org.eclipse.jdt.ui.wizards.NewJavaProjectWizardPageOne;
import org.eclipse.jdt.ui.wizards.NewJavaProjectWizardPageTwo;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.osgi.service.prefs.BackingStoreException;
import org.robovm.eclipse.RoboVMPlugin;
import org.robovm.templater.Templater;

/**
 *
 * @version $Id$
 */
public class NewProjectWizard extends Wizard implements INewWizard {

    protected RoboVMPageOne page1;
    protected NewJavaProjectWizardPageTwo page2;

    public NewProjectWizard() {
        setWindowTitle("New RoboVM Console Project");
    }

    @Override
    public void addPages() {
        if (page1 == null) {
            page1 = createPageOne();
            page1.setTitle(page1.getTitle().replace("Java", "RoboVM"));
            page1.setDescription(page1.getDescription().replace("Java", "RoboVM"));
        }
        addPage(page1);
        if (page2 == null) {
            page2 = createPageTwo(page1);
        }
        addPage(page2);
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        setHelpAvailable(false);
    }

    protected RoboVMPageOne createPageOne() {
        RoboVMPageOne page = new RoboVMPageOne(getDefaultArch(), getDefaultOs());
        page.setImageDescriptor(RoboVMPlugin.getDefault().getImageRegistry()
                .getDescriptor(RoboVMPlugin.IMAGE_NEW_CONSOLE_PROJECT_BANNER));
        return page;
    }

    protected NewJavaProjectWizardPageTwo createPageTwo(NewJavaProjectWizardPageOne mainPage) {
        NewJavaProjectWizardPageTwo page = new NewJavaProjectWizardPageTwo(mainPage);
        page.setImageDescriptor(RoboVMPlugin.getDefault().getImageRegistry()
                .getDescriptor(RoboVMPlugin.IMAGE_NEW_CONSOLE_PROJECT_BANNER));
        return page;
    }

    protected String getDefaultArch() {
        return RoboVMPlugin.ARCH_AUTO;
    }

    protected String getDefaultOs() {
        return RoboVMPlugin.OS_AUTO;
    }

    protected List<IClasspathEntry> customizeClasspath(List<IClasspathEntry> classpath) {
        return classpath;
    }

    protected void customizeTemplate(Templater templater) throws Exception {
        IJavaProject javaProject = page2.getJavaProject();
        IProject project = javaProject.getProject(); 
        templater.mainClass("Main");
        templater.appName(project.getName());
        templater.appId(project.getName());
    }
    
    protected String getTemplateName() {
        if(this instanceof NewCocoaTouchProjectWizard) {
            return "default";
        } else {
            return "console";
        }
    }

    @Override
    public boolean performFinish() {
        try {
            page2.performFinish(new NullProgressMonitor());
            IJavaProject javaProject = page2.getJavaProject();
            IProject project = javaProject.getProject();            

            // TODO create selection screen for the template type
            String templateName = getTemplateName();
            Templater templater = new Templater(RoboVMPlugin.getConsoleLogger(), templateName);
            File projectRoot = project.getLocation().toFile();
            customizeTemplate(templater);
            templater.buildProject(projectRoot);

            page1.storePreferences(project);
            IClasspathEntry[] oldClasspath = javaProject.getRawClasspath();
            List<IClasspathEntry> newClasspath = new ArrayList<IClasspathEntry>();

            String rootSrc = javaProject.getPath().append("src").toString();

            for (IClasspathEntry entry : oldClasspath) {
                if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER
                        && entry.getPath().toString().equals("org.eclipse.jdt.launching.JRE_CONTAINER")) {

                    newClasspath.add(JavaCore.newContainerEntry(new Path(RoboVMClasspathContainer.ID)));
                } else if (entry.getPath().toString().startsWith(rootSrc)) {
                    // Cannot have nested classpath entries.
                    newClasspath.add(JavaCore.newSourceEntry(javaProject.getPath().append("src/main/java")));
                } else {
                    newClasspath.add(entry);
                }
            }
            newClasspath = customizeClasspath(newClasspath);
            javaProject.setRawClasspath(newClasspath.toArray(
                    new IClasspathEntry[newClasspath.size()]),
                    new NullProgressMonitor());
            RoboVMNature.configureNatures(project, new NullProgressMonitor());

            project.refreshLocal(IResource.DEPTH_INFINITE, null);
        } catch (Exception e) {
            RoboVMPlugin.log(e);
            return false;
        }
        OpenJavaPerspectiveAction action = new OpenJavaPerspectiveAction();
        action.run();

        return true;
    }

    public static class RoboVMPageOne extends NewJavaProjectWizardPageOne {

        private final String defaultArch;
        private final String defaultOs;
        private ProjectProperties projectProperties = null;

        public RoboVMPageOne(String defaultArch, String defaultOs) {
            this.defaultArch = defaultArch;
            this.defaultOs = defaultOs;
        }

        @Override
        public void createControl(Composite parent) {
            // Wrap the contents in a ScrolledComposite to make it accessible even on small screens
            final ScrolledComposite sc = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.H_SCROLL);
            sc.setExpandVertical(true);
            sc.setExpandHorizontal(true);
            final Composite composite = new Composite(sc, SWT.NONE);
            composite.setLayout(new GridLayout(1, true));
            sc.setContent(composite);
            sc.addControlListener(new ControlAdapter() {
                public void controlResized(ControlEvent e) {
                    Rectangle r = sc.getClientArea();
                    sc.setMinSize(composite.computeSize(r.width,
                            SWT.DEFAULT));
                }
            });
            super.createControl(composite);
        }

        @Override
        public String getCompilerCompliance() {
            return JavaCore.VERSION_1_7;
        }

        @Override
        public IClasspathEntry[] getDefaultClasspathEntries() {
            return new IClasspathEntry[] {
                JavaCore.newContainerEntry(new Path(RoboVMClasspathContainer.ID))
            };
        }

        @Override
        public IClasspathEntry[] getSourceClasspathEntries() {
            IClasspathEntry defaultEntry = super.getSourceClasspathEntries()[0];
            return new IClasspathEntry[] {
                JavaCore.newSourceEntry(defaultEntry.getPath().append("main/java"))
            };
        }

        @Override
        protected Control createJRESelectionControl(Composite composite) {
            // Hide the JRE selection control
            Label l = new Label(composite, NONE);
            l.setSize(1, 1);
            return l;
        }

        @Override
        protected Control createInfoControl(Composite composite) {
            addCustomControls(composite);
            return super.createInfoControl(composite);
        }

        protected void addCustomControls(Composite parent) {
            projectProperties = new ProjectProperties(parent, true);
            projectProperties.setArch(defaultArch);
            projectProperties.setOs(defaultOs);
        }

        public void storePreferences(IProject project) throws BackingStoreException {
            projectProperties.storePreferences(project);
        }
    }
}
