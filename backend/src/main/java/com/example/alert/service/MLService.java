package com.example.alert.service;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Minimal ML service for demo fall-detection backend.
 * - Attempts to load a simple JSON logistic regression model from classpath: model/fall_model.bin
 * - Falls back to loading placeholder TFLite bytes from model/fall_model.tflite
 * - If no model is found, uses a simple heuristic stub for demonstration.
 */
@Service
public class MLService {

    private byte[] modelBytes;
    private double[] lrWeights; // [w_accel_mag, w_gyro_mag]
    private double lrBias;
    private double lrThreshold = 0.6;

    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void load() {
        try {
            // Prefer JSON logistic regression model if present
            Resource jsonRes = new ClassPathResource("model/fall_model.bin");
            if (jsonRes.exists()) {
                try (InputStream is = jsonRes.getInputStream()) {
                    parseJsonModel(is);
                    System.out.println("[ML] Loaded logistic regression model from fall_model.bin");
                }
                return;
            }

            // Else, load TFLite placeholder bytes (not used for real inference in this demo)
            Resource res = new ClassPathResource("model/fall_model.tflite");
            if (res.exists()) {
                try (InputStream is = res.getInputStream()) {
                    modelBytes = is.readAllBytes();
                    System.out.println("[ML] Loaded prebuilt fall_model.tflite (" + modelBytes.length + " bytes)");
                }
            } else {
                System.out.println("[ML] Model not found; using stub predictions");
            }
        } catch (IOException e) {
            System.out.println("[ML] Failed to load model: " + e.getMessage());
        }
    }

    /**
     * Very simple stub / fallback: returns score in [0,1] based on accelerometer and gyro magnitudes.
     *
     * @param ax accelerometer x
     * @param ay accelerometer y
     * @param az accelerometer z
     * @param gx gyroscope x
     * @param gy gyroscope y
     * @param gz gyroscope z
     * @return probability-like score in [0,1]
     */
    public double predictFallProbability(double ax, double ay, double az, double gx, double gy, double gz) {
        double accelMag = Math.sqrt(ax * ax + ay * ay + az * az);
        double gyroMag = Math.sqrt(gx * gx + gy * gy + gz * gz);

        if (lrWeights != null && lrWeights.length == 2) {
            double z = lrWeights[0] * accelMag + lrWeights[1] * gyroMag + lrBias;
            double prob = 1.0 / (1.0 + Math.exp(-z));
            return prob;
        }

        // fallback heuristic
        double raw = (Math.max(0, accelMag - 12.0) / 15.0) + (Math.min(gyroMag, 300.0) / 300.0) * 0.5;
        double score = Math.max(0.0, Math.min(1.0, raw));
        return score;
    }

    /**
     * Very small ad-hoc JSON parsing to extract weights, bias, and threshold.
     * Expected JSON shape:
     * {"type":"logistic_regression","weights":[w1,w2],"bias":b,"threshold":t}
     *
     * This avoids adding a JSON library for the demo. Replace with a proper JSON parser (Jackson/Gson) for production.
     */
    private void parseJsonModel(InputStream is) throws IOException {
        JsonNode root = mapper.readTree(is);
        JsonNode w = root.get("weights");
        if (w != null && w.isArray() && w.size() >= 2) {
            lrWeights = new double[] { w.get(0).asDouble(), w.get(1).asDouble() };
        }
        JsonNode b = root.get("bias");
        if (b != null && b.isNumber()) {
            lrBias = b.asDouble();
        }
        JsonNode t = root.get("threshold");
        if (t != null && t.isNumber()) {
            lrThreshold = t.asDouble();
        }
    }
}
