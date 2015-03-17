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

import static org.robovm.eclipse.RoboVMPlugin.*;

import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.robovm.compiler.config.OS;
import org.robovm.eclipse.RoboVMPlugin;

/**
 * @author niklas
 *
 */
public class RoboVMPreferencePage extends FieldEditorPreferencePage implements
        IWorkbenchPreferencePage {

    public static final String ID = "org.robovm.eclipse.preferences.main";
    
    private static final String[][] POSSIBLE_ARCH_NAMES_AND_VALUES;

    static {
        POSSIBLE_ARCH_NAMES_AND_VALUES = new String[ALL_ARCH_NAMES.length + 1][2];
        POSSIBLE_ARCH_NAMES_AND_VALUES[0][0] = "Auto (build for current host)";
        POSSIBLE_ARCH_NAMES_AND_VALUES[0][1] = ARCH_AUTO;
        for (int i = 0; i < ALL_ARCH_NAMES.length; i++) {
            POSSIBLE_ARCH_NAMES_AND_VALUES[i + 1][0] = ALL_ARCH_NAMES[i];
            POSSIBLE_ARCH_NAMES_AND_VALUES[i + 1][1] = ALL_ARCH_VALUES[i].toString();
        }
    }
    
    public RoboVMPreferencePage() {
        super("RoboVM", GRID);
        setPreferenceStore(RoboVMPlugin.getPluginPreferenceStore());
    }

    @Override
    public void init(IWorkbench workbench) {
    }

    @Override
    protected void createFieldEditors() {
        final Composite parent = getFieldEditorParent();
        
        ComboFieldEditor archFieldEditor = new ComboFieldEditor(PREFERENCE_INCREMENTAL_BUILD_ARCH, 
                "Default arch:", POSSIBLE_ARCH_NAMES_AND_VALUES, parent);
        addField(archFieldEditor);
        
        ComboFieldEditor osFieldEditor = new ComboFieldEditor(PREFERENCE_INCREMENTAL_BUILD_OS, 
                "Default OS:", new String[][] {
                {"Auto (build for current host)", OS_AUTO},
                {OS.macosx.toString(), OS.macosx.toString()},
                {OS.ios.toString(), OS.ios.toString()},
                {OS.linux.toString(), OS.linux.toString()}
        }, parent);
        addField(osFieldEditor);
    }
}
