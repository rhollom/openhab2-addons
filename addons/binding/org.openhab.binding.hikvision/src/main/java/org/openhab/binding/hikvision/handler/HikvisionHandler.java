/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hikvision.handler;

import static org.openhab.binding.hikvision.HikvisionBindingConstants.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.IOUtils;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.RawType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.hikvision.HikvisionBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HikvisionHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Ryan - Initial contribution
 */
public class HikvisionHandler extends BaseThingHandler {

    public class HeartbeatRunnable implements Runnable {

        public HeartbeatRunnable() {
        }

        @Override
        public void run() {
            if (reconnectCount > 10) {
                logger.debug("Reconnect count exceeded maximum.  No more reconnect attempts will be made for {}",
                        getIPAddress());
            }
            if (!isConnected()) {
                logger.debug("Camera {} not connected to event stream, attempting reconnect", getIPAddress());
                reconnectCount++;
                connectToAlertStream();
            }
        }
    }

    public class SnapshotRunnable implements Runnable {

        public SnapshotRunnable() {
        }

        @Override
        public void run() {
            if (!isConnected()) {
                logger.debug("Camera {} not connected to event stream, snapshot not updated", getIPAddress());
            }
            State snapshot = getSnapshot();
            updateState(HikvisionBindingConstants.SNAPSHOT, snapshot);
        }
    }

    public class AlertStreamRunnable implements Runnable {

        private static final String CONTENT_LENGTH = "Content-Length: ";

        public AlertStreamRunnable() {
        }

        @Override
        public void run() {
            HttpURLConnection con = null;
            BufferedReader in = null;

            try {
                String connectionURL = MessageFormat.format(BASE_URL, getIPAddress(), ALERT_STREAM);
                logger.debug("Opening connection to {}", connectionURL);
                URL obj = new URL(connectionURL);
                con = (HttpURLConnection) obj.openConnection();

                String userpass = getUsername() + ":" + getPassword();
                String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(userpass.getBytes());

                con.setRequestProperty("Authorization", basicAuth);

                // optional default is GET
                con.setRequestMethod("GET");

                // add request header
                con.setRequestProperty("User-Agent", USER_AGENT);
                // con.setRequestProperty("content-type", "multipart/x-mixed-replace");

                int responseCode = con.getResponseCode();
                logger.debug("\nSending 'GET' request to URL : {}", connectionURL);
                logger.debug("Response Code : {}", responseCode);
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    handleConnectionFailure("Unable to connect with the given credentials");
                    return;
                }
                reconnectCount = 0; // reset the reconnect count
                // useMultiPartReader(con);
                // useStaxParser(con);

                InputStream connStream = con.getInputStream();
                in = new BufferedReader(new InputStreamReader(connStream));
                String inputLine;

                SAXParser newSAXParser = SAXParserFactory.newInstance().newSAXParser();
                FastEventParser handler = new FastEventParser(HikvisionHandler.this);
                Charset charSet = Charset.forName("UTF-8");
                int tagLength = CONTENT_LENGTH.length();
                while (isConnected() && (inputLine = in.readLine()) != null) {
                    if (inputLine.startsWith(CONTENT_LENGTH)) {
                        int length = Integer.parseInt(inputLine.substring(tagLength));
                        char[] chars = new char[length];
                        int available = con.getInputStream().available();
                        while (available < length) {
                            // wait until there's enough in the buffer
                            try {
                                // put a brief sleep in to avoid sucking up the CPU
                                // events appear to be emitted at most a few times per second
                                Thread.sleep(200);
                                // System.out.print('.');
                            } catch (Exception e) {
                            }
                            available = connStream.available();
                        }
                        in.read(chars);
                        ByteBuffer bb = charSet.encode(CharBuffer.wrap(chars));
                        byte[] b = new byte[bb.remaining()];
                        bb.get(b);
                        newSAXParser.parse(new ByteArrayInputStream(b), handler);
                    } else {
                        // should be boundary tag
                    }
                }
            } catch (Exception e) {
                handleConnectionFailure(
                        "Unable to connect with the given credentials [Reason: " + e.getMessage() + "]");

                // fall through to reconnect code
            } finally {
                setConnected(false);
                logger.info("Disconnected from alert stream at {}", getIPAddress());
                try {
                    if (in != null) {
                        in.close();
                    }
                    if (con != null) {
                        con.disconnect();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private Logger logger = LoggerFactory.getLogger(HikvisionHandler.class);

    private ScheduledFuture<?> refreshJob;
    private ScheduledFuture<?> activeRefreshJob;
    private ScheduledFuture<?> heartbeatJob;

    private Runnable alertStreamRunnable = new AlertStreamRunnable();
    private Runnable snapshotRunnable = new SnapshotRunnable();
    private Runnable heartbeatRunnable = new HeartbeatRunnable();

    private final String USER_AGENT = "Mozilla/5.0";

    private Set<ChannelUID> activeChannels = new HashSet<>();

    private boolean connected;
    private int reconnectCount;

    public HikvisionHandler(Thing thing) {
        super(thing);
    }

    public void updateChannel(String channelID) {
        logger.trace("Updating state on channel {} for {}", channelID, getIPAddress());
        State newState = UnDefType.UNDEF;
        switch (channelID) {
            case SNAPSHOT:
                newState = getSnapshot();
                break;

            case MOTION_DETECTION:
            case LINE_DETECTION:
            case VIDEO_LOSS:
            case INTRUSTION_DETECTION:
                newState = OnOffType.OFF;
                break;

            default:
                break;
        }
        updateState(channelID, newState);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (!isLinked(channelUID.getId())) {
            return;
        }

        logger.trace("Handling command on channel {} for {}", channelUID.getId(), getIPAddress());
        if (command instanceof RefreshType) {
            updateChannel(channelUID.getId());
            return;
        }
        if (command instanceof State) {
            updateState(channelUID, (State) command);
            if (command instanceof OnOffType) {
                handleOnOffCommand(channelUID, (OnOffType) command);
            }
            return;
        }
        // Note: if communication with thing fails for some reason,
        // indicate that by setting the status with detail information
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
        // "Could not control device at IP address x.x.x.x");
    }

    private void handleOnOffCommand(ChannelUID channelUID, OnOffType command) {
        Channel channel = getThing().getChannel(channelUID.getId());
        if (channel != null) {
            Configuration configuration = channel.getConfiguration();
            Object object = configuration.get(HikvisionBindingConstants.LINK_TO_SNAPSHOT);
            if (object != null && object instanceof Boolean) {
                if ((Boolean) object) {
                    if (command == OnOffType.ON) {
                        activeChannels.add(channelUID);
                        if (activeRefreshJob == null || activeRefreshJob.isDone()) {
                            logger.trace("Scheduling active refresh job on channel {} for {}", channelUID.getId(),
                                    getIPAddress());
                            activeRefreshJob = scheduler.scheduleAtFixedRate(snapshotRunnable, 0, 1000,
                                    TimeUnit.MILLISECONDS);
                        }
                    } else {
                        activeChannels.remove(channelUID);
                        if (activeChannels.size() == 0) {
                            logger.trace("Cancelling refresh job -- event {} complete for {}", channelUID.getId(),
                                    getIPAddress());
                            activeRefreshJob.cancel(true);
                        }
                    }
                } else {
                    if (activeRefreshJob != null && activeChannels.size() == 0) {
                        activeRefreshJob.cancel(true);
                    }
                }
            }
        }
    }

    private synchronized void onUpdate() {
        if (refreshJob == null || refreshJob.isCancelled()) {
            Configuration config = getThing().getChannel(HikvisionBindingConstants.SNAPSHOT).getConfiguration();
            int refreshInterval = 0;
            Object refreshConfig = config.get("refreshInterval");
            if (refreshConfig != null) {
                refreshInterval = ((BigDecimal) refreshConfig).intValue();
            }
            if (refreshInterval > 0) {
                refreshJob = scheduler.scheduleAtFixedRate(snapshotRunnable, 0, refreshInterval, TimeUnit.SECONDS);
            }
        }
    }

    @Override
    public void handleUpdate(ChannelUID channelUID, State newState) {
        logger.trace("Updating state on channel {} to {}", channelUID.getId(), newState);
        super.handleUpdate(channelUID, newState);
    }

    @Override
    public void initialize() {
        Configuration configuration = getConfig();
        String ipAddress = (String) configuration.get(HikvisionBindingConstants.IP_ADDRESS);
        if (ipAddress != null) {
            connectToAlertStream();
            onUpdate();
        }
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void dispose() {
        logger.trace("Disposing handler for hikvision camera {}", getIPAddress());

        if (refreshJob != null && !refreshJob.isCancelled()) {
            refreshJob.cancel(true);
            refreshJob = null;
        }
        if (activeRefreshJob != null && !activeRefreshJob.isCancelled()) {
            activeRefreshJob.cancel(true);
            activeRefreshJob = null;
        }
        if (heartbeatJob != null && !heartbeatJob.isCancelled()) {
            heartbeatJob.cancel(true);
            heartbeatJob = null;
        }
        setConnected(false);

        super.dispose();
    }

    @Override
    public void handleRemoval() {
        // this is handled in the dispose method
        logger.trace("Removing handler for hikvision camera {}", getIPAddress());
        super.handleRemoval();
    }

    public void handleConnectionFailure(String reason) {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, reason);
    }

    public boolean connectToAlertStream() {
        if (heartbeatJob != null) {
            // cancel existing heartbeatJob
            heartbeatJob.cancel(true);
        }
        logger.debug("Connecting to alert stream at {}", getIPAddress());
        Thread alertStreamThread = new Thread(alertStreamRunnable, "Hikvision-AlertStream-" + getIPAddress());
        alertStreamThread.start();
        setConnected(true);
        heartbeatJob = scheduler.scheduleAtFixedRate(heartbeatRunnable, 5, 10, TimeUnit.SECONDS);
        return true;
    }

    private void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isConnected() {
        return connected;
    }

    private String getUsername() {
        return (String) getConfig().get(HikvisionBindingConstants.USERNAME);
    }

    private String getPassword() {
        return (String) getConfig().get(HikvisionBindingConstants.PASSWORD);
    }

    String getIPAddress() {
        return (String) getConfig().get(HikvisionBindingConstants.IP_ADDRESS);
    }

    public void ensureOff() {
        if (activeRefreshJob != null && !activeRefreshJob.isDone()) {
            logger.debug("Cancelling refresh job -- all events complete for {}", getIPAddress());
            activeRefreshJob.cancel(true);
            activeChannels.clear();
        }
    }

    private State getSnapshot() {
        URL url = getSnapshotUrl();
        if (url != null) {
            try {
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                String username = getUsername();
                String password = getPassword();
                String userpass = username + ":" + password;
                String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(userpass.getBytes());

                con.setRequestProperty("Authorization", basicAuth);
                State newState = new RawType(IOUtils.toByteArray(con.getInputStream()));
                IOUtils.closeQuietly(con.getInputStream());
                con.disconnect();
                return newState;
            } catch (IOException e) {
                logger.debug("Failed to obtain snapshot {}", e.getMessage());
                return UnDefType.UNDEF;
            }
        }
        return UnDefType.UNDEF;
    }

    private URL getSnapshotUrl() {
        String connectionURL = MessageFormat.format(BASE_URL, getIPAddress(), SNAPSHOT_URL);
        logger.trace("Opening connection to {}", connectionURL);
        try {
            return new URL(connectionURL);
        } catch (MalformedURLException e) {
            logger.error("Unable to obtain snapshot at {} [Reason: {}]", connectionURL, e.getMessage());
        }
        return null;
    }

}
