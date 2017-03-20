# Hikvision Binding

This binding creates a direct connection to the event stream of Hikvision IP Cameras.  

## Supported Things

There is exactly one supported thing, the Hikvision IP Camera. It has the id `camera`.

## Thing Configuration

The camera must be configured with the IP address, username, and password.  If discovery is used, the IP address will be set automatically, along with the serial number 
and "Friendly Name" assigned to the camera.

## Channels

The event stream connection will monitor the camera for certain events, available through the following channels:

| Channel Type ID | Item Type    | Description  |
|-----------------|------------------------|------------- |
| VMD             | Switch       | Detects whether motion has been detected |
| linedetection   | Switch       | Detects if line crossing has been detected |
| fielddetection  | Switch       | Detects if field detection/intrusion has been detected |
| videoloss       | Switch       | Detects a loss in the video signal |
| snapshot        | Image        | Can be used to grab a current snapshot for the camera, with an optional refresh interval in seconds |


## Full Example

demo.things:

```
hikvision:camera:frontyard [ username="admin",password="12345",ipAddress="192.168.1.123" ]
```

demo.items:

```
Switch FrontYardMotionDetection  "Front Yard Motion Detected" { channel="hikvision:camera:frontyard:VMD" }
```

demo.sitemap:

```
sitemap demo label="Main Menu"
{
    Frame {
        Switch item=FrontYardMotionDetection
    }
}
```
