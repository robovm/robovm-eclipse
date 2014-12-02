/*
 * Copyright (C) 2014 Trillian Mobile AB
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
package org.robovm.eclipse.internal.junit;

import java.io.IOException;

import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.robovm.compiler.target.ios.DeviceType;
import org.robovm.compiler.target.ios.DeviceType.DeviceFamily;
import org.robovm.eclipse.RoboVMPlugin;

/**
 *
 */
public class IOSSimulatorJUnitLaunchShortcut extends AbstractJUnitLaunchShortcut {

    @Override
    protected String getConfigurationTypeId() {
        return IOSSimulatorJUnitLaunchConfigurationDelegate.TYPE_ID;
    }

    @Override
    protected void customizeConfiguration(ILaunchConfigurationWorkingCopy wc) {
        try {
            wc.setAttribute(IOSSimulatorJUnitLaunchConfigurationDelegate.ATTR_IOS_SIM_DEVICE_TYPE,
                    DeviceType.getBestDeviceType(RoboVMPlugin.getRoboVMHome(), DeviceFamily.iPhone).getSimpleDeviceTypeId());
        } catch (IOException e) {
            RoboVMPlugin.log(e);
        }
    }
}
