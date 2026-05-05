package com.ai_bus_fare_system.aibusfare.service;

import com.ai_bus_fare_system.aibusfare.model.User;
import com.ai_bus_fare_system.aibusfare.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String register(String username, String email, String rawPassword) {
        if (userRepository.existsByUsername(username)) return "USERNAME_TAKEN";
        if (userRepository.existsByEmail(email))       return "EMAIL_TAKEN";

        User user = new User(username, email, encoder.encode(rawPassword));
        userRepository.save(user);
        return "OK";
    }

    public boolean login(String username, String rawPassword) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return false;
        return encoder.matches(rawPassword, userOpt.get().getPassword());
    }
}
