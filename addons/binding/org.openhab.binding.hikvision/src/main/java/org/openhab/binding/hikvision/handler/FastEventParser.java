package org.openhab.binding.hikvision.handler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class FastEventParser extends DefaultHandler {

    private class EventNotificationAlert {

        private int initialPostCount; // this is a running count starting when the connection is made - starts from the
                                      // end of the previous event of this type
        private int currentPostCount; // this is a running count starting when the connection is made - starts from the
        private String eventType;
        private String startTime;
        private String endTime;

        private HashMap<String, char[]> tagToValMap = new HashMap<String, char[]>();

        public EventNotificationAlert() {
        }

        public void setInitialPostCount(int initialPostCount) {
            this.initialPostCount = initialPostCount;
        }

        public String getStartTime() {
            return startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }

        public int getInitialPostCount() {
            return initialPostCount;
        }

        public int getCurrentPostCount() {
            return currentPostCount;
        }

        public void setCurrentPostCount(int currentCount) {
            this.currentPostCount = currentCount;
        }

        public void setPropertyValue(String property, char[] value) {
            tagToValMap.put(property, value);
        }

        public String getPropertyValueAsString(String property) {
            char[] val = tagToValMap.get(property);
            if (val != null) {
                return String.valueOf(val);
            }
            return null;
        }

        public void setEventType(String currentEventType) {
            this.eventType = currentEventType;
        }

        public String getEventType() {
            return eventType;
        }

    }

    private Logger logger = LoggerFactory.getLogger(FastEventParser.class);
    private HashMap<String, EventNotificationAlert> activeEventMap = new HashMap<String, EventNotificationAlert>();
    private boolean processingEvent = false;

    private String currentElement;
    private String currentEventType;

    private static final String EVENT_NOTIFICATION_ALERT = "EventNotificationAlert";
    private static final String EVENT_DESC_TAG = "eventDescription";
    private static final String EVENT_STATE_TAG = "eventState";
    private static final String EVENT_TYPE_TAG = "eventType";
    private static final String ACTIVE_POST_COUNT_TAG = "activePostCount";
    private static final String DATE_TIME_TAG = "dateTime";

    private EventNotificationAlert currentEvent;
    private HikvisionHandler hikvisionHandler;

    /**
     * @param hikvisionHandler
     */
    public FastEventParser(HikvisionHandler hikvisionHandler) {
        this.hikvisionHandler = hikvisionHandler;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (EVENT_NOTIFICATION_ALERT.equals(qName)) {
            processingEvent = true;
            currentEvent = new EventNotificationAlert();
        } else if (processingEvent) {
            currentElement = qName;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (processingEvent && currentElement != null) {
            if (length == 1 && ch[start] == '\n') {
                return;
            }
            char[] value = new char[length];
            for (int i = 0; i < length; i++) {
                value[i] = ch[start + i];
            }
            currentEvent.setPropertyValue(currentElement, value);
            if (ACTIVE_POST_COUNT_TAG.equals(currentElement)) {
                int activePostCount = Integer.valueOf(String.valueOf(value));
                if (activePostCount == 0) {
                    processActiveEvents();
                } else {
                    if (currentEvent.getInitialPostCount() > 0) {
                        currentEvent.setCurrentPostCount(activePostCount);
                    } else {
                        currentEvent.setInitialPostCount(activePostCount);
                    }
                }
            }
            if (EVENT_TYPE_TAG.equals(currentElement)) {
                currentEventType = String.valueOf(value);
                currentEvent.setEventType(currentEventType);
                EventNotificationAlert activeEvent = activeEventMap.get(currentEventType);
                if (activeEvent != null) {
                    activeEvent.setCurrentPostCount(currentEvent.getInitialPostCount());
                    activeEvent.setEndTime(currentEvent.getStartTime());
                }
            }
            if (DATE_TIME_TAG.equals(currentElement)) {
                String time = String.valueOf(value);
                currentEvent.setStartTime(time);
            }
            if (EVENT_STATE_TAG.equals(currentElement)) {
                // look for 'active' event state
                if (length == 6) {
                    // optimize for the length of the string 'active'
                    String val = String.valueOf(value);
                    if ("active".equals(val)) {
                        if (!activeEventMap.containsKey(currentEventType)) {
                            logger.trace("Got a new event of type {} for {}", currentEventType,
                                    hikvisionHandler.getIPAddress());
                            Channel channel = hikvisionHandler.getThing().getChannel(currentEventType);
                            if (channel == null) {
                                logger.debug("No channel found for event type {} for {}", currentEventType,
                                        hikvisionHandler.getIPAddress());
                                return;
                            }
                            hikvisionHandler.handleCommand(channel.getUID(), OnOffType.ON);
                            activeEventMap.put(currentEventType, currentEvent); // This assumes that the eventType tag
                                                                                // comes before the eventState tag
                        }
                    }
                }
            }
        }
    }

    private void processActiveEvents() {
        if (activeEventMap.size() > 0) {
            logger.trace("Processing all active events for {}", hikvisionHandler.getIPAddress());
            Iterator<String> keySet = activeEventMap.keySet().iterator();
            while (keySet.hasNext()) {
                String key = keySet.next();
                // EventNotificationAlert eventNotificationAlert = activeEventMap.get(key);
                logger.trace("Turning off channel '{}'", key);
                hikvisionHandler.handleCommand(getChannelUID(key), OnOffType.OFF);
                // printInfo(eventNotificationAlert);
            }
        } else {
            hikvisionHandler.ensureOff();
        }
        activeEventMap.clear();
    }

    private ChannelUID getChannelUID(String key) {
        Channel channel = hikvisionHandler.getThing().getChannel(key);
        if (channel != null) {
            return channel.getUID();
        }
        logger.warn("No channel found for key {}", key);
        return null;
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (EVENT_NOTIFICATION_ALERT.equals(qName)) {
            processingEvent = false;
        }
    }

    public void printInfo(EventNotificationAlert event) {
        Set<String> keySet = event.tagToValMap.keySet();
        logger.debug("Event type: " + " -- " + event.getEventType());
        logger.debug("Start count: " + " -- " + event.getInitialPostCount());
        logger.debug("End count: " + " -- " + event.getCurrentPostCount());
        logger.debug("Start time: " + " -- " + event.getStartTime());
        logger.debug("End time: " + " -- " + event.getEndTime());
        Iterator<String> iterator = keySet.iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            logger.debug(key + " -- " + String.valueOf(event.getPropertyValueAsString(key)));
        }
    }

    public void saveImage(final String imageUrl, final String destinationFile) throws IOException {
        // threadPool.execute(new SnapshotRunnable(imageUrl, destinationFile));
    }

}