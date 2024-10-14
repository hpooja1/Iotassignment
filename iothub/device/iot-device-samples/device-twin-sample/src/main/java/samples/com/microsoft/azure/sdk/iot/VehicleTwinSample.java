package samples.com.microsoft.azure.sdk.iot;

import com.microsoft.azure.sdk.iot.device.*;
import com.microsoft.azure.sdk.iot.device.twin.*;
import com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VehicleTwinSample {
    private static Twin twin;
    private static final String TRAFFIC_LIGHT_STATUS = "TrafficLightStatus"; // Desired property key
    private static final String INTERSECTIONS = "IntersectionDetails"; // Reported property key
    private static final String VEHICLE_GPS = "VehicleGPS"; // Reported property key for vehicle GPS data

    // Enum to manage traffic light states
    private enum LIGHTS { GREEN,RED }

    // Desired properties update callback
    private static class DesiredPropertiesUpdatedHandler implements DesiredPropertiesCallback {
        @Override
        public void onDesiredPropertiesUpdated(Twin desiredPropertyUpdateTwin, Object context) {
            if (twin == null) {
                System.out.println("Received desired properties update before getting current twin. Ignoring this update.");
                return;
            }
            twin.getDesiredProperties().putAll(desiredPropertyUpdateTwin.getDesiredProperties());
            twin.getDesiredProperties().setVersion(desiredPropertyUpdateTwin.getDesiredProperties().getVersion());
            System.out.println("Received desired property update. Current twin:");
            System.out.println(twin);

            // Check if traffic light status is RED
            String trafficLightStatus = (String) twin.getDesiredProperties().get(TRAFFIC_LIGHT_STATUS);
            if (LIGHTS.RED.name().equals(trafficLightStatus)) {
                // Stop both vehicles when traffic light is red
                stopVehicleTwin("vehicleId1", context);
                stopVehicleTwin("vehicleId2", context);
            }
        }
    }

    // Send Cloud-to-Device message to stop a vehicle
    public static void stopVehicleC2D(String vehicleId, DeviceClient client) {
        try {
            Message messageToDevice = new Message("StopVehicle");
            messageToDevice.setMessageId(UUID.randomUUID().toString());
            messageToDevice.setExpiryTime(5000); // message expiry time
            client.sendEventAsync(messageToDevice, null, null); // Send message to the vehicle device
            System.out.println("Message to stop vehicle sent via C2D.");
        } catch (Exception e) {
            System.out.println("Failed to send stop message: " + e.getMessage());
        }
    }

    // Connection status change logger
    protected static class IotHubConnectionStatusChangeCallbackLogger implements IotHubConnectionStatusChangeCallback {
        @Override
        public void onStatusChanged(ConnectionStatusChangeContext connectionStatusChangeContext) {
            IotHubConnectionStatus status = connectionStatusChangeContext.getNewStatus();
            System.out.println("CONNECTION STATUS UPDATE: " + status);
        }
    }

    // Use Twin to stop the vehicle by updating the twin's properties
    public static void stopVehicleTwin(String vehicleId, Object context) {
        DeviceClient client = (DeviceClient) context;
        try {
            TwinCollection reportedProperties = new TwinCollection();
            reportedProperties.put("VehicleStatus", "STOPPED");
            client.updateReportedProperties(reportedProperties);
            System.out.println("Vehicle stop request sent via device twin.");
        } catch (Exception e) {
            System.out.println("Failed to update twin properties: " + e.getMessage());
        }
    }

    // Generate random latitude and longitude
    public static double getRandomLatitude() {
        double minLat = 33.0;  // Minimum latitude
        double maxLat = 45.0;  // Maximum latitude
        return minLat + (maxLat - minLat) * new Random().nextDouble();
    }

    public static double getRandomLongitude() {
        double minLon = -116.0; // Minimum longitude
        double maxLon = -89.0;  // Maximum longitude
        return minLon + (maxLon - minLon) * new Random().nextDouble();
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        // Device connection string (replace with your own)
        if (args.length == 0) {
            System.out.println("Please provide the IoT Hub connection string as an argument.");
            return;
        }

        // Device connection string
        String connString = args[0];
        DeviceClient client = new DeviceClient(connString, IotHubClientProtocol.MQTT);
        client.setConnectionStatusChangeCallback(new IotHubConnectionStatusChangeCallbackLogger(), null);
        try {
            System.out.println("Opening connection to IoT hub");
            client.open(true);

            System.out.println("Subscribing to desired properties");
            client.subscribeToDesiredProperties(new DesiredPropertiesUpdatedHandler(), client);

            System.out.println("Getting current twin");
            twin = client.getTwin();
            System.out.println("Received current twin: " + twin);

            // Report number of intersections and ways
            TwinCollection intersectionDetails = new TwinCollection();
            intersectionDetails.put("Intersection1", new HashMap<String, Object>() {{
                put("NumberOfWays", 4);
                put("NumberOfVehicles", 2);
            }});
            intersectionDetails.put("Intersection2", new HashMap<String, Object>() {{
                put("NumberOfWays", 3);
                put("NumberOfVehicles", 1);
            }});

            TwinCollection reportedProperties = twin.getReportedProperties();
            reportedProperties.put(INTERSECTIONS, intersectionDetails);

            // Simulating vehicle GPS data
            List<Map<String, Double>> vehicleGpsData = new ArrayList<>();
            for (int i = 0; i < 2; i++) { // Simulating 2 vehicles
                Map<String, Double> vehicleData = new HashMap<>();
                vehicleData.put("Latitude", getRandomLatitude());
                vehicleData.put("Longitude", getRandomLongitude());
                vehicleData.put("Speed", Math.random() * 100); // Random speed between 0-100 km/h
                vehicleGpsData.add(vehicleData);
            }
            reportedProperties.put(VEHICLE_GPS, vehicleGpsData);
            client.updateReportedProperties(reportedProperties);
            System.out.println("Successfully updated reported properties for intersections and vehicle GPS data.");

        } catch (Exception e) {
            System.out.println("On exception, shutting down \n" + e.getMessage());
        } finally {
            client.close();
            System.out.println("Shutting down...");
        }

        System.out.println("Press any key to exit...");
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8.name());
        scanner.nextLine();
    }
}
