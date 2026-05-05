package com.ai_bus_fare_system.aibusfare.controllers;

import com.ai_bus_fare_system.aibusfare.model.PaymentStatus;
import com.ai_bus_fare_system.aibusfare.model.Trip;
import com.ai_bus_fare_system.aibusfare.repository.TripRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private HttpSession session;

    @GetMapping("/history")
    public Map<String, Object> history() {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.isBlank()) {
            return Map.of("success", false, "message", "User not logged in.", "trips", List.of());
        }

        return Map.of(
                "success", true,
                "trips", tripRepository.findByUsernameOrderByCreatedAtDesc(loggedInUser)
        );
    }

    @PostMapping("/mark-paid")
    public Map<String, Object> markPaid() {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.isBlank()) {
            return Map.of("success", false, "message", "User not logged in.");
        }

        Trip trip = resolveCurrentTrip(loggedInUser);
        if (trip == null) {
            return Map.of("success", false, "message", "No trip found for this user.");
        }

        trip.setPaymentStatus(PaymentStatus.PAID);
        trip.setPaidAt(LocalDateTime.now());
        tripRepository.save(trip);
        session.setAttribute("currentTripId", trip.getId());

        return Map.of("success", true, "message", "Trip marked as PAID.");
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
