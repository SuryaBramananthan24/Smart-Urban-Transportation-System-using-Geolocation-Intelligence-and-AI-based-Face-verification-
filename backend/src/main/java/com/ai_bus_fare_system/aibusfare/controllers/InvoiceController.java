package com.ai_bus_fare_system.aibusfare.controllers;

import com.ai_bus_fare_system.aibusfare.model.PaymentStatus;
import com.ai_bus_fare_system.aibusfare.model.Trip;
import com.ai_bus_fare_system.aibusfare.model.User;
import com.ai_bus_fare_system.aibusfare.repository.TripRepository;
import com.ai_bus_fare_system.aibusfare.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/invoice")
public class InvoiceController {

    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private HttpSession session;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/send")
    public Map<String, Object> sendInvoice() {

        // 1. Get logged-in user from session
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return Map.of("success", false, "message", "User not logged in.");
        }

        // 2. Fetch email from H2 DB
        Optional<User> userOpt = userRepository.findByUsername(loggedInUser);
        if (userOpt.isEmpty()) {
            return Map.of("success", false, "message", "User not found.");
        }
        User user = userOpt.get();

        // 3. Pull trip data from session
        Object roadKmObj = session.getAttribute("road_km");
        Object fareObj   = session.getAttribute("fare_amount");
        Object lat1      = session.getAttribute("start_lat");
        Object lon1      = session.getAttribute("start_lon");
        Object lat2      = session.getAttribute("end_lat");
        Object lon2      = session.getAttribute("end_lon");

        if (roadKmObj == null || fareObj == null) {
            return Map.of("success", false, "message", "Trip data missing. Complete a trip first.");
        }

        double roadKm = ((Number) roadKmObj).doubleValue();
        double fare   = ((Number) fareObj).doubleValue();

        // 4. Build the exact JSON your email server expects
        Map<String, Object> invoicePayload = Map.of(
                "name",              user.getUsername(),
                "email",             user.getEmail(),
                "pickupLocation",    lat1 + ", " + lon1,
                "dropLocation",      lat2 + ", " + lon2,
                "distanceTravelled", roadKm,
                "items", List.of(Map.of(
                        "itemName", "Bus Ticket",
                        "quantity", 1,
                        "price",    fare
                ))
        );

        // 5. POST to external email server on port 8080
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "http://localhost:8080/api/invoice",
                    invoicePayload,
                    String.class
            );

            Trip tripToUpdate = resolveCurrentTrip(loggedInUser);
            if (tripToUpdate != null) {
                tripToUpdate.setPaymentStatus(PaymentStatus.PAY_LATER);
                tripToUpdate.setInvoiceSentAt(LocalDateTime.now());
                tripRepository.save(tripToUpdate);
                session.setAttribute("currentTripId", tripToUpdate.getId());
            }

            return Map.of("success", true, "message", "Invoice sent to " + user.getEmail());
        } catch (Exception e) {
            return Map.of("success", false, "message", "Email server error: " + e.getMessage());
        }
    }

    private Trip resolveCurrentTrip(String loggedInUser) {
        Object tripIdObj = session.getAttribute("currentTripId");
        if (tripIdObj instanceof Number tripIdNum) {
            Long tripId = tripIdNum.longValue();
            Optional<Trip> byId = tripRepository.findById(tripId);
            if (byId.isPresent() && loggedInUser.equals(byId.get().getUsername())) {
                return byId.get();
            }
        }

        return tripRepository.findTopByUsernameOrderByCreatedAtDesc(loggedInUser).orElse(null);
    }
}
