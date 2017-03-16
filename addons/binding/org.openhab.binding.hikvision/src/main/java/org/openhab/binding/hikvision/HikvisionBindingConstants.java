/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hikvision;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link HikvisionBinding} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Ryan - Initial contribution
 */
public class HikvisionBindingConstants {

    public static final String BINDING_ID = "hikvision";

    // List of all Thing Type UIDs
    public final static ThingTypeUID THING_TYPE_HIKVISION = new ThingTypeUID(BINDING_ID, "hikvision");

    // List of all Channel ids
    public final static String MOTION_DETECTION = "VMD";
    public final static String LINE_DETECTION = "linedetection";
    public final static String INTRUSTION_DETECTION = "fielddetection";
    public final static String VIDEO_LOSS = "videoloss";
    public final static String SNAPSHOT = "snapshot";
    public final static String LINK_TO_SNAPSHOT = "linkToSnapshot";

    public static final String IP_ADDRESS = "ipAddress";
    public static final String SERIAL_NUMBER = "serialNo";

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    public static final String BASE_URL = "http://{0}{1}";
    public static final String BASE_URL_WITH_AUTH = "http://{0}:{1}@{2}{3}";
    public static final String ALERT_STREAM = "/ISAPI/Event/notification/alertStream";
    public static final String SNAPSHOT_URL = "/Streaming/channels/1/picture"; // TODO : Allow switching to channel 2?

}
