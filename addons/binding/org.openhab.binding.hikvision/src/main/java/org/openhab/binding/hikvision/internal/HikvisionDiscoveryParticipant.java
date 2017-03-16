package org.openhab.binding.hikvision.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.UpnpDiscoveryParticipant;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.jupnp.model.meta.DeviceDetails;
import org.jupnp.model.meta.ManufacturerDetails;
import org.jupnp.model.meta.RemoteDevice;
import org.openhab.binding.hikvision.HikvisionBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HikvisionDiscoveryParticipant /* extends AbstractDiscoveryService */ implements UpnpDiscoveryParticipant {

    private Logger logger = LoggerFactory.getLogger(HikvisionDiscoveryParticipant.class);

    // public HikvisionDiscoveryParticipant() throws IllegalArgumentException {
    // super(1500);
    // }
    //
    // @Override
    // protected void startScan() {
    // // TODO Auto-generated method stub
    // System.out.println("Start scan");
    // }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Collections.singleton(HikvisionBindingConstants.THING_TYPE_HIKVISION);
    }

    @Override
    public DiscoveryResult createResult(RemoteDevice device) {
        logger.trace("Checking device: {}", device.getDisplayString());
        ThingUID uid = getThingUID(device);
        if (uid != null) {
            Map<String, Object> properties = new HashMap<>(2);
            properties.put(HikvisionBindingConstants.IP_ADDRESS, device.getDetails().getPresentationURI().getHost());
            properties.put(HikvisionBindingConstants.SERIAL_NUMBER, device.getDetails().getSerialNumber());
            logger.trace("Friendly name: {}", device.getDetails().getFriendlyName());
            DiscoveryResult result = DiscoveryResultBuilder.create(uid).withProperties(properties)
                    .withLabel(device.getDetails().getFriendlyName())
                    .withRepresentationProperty(HikvisionBindingConstants.SERIAL_NUMBER).build();
            return result;
        } else {
            return null;
        }
    }

    @Override
    public ThingUID getThingUID(RemoteDevice device) {
        DeviceDetails details = device.getDetails();
        if (details != null) {
            ManufacturerDetails modelDetails = details.getManufacturerDetails();
            if (modelDetails != null) {
                String modelName = modelDetails.getManufacturer();
                if (modelName != null) {
                    if (modelName.startsWith("HIKVISION")) {
                        return new ThingUID(HikvisionBindingConstants.THING_TYPE_HIKVISION, details.getSerialNumber());
                    }
                }
            }
        }
        return null;
    }

}
