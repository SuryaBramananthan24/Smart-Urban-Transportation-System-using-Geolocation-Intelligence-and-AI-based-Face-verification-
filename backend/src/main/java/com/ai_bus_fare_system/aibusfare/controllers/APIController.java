package com.ai_bus_fare_system.aibusfare.controllers;

import com.ai_bus_fare_system.aibusfare.model.PaymentStatus;
import com.ai_bus_fare_system.aibusfare.model.Trip;
import com.ai_bus_fare_system.aibusfare.repository.TripRepository;
import jakarta.servlet.http.HttpSession;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import com.ai_bus_fare_system.aibusfare.service.FaceService;
import com.ai_bus_fare_system.aibusfare.service.OSRMRoutingService;

import java.util.List;
import java.util.Map;

@Controller
public class APIController {

    @Autowired
    private FaceService faceService;

    @Autowired
    private OSRMRoutingService routingService;

    @Autowired
    private HttpSession session;

    @Autowired
    private TripRepository tripRepository;

    @Value("${payment.upi.id:test@upi}")
    private String upiId;

    @Value("${payment.upi.name:AI Bus Fare}")
    private String upiPayeeName;

    // ------------------- BASIC PAGES -------------------

    @GetMapping("/")
    public String Home(){ return "Home"; }

    @GetMapping("/login")
    public String Login(){ return "Login"; }

    @GetMapping("/signup")
    public String Signup(){ return "Signup"; }

    @GetMapping("/fd")
    public String FD(){ return "Face_Detection"; }

    @GetMapping("/fc")
    public String FC(){ return "Fare_Calculation"; }

    @GetMapping("/pay")
    public String Pay(){ return "Payment"; }

    @GetMapping("/dm")
    public String DM(){ return "Distance_map"; }

    @GetMapping("/history")
    public String history(){ return "History"; }


    // ------------------- HELPER: HAVERSINE -------------------

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0; // Earth radius in KM
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // Convenience: safe number extraction from request body (avoids ClassCast issues)
    private Double getDouble(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s && !s.isBlank()) return Double.parseDouble(s);
        return null;
    }

    // ------------------- STEP 1 & STEP 2 : Upload Face + Location -------------------

    @PostMapping("/fd/upload")
    @ResponseBody
    public Map<String, Object> uploadImage(@RequestBody Map<String, Object> body) {

        String base64Image = (String) body.get("image");
        String timestamp = (String) body.get("timestamp");

        // Robust parsing for numbers coming from JSON
        Double latitude = getDouble(body, "latitude");
        Double longitude = getDouble(body, "longitude");

        Map response = faceService.detectFace(base64Image);

        Map<String, Object> result = Map.of(
                "face_image", response.get("face_image"),
                "timestamp", timestamp,
                "latitude", latitude,
                "longitude", longitude
        );

        if (session.getAttribute("embedding1") == null) {

            session.setAttribute("embedding1", response.get("embedding"));
            session.setAttribute("face1", response.get("face_image"));
            session.setAttribute("timestamp1", timestamp);
            session.setAttribute("lat1", latitude);
            session.setAttribute("lon1", longitude);

            return Map.of("step", 1, "data", result);

        } else {
            session.setAttribute("embedding2", response.get("embedding"));
            session.setAttribute("face2", response.get("face_image"));
            session.setAttribute("timestamp2", timestamp);
            session.setAttribute("lat2", latitude);
            session.setAttribute("lon2", longitude);

            return Map.of("step", 2, "data", result);
        }
    }

    // ------------------- STEP 3 : Compare Faces + Calculate Distances + Fare -------------------

    @PostMapping("/fd/compare")
    @ResponseBody
    public Map<String, Object> compareFaces() {

        List<Double> e1 = (List<Double>) session.getAttribute("embedding1");
        List<Double> e2 = (List<Double>) session.getAttribute("embedding2");

        if (e1 == null || e2 == null) {
            return Map.of("error", "Both faces are not captured");
        }

        boolean match = faceService.compareFaces(e1, e2);

        if (!match) {
            return Map.of("match", false);
        }

        Double lat1 = (Double) session.getAttribute("lat1");
        Double lon1 = (Double) session.getAttribute("lon1");
        Double lat2 = (Double) session.getAttribute("lat2");
        Double lon2 = (Double) session.getAttribute("lon2");

        try {
            // ROAD DISTANCE (via OSRM public API through OSRMRoutingService)
            OSRMRoutingService.RouteResult rr = routingService.route(lat1, lon1, lat2, lon2);

            // STRAIGHT DISTANCE (Haversine)
            double straightKm = haversine(lat1, lon1, lat2, lon2);

            // ------------------- FARE RULE (UPDATED) -------------------
            // If road distance < 1 km -> flat ₹30
            // Else -> base ₹5 + ₹3 per km
            double fare;
            if (rr.distanceKm < 1.0) {
                fare = 30.0;
            } else {
                double baseFare = 5.0;
                double perKm = 3.0;
                fare = baseFare + (rr.distanceKm * perKm);
            }
            // -----------------------------------------------------------

            // SAVE TO SESSION (for DM + Payment pages)
            session.setAttribute("straight_km", straightKm);
            session.setAttribute("road_km", rr.distanceKm);
            session.setAttribute("fare_amount", fare);
            session.setAttribute("duration_sec", rr.durationSec);
            session.setAttribute("route_geojson", rr.geometryGeoJson);

            session.setAttribute("start_lat", lat1);
            session.setAttribute("start_lon", lon1);
            session.setAttribute("end_lat", lat2);
            session.setAttribute("end_lon", lon2);

            String loggedInUser = (String) session.getAttribute("loggedInUser");
            if (loggedInUser != null && !loggedInUser.isBlank()) {
                Trip trip = new Trip();
                trip.setUsername(loggedInUser);
                trip.setStartLat(lat1);
                trip.setStartLon(lon1);
                trip.setEndLat(lat2);
                trip.setEndLon(lon2);
                trip.setRoadKm(rr.distanceKm);
                trip.setFareAmount(fare);
                trip.setPaymentStatus(PaymentStatus.PENDING);
                trip.setCreatedAt(java.time.LocalDateTime.now());
                Trip savedTrip = tripRepository.save(trip);
                session.setAttribute("currentTripId", savedTrip.getId());
            }

            return Map.of(
                    "match", true,
                    "straight_km", straightKm,
                    "road_km", rr.distanceKm,
                    "fare", fare
            );

        } catch (Exception ex) {
            return Map.of("error", "Routing failed: " + ex.getMessage());
        }
    }

    // ------------------- PROVIDE ROUTE FOR DISTANCE MAP PAGE -------------------

    @GetMapping("/route/session")
    @ResponseBody
    public Map<String, Object> getSessionRoute() {

        return Map.of(
                "straight_km", session.getAttribute("straight_km"),
                "road_km", session.getAttribute("road_km"),
                "fare", session.getAttribute("fare_amount"),
                "duration_sec", session.getAttribute("duration_sec"),
                "route_geojson", session.getAttribute("route_geojson"),
                "start_lat", session.getAttribute("start_lat"),
                "start_lon", session.getAttribute("start_lon"),
                "end_lat", session.getAttribute("end_lat"),
                "end_lon", session.getAttribute("end_lon")
        );
    }

    // ------------------- GENERATE UPI QR -------------------

    @GetMapping("/api/upi_qr")
    @ResponseBody
    public ResponseEntity<byte[]> generateUpiQr(
            @RequestParam(required = false) String am,
            @RequestParam(defaultValue = "AI Bus Fare") String tn
    ) {
        try {
            BigDecimal amount;
            if (am != null && !am.isBlank()) {
                amount = new BigDecimal(am);
            } else {
                Object fareObj = session.getAttribute("fare_amount");
                if (fareObj == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .contentType(MediaType.TEXT_PLAIN)
                            .body("Fare amount is not available in session".getBytes(StandardCharsets.UTF_8));
                }
                amount = new BigDecimal(fareObj.toString());
            }

            amount = amount.setScale(2, RoundingMode.HALF_UP);

            String upiUri = "upi://pay"
                    + "?pa=" + URLEncoder.encode(upiId, StandardCharsets.UTF_8)
                    + "&pn=" + URLEncoder.encode(upiPayeeName, StandardCharsets.UTF_8)
                    + "&am=" + URLEncoder.encode(amount.toPlainString(), StandardCharsets.UTF_8)
                    + "&cu=INR"
                    + "&tn=" + URLEncoder.encode(tn, StandardCharsets.UTF_8);

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matrix = new MultiFormatWriter().encode(upiUri, BarcodeFormat.QR_CODE, 320, 320, hints);

            ByteArrayOutputStream png = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", png);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                    .body(png.toByteArray());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Failed to generate UPI QR: " + ex.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }
}
