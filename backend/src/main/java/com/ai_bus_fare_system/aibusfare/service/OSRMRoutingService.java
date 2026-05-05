package com.ai_bus_fare_system.aibusfare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class OSRMRoutingService {

    // Public OSRM server (driving). You can change to your own later.
    // Docs: OSRM HTTP API 'route' service. routes[0].distance returns meters.
    // https://project-osrm.org/docs/v5.5.1/api/#route-service   (API)
    // https://routing.openstreetmap.de/routed-car                (public endpoint)
    private static final String BASE = "https://routing.openstreetmap.de/routed-car";

    private final RestTemplate http;
    private final ObjectMapper om = new ObjectMapper();

    public OSRMRoutingService() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(8000);
        this.http = new RestTemplate(f);
    }

    public RouteResult route(double lat1, double lon1, double lat2, double lon2) {
        try {
            // OSRM expects lon,lat format in path; ask for GeoJSON geometry + full overview
            String url = String.format(
                "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                BASE, lon1, lat1, lon2, lat2
            );
            String resp = http.getForObject(url, String.class);
            JsonNode root = om.readTree(resp);

            // Basic checks
            if (root == null || !root.has("routes") || root.get("routes").isEmpty()) {
                throw new RuntimeException("No route found from OSRM");
            }

            JsonNode route0 = root.get("routes").get(0);
            double meters = route0.get("distance").asDouble(); // meters (OSRM docs)
            double seconds = route0.get("duration").asDouble(); // seconds
            // Geometry: GeoJSON LineString object
            JsonNode geometry = route0.get("geometry");

            RouteResult r = new RouteResult();
            r.distanceKm = meters / 1000.0;
            r.durationSec = seconds;
            r.geometryGeoJson = geometry.toString(); // keep as JSON string for frontend
            return r;

        } catch (Exception ex) {
            throw new RuntimeException("OSRM call failed", ex);
        }
    }

    public static class RouteResult {
        public double distanceKm;
        public double durationSec;
        public String geometryGeoJson; // a GeoJSON LineString (stringified)
    }
}