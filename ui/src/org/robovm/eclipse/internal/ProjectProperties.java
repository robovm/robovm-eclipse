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

import static org.robovm.eclipse.RoboVMPlugin.*;

import java.util.Arrays;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.osgi.service.prefs.BackingStoreException;
import org.robovm.compiler.config.OS;
import org.robovm.eclipse.RoboVMPlugin;

/**
 * @author niklas
 *
 */
public class ProjectProperties {

    private static final String[] POSSIBLE_ARCH_VALUES;
    private static final String[] POSSIBLE_ARCH_NAMES;
    private static final String[] POSSIBLE_OS_VALUES;
    private static final String[] POSSIBLE_OS_NAMES;

    static {
        POSSIBLE_ARCH_VALUES = new String[ALL_ARCH_VALUES.length + 2];
        POSSIBLE_ARCH_NAMES = new String[ALL_ARCH_NAMES.length + 2];
        POSSIBLE_ARCH_NAMES[0] = "Use workspace default";
        POSSIBLE_ARCH_VALUES[0] = null;
        POSSIBLE_ARCH_NAMES[1] = "Auto (build for current host)";
        POSSIBLE_ARCH_VALUES[1] = ARCH_AUTO;
        for (int i = 0; i < ALL_ARCH_NAMES.length; i++) {
            POSSIBLE_ARCH_NAMES[i + 2] = ALL_ARCH_NAMES[i];
            POSSIBLE_ARCH_VALUES[i + 2] = ALL_ARCH_VALUES[i].toString();
        }
        
        POSSIBLE_OS_VALUES = new String[OS.values().length + 2];
        POSSIBLE_OS_NAMES = new String[OS.values().length + 2];
        POSSIBLE_OS_NAMES[0] = "Use workspace default";
        POSSIBLE_OS_VALUES[0] = null;
        POSSIBLE_OS_NAMES[1] = "Auto (build for current host)";
        POSSIBLE_OS_VALUES[1] = OS_AUTO;
        for (int i = 0; i < OS.values().length; i++) {
            POSSIBLE_OS_NAMES[i + 2] = OS.values()[i].toString();
            POSSIBLE_OS_VALUES[i + 2] = OS.values()[i].toString();
        }
    }

    private Composite composite;
    private String arch = null;
    private String os = null;
    private FontMetrics fontMetrics = null;
    private Combo archCombo;
    private Combo osCombo;
    
    public ProjectProperties(Composite parent, boolean showBorder) {
        GC gc = new GC(parent);
        gc.setFont(JFaceResources.getDialogFont());
        fontMetrics = gc.getFontMetrics();
        gc.dispose();
        
        if (showBorder) {
            Group group = new Group(parent, SWT.NONE);
            group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            group.setFont(parent.getFont());
            group.setLayout(initGridLayout(new GridLayout(3, false), true));
            group.setText("Incremental build settings");
            composite = group;
        } else {
            composite = new Composite(parent, SWT.NONE);
            composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            composite.setFont(parent.getFont());
            composite.setLayout(initGridLayout(new GridLayout(3, false), false));
        }
        createControls(composite);
    }

    public Composite getComposite() {
        return composite;
    }
    
    public void setArch(String arch) {
        this.arch = arch;
        archCombo.select(Arrays.asList(POSSIBLE_ARCH_VALUES).indexOf(arch));
    }
    
    public void setOs(String os) {
        this.os = os;
        osCombo.select(Arrays.asList(POSSIBLE_OS_VALUES).indexOf(os));
    }
    
    protected void createControls(Composite parent) {
        Label archLabel = new Label(parent, SWT.NONE);
        archLabel.setFont(parent.getFont());
        archLabel.setText("Arch:");
        archLabel.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, false));
        
        archCombo = new Combo(parent, SWT.READ_ONLY | SWT.BORDER);
        archCombo.setItems(POSSIBLE_ARCH_NAMES);
        archCombo.select(0);
        archCombo.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, false, false));
        archCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int index = archCombo.getSelectionIndex();
                arch = POSSIBLE_ARCH_VALUES[index];
            }
        });

        Label emptyLabel = new Label(parent, SWT.NONE);
        GridData emptyLabelGridData = new GridData(GridData.END, GridData.END, true, false);
        emptyLabel.setLayoutData(emptyLabelGridData);
        
        Label osLabel = new Label(parent, SWT.NONE);
        osLabel.setFont(parent.getFont());
        osLabel.setText("OS:");
        osLabel.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, false));
        
        osCombo = new Combo(parent, SWT.READ_ONLY | SWT.BORDER);
        osCombo.setItems(POSSIBLE_OS_NAMES);
        osCombo.select(0);
        osCombo.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, false, false));
        osCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int index = osCombo.getSelectionIndex();
                os = POSSIBLE_OS_VALUES[index];
            }
        });
        
        Link prefsLink = new Link(parent, SWT.NONE);
        prefsLink.setText("<a>Configure default RoboVM settings...</a>");
        GridData prefsLinkGridData = new GridData(GridData.END, GridData.END, true, false);
        prefsLinkGridData.horizontalSpan = 1;
        prefsLink.setLayoutData(prefsLinkGridData);
        prefsLink.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                widgetSelected(e);
            }
            @Override
            public void widgetSelected(SelectionEvent e) {
                String id = RoboVMPreferencePage.ID;
                PreferencesUtil.createPreferenceDialogOn(RoboVMPlugin.getShell(), id, new String[] { id }, null).open();
            }
        });
    }
    
    private GridLayout initGridLayout(GridLayout layout, boolean margins) {
        layout.horizontalSpacing = Dialog.convertHorizontalDLUsToPixels(fontMetrics, IDialogConstants.HORIZONTAL_SPACING);
        layout.verticalSpacing = Dialog.convertVerticalDLUsToPixels(fontMetrics, IDialogConstants.VERTICAL_SPACING);
        if (margins) {
            layout.marginWidth = Dialog.convertHorizontalDLUsToPixels(fontMetrics, IDialogConstants.HORIZONTAL_MARGIN);
            layout.marginHeight = Dialog.convertVerticalDLUsToPixels(fontMetrics, IDialogConstants.VERTICAL_MARGIN);
        } else {
            layout.marginWidth = 0;
            layout.marginHeight = 0;
        }
        return layout;
    }

    public void resetToDefaults() {
        setArch(null);
        setOs(null);
    }
    
    public void loadPreferences(IProject project) {
        IEclipsePreferences node = new ProjectScope(project).getNode(RoboVMPlugin.PLUGIN_ID);
        setArch(node.get(RoboVMPlugin.PREFERENCE_INCREMENTAL_BUILD_ARCH, null));
        setOs(node.get(RoboVMPlugin.PREFERENCE_INCREMENTAL_BUILD_OS, null));
    }
    
    public void storePreferences(IProject project) throws BackingStoreException {
        IEclipsePreferences node = new ProjectScope(project).getNode(RoboVMPlugin.PLUGIN_ID);
        if (arch != null) {
            node.put(RoboVMPlugin.PREFERENCE_INCREMENTAL_BUILD_ARCH, arch);
        } else {
            node.remove(RoboVMPlugin.PREFERENCE_INCREMENTAL_BUILD_ARCH);
        }
        if (os != null) {
            node.put(RoboVMPlugin.PREFERENCE_INCREMENTAL_BUILD_OS, os);
        } else {
            node.remove(RoboVMPlugin.PREFERENCE_INCREMENTAL_BUILD_OS);
        }
        node.flush();
    }
}
