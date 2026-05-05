package com.ai_bus_fare_system.aibusfare.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;

@Service
public class FaceService {

    private final RestTemplate rest = new RestTemplate();

    public Map detectFace(String imageBase64) {
        String url = "http://localhost:5000/detect";

        Map<String,String> req = Map.of("image", imageBase64);

        return rest.postForObject(url, req, Map.class);
    }

    public boolean compareFaces(List<Double> e1, List<Double> e2) {
        String url = "http://localhost:5000/compare";

        Map<String,List> req = Map.of("e1", e1, "e2", e2);

        Map resp = rest.postForObject(url, req, Map.class);

        return (Boolean) resp.get("match");
    }
}