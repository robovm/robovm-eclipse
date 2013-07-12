/*
 * Copyright (C) 2012 Trillian AB
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
import org.robovm.compiler.config.Arch;
import org.robovm.compiler.config.OS;
import org.robovm.eclipse.RoboVMPlugin;

/**
 * @author niklas
 *
 */
public class RoboVMPreferencePage extends FieldEditorPreferencePage implements
        IWorkbenchPreferencePage {

    public static final String ID = "org.robovm.eclipse.preferences.main";
    
    public RoboVMPreferencePage() {
        super("RoboVM", GRID);
        setPreferenceStore(RoboVMPlugin.getDefault().getPreferenceStore());
    }

    @Override
    public void init(IWorkbench workbench) {
    }

    @Override
    protected void createFieldEditors() {
        final Composite parent = getFieldEditorParent();
        
        ComboFieldEditor archFieldEditor = new ComboFieldEditor(PREFERENCE_INCREMENTAL_BUILD_ARCH, 
                "Default arch:", new String[][] {
                {"Auto (build for current host)", ARCH_AUTO},
                {Arch.thumbv7.toString(), Arch.thumbv7.toString()},
                {Arch.x86.toString(), Arch.x86.toString()}
        }, parent);
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
