package com.koro.app.image.controller;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.image.dto.ImageRecognitionResponse;
import com.koro.app.image.entity.ImageRecognitionResult;
import com.koro.app.image.repository.ImageRecognitionResultRepository;
import com.koro.app.integration.storage.StorageService;
import com.koro.app.integration.vision.VisionResult;
import com.koro.app.integration.vision.VisionService;
import com.koro.app.translation.dto.TranslationResponse;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.TranslationRepository;
import com.koro.app.user.entity.User;
import com.koro.app.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/images")
public class ImageController {

    @Autowired
    private StorageService storageService;

    @Autowired
    private VisionService visionService;

    @Autowired
    private ImageRecognitionResultRepository resultRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private TranslationRepository translationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityLogService activityLogService;

    @PostMapping("/recognize")
    public ResponseEntity<?> recognizeImage(@RequestParam("file") MultipartFile file) {
        User user = getCurrentUser();
        
        // 1. Save uploaded image to storage
        String filename = storageService.store(file);
        String fileUrl = "/api/v1/images/files/" + filename; // URL pattern to access file

        // 2. Perform object detection / image recognition
        VisionResult visionResult = visionService.detectLabel(file);
        
        // 3. Match label to a Concept in our database (case-insensitive)
        Optional<Concept> conceptOpt = conceptRepository.findByNameIgnoreCase(visionResult.getLabel());
        
        // 4. Save result
        ImageRecognitionResult res = ImageRecognitionResult.builder()
                .user(user)
                .imageUrl(fileUrl)
                .detectedLabel(visionResult.getLabel())
                .confidence(visionResult.getConfidence())
                .concept(conceptOpt.orElse(null))
                .build();
        
        ImageRecognitionResult savedRes = resultRepository.save(res);

        // 5. Gather translations if concept matched
        List<TranslationResponse> translations = new ArrayList<>();
        if (conceptOpt.isPresent()) {
            List<Translation> list = translationRepository.findByConceptId(conceptOpt.get().getId());
            translations = list.stream()
                    .map(TranslationResponse::fromTranslation)
                    .collect(Collectors.toList());
        }

        // 6. Log user activity
        activityLogService.log(
                ActivityType.IMAGE_RECOGNITION, 
                "Ran image recognition: detected label '" + visionResult.getLabel() + "'", 
                savedRes.getId(), 
                "confidence=" + visionResult.getConfidence()
        );

        return ResponseEntity.ok(ImageRecognitionResponse.build(savedRes, translations));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ImageRecognitionResponse>> getRecognitionHistory() {
        User user = getCurrentUser();
        List<ImageRecognitionResult> list = resultRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        
        List<ImageRecognitionResponse> responses = list.stream().map(res -> {
            List<TranslationResponse> translations = new ArrayList<>();
            if (res.getConcept() != null) {
                translations = translationRepository.findByConceptId(res.getConcept().getId()).stream()
                        .map(TranslationResponse::fromTranslation)
                        .collect(Collectors.toList());
            }
            return ImageRecognitionResponse.build(res, translations);
        }).collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // Serving files locally from the uploads directory
    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<?> getFile(@PathVariable String filename) {
        org.springframework.core.io.Resource file = storageService.loadAsResource(filename);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                .body(file);
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userRepository.findById(userDetails.getId()).orElse(null);
        }
        return null;
    }
}
