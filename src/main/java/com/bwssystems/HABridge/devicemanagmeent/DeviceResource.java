package com.bwssystems.HABridge.devicemanagmeent;

import static spark.Spark.get;
import static spark.Spark.options;
import static spark.Spark.post;
import static spark.Spark.put;
import static spark.Spark.delete;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bwssystems.HABridge.BridgeSettings;
import com.bwssystems.HABridge.JsonTransformer;
import com.bwssystems.HABridge.Version;
import com.bwssystems.HABridge.dao.DeviceDescriptor;
import com.bwssystems.HABridge.dao.DeviceRepository;
import com.bwssystems.harmony.HarmonyHome;
import com.bwssystems.luupRequests.Sdata;
import com.bwssystems.vera.VeraInfo;
import com.google.gson.Gson;

/**
	spark core server for bridge configuration
 */
public class DeviceResource {
    private static final String API_CONTEXT = "/api/devices";
    private static final Logger log = LoggerFactory.getLogger(DeviceResource.class);

    private DeviceRepository deviceRepository;
    private VeraInfo veraInfo;
    private Version version;
    private HarmonyHome myHarmonyHome;
    private static final Set<String> supportedVerbs = new HashSet<>(Arrays.asList("get", "put", "post"));

	public DeviceResource(BridgeSettings theSettings, Version theVersion, HarmonyHome theHarmonyHome) {
		this.deviceRepository = new DeviceRepository(theSettings.getUpnpDeviceDb());
		this.veraInfo = new VeraInfo(theSettings.getVeraAddress(), theSettings.isValidVera());
		this.myHarmonyHome = theHarmonyHome;
		this.version = theVersion;
        setupEndpoints();
	}

	public DeviceRepository getDeviceRepository() {
		return deviceRepository;
	}

    private void setupEndpoints() {
    	log.info("HABridge device management service started.... ");
	    // http://ip_address:port/api/devices CORS request
	    options(API_CONTEXT, "application/json", (request, response) -> {
	        response.status(HttpStatus.SC_OK);
	        response.header("Access-Control-Allow-Origin", request.headers("Origin"));
	        response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
	        response.header("Access-Control-Allow-Headers", request.headers("Access-Control-Request-Headers"));
	        response.header("Content-Type", "text/html; charset=utf-8");
	    	return "";
	    });
    	post(API_CONTEXT, "application/json", (request, response) -> {
	    	log.debug("Create a Device - request body: " + request.body());
    		DeviceDescriptor device = new Gson().fromJson(request.body(), DeviceDescriptor.class);
	    	if(device.getContentBody() != null ) {
	            if (device.getContentType() == null || device.getHttpVerb() == null || !supportedVerbs.contains(device.getHttpVerb().toLowerCase())) {
	            	device = null;
	            	response.status(HttpStatus.SC_BAD_REQUEST);
					log.debug("Bad http verb in create a Device: " + request.body());
					return device;
	            }
	        }

	    	deviceRepository.save(device);
			log.debug("Created a Device: " + request.body());

	        response.header("Access-Control-Allow-Origin", request.headers("Origin"));
			response.status(HttpStatus.SC_CREATED);

            return device;
	    }, new JsonTransformer());

	    // http://ip_address:port/api/devices/:id CORS request
	    options(API_CONTEXT + "/:id", "application/json", (request, response) -> {
	        response.status(HttpStatus.SC_OK);
	        response.header("Access-Control-Allow-Origin", request.headers("Origin"));
	        response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
	        response.header("Access-Control-Allow-Headers", request.headers("Access-Control-Request-Headers"));
	        response.header("Content-Type", "text/html; charset=utf-8");
	    	return "";
	    });
    	put (API_CONTEXT + "/:id", "application/json", (request, response) -> {
	    	log.debug("Edit a Device - request body: " + request.body());
        	DeviceDescriptor device = new Gson().fromJson(request.body(), DeviceDescriptor.class);
	        DeviceDescriptor deviceEntry = deviceRepository.findOne(request.params(":id"));
	        if(deviceEntry == null){
		    	log.debug("Could not save an edited Device Id: " + request.params(":id"));
		    	response.status(HttpStatus.SC_BAD_REQUEST);
	        }
	        else
	        {
				log.debug("Saving an edited Device: " + deviceEntry.getName());

				deviceEntry.setName(device.getName());
				if (device.getDeviceType() != null)
					deviceEntry.setDeviceType(device.getDeviceType());
				deviceEntry.setMapId(device.getMapId());
				deviceEntry.setMapType(device.getMapType());
				deviceEntry.setTargetDevice(device.getTargetDevice());
				deviceEntry.setOnUrl(device.getOnUrl());
				deviceEntry.setOffUrl(device.getOffUrl());
				deviceEntry.setHttpVerb(device.getHttpVerb());
				deviceEntry.setContentType(device.getContentType());
				deviceEntry.setContentBody(device.getContentBody());
				deviceEntry.setContentBodyOff(device.getContentBodyOff());

				deviceRepository.save(deviceEntry);
				response.status(HttpStatus.SC_OK);
	        }
	        return deviceEntry;
    	}, new JsonTransformer());

    	get (API_CONTEXT, "application/json", (request, response) -> {
    		List<DeviceDescriptor> deviceList = deviceRepository.findAll();
	    	log.debug("Get all devices");
	    	JsonTransformer aRenderer = new JsonTransformer();
	    	String theStream = aRenderer.render(deviceList);
	    	log.debug("The Device List: " + theStream);
			response.status(HttpStatus.SC_OK);
    		return deviceList;
    	}, new JsonTransformer());

    	get (API_CONTEXT + "/:id", "application/json", (request, response) -> {
	    	log.debug("Get a device");
	        DeviceDescriptor descriptor = deviceRepository.findOne(request.params(":id"));
	        if(descriptor == null)
				response.status(HttpStatus.SC_NOT_FOUND);
	        else
	        	response.status(HttpStatus.SC_OK);
	        return descriptor;
	    }, new JsonTransformer());

    	delete (API_CONTEXT + "/:id", "application/json", (request, response) -> {
    		String anId = request.params(":id");
	    	log.debug("Delete a device: " + anId);
	        DeviceDescriptor deleted = deviceRepository.findOne(anId);
	        if(deleted == null)
				response.status(HttpStatus.SC_NOT_FOUND);
	        else
	        {
	        	deviceRepository.delete(deleted);
				response.status(HttpStatus.SC_OK);
	        }
	        return null;
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/habridge/version", "application/json", (request, response) -> {
	    	log.debug("Get HA Bridge version: v" + version.getVersion());
			response.status(HttpStatus.SC_OK);
	        return "{\"version\":\"" + version.getVersion() + "\"}";
	    });

    	get (API_CONTEXT + "/vera/devices", "application/json", (request, response) -> {
	    	log.debug("Get vera devices");
	        Sdata sData = veraInfo.getSdata();
	        if(sData == null){
				response.status(HttpStatus.SC_NOT_FOUND);
				return null;
	        }

	      	response.status(HttpStatus.SC_OK);
	        return sData.getDevices();
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/vera/scenes", "application/json", (request, response) -> {
	    	log.debug("Get vera scenes");
	        Sdata sData = veraInfo.getSdata();
	        if(sData == null){
				response.status(HttpStatus.SC_NOT_FOUND);
	            return null;
	        }
	      	response.status(HttpStatus.SC_OK);
	        return sData.getScenes();
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/harmony/activities", "application/json", (request, response) -> {
	    	log.debug("Get harmony activities");
	      	if(myHarmonyHome == null) {
				response.status(HttpStatus.SC_NOT_FOUND);
		      	return null;	      		
	      	}
	      	response.status(HttpStatus.SC_OK);
	      	return myHarmonyHome.getActivities();
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/harmony/show", "application/json", (request, response) -> {
	    	log.debug("Get harmony current activity");
	      	if(myHarmonyHome == null) {
	      		response.status(HttpStatus.SC_NOT_FOUND);
	      		return null;
	      	}
	      	response.status(HttpStatus.SC_OK);
      		return myHarmonyHome.getCurrentActivities();
	    }, new JsonTransformer());

    	get (API_CONTEXT + "/harmony/devices", "application/json", (request, response) -> {
	    	log.debug("Get harmony devices");
	      	if(myHarmonyHome == null) {
				response.status(HttpStatus.SC_NOT_FOUND);
		      	return null;	      		
	      	}
	      	response.status(HttpStatus.SC_OK);
	      	return myHarmonyHome.getDevices();
	    }, new JsonTransformer());

    }
}