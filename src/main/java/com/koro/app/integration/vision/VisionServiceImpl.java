package com.koro.app.integration.vision;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.Locale;
import java.util.Random;

@Service
public class VisionServiceImpl implements VisionService {

    @Value("${app.vision.mock-enabled:true}")
    private boolean mockEnabled;

    private final Random random = new Random();
    private final String[] mockLabels = {"Tree", "Water", "Bird", "Fish", "Dog", "House", "Chair", "Table", "Flower", "River"};

    @Override
    public VisionResult detectLabel(MultipartFile file) {
        if (mockEnabled) {
            String originalName = file.getOriginalFilename();
            if (originalName != null) {
                String nameLower = originalName.toLowerCase(Locale.ROOT);
                for (String label : mockLabels) {
                    if (nameLower.contains(label.toLowerCase(Locale.ROOT))) {
                        return new VisionResult(label, 0.92 + random.nextDouble() * 0.07);
                    }
                }
            }
            // default to a random standard label
            String randomLabel = mockLabels[random.nextInt(mockLabels.length)];
            return new VisionResult(randomLabel, 0.80 + random.nextDouble() * 0.18);
        } else {
            // Actual implementation would make a call to Google Cloud Vision API
            // using RestClient or the official Google Cloud Vision client library.
            // For now we throw an exception or return a basic result.
            throw new UnsupportedOperationException("Google Cloud Vision actual integration is disabled. Enable mock-enabled=true for local development.");
        }
    }
}
