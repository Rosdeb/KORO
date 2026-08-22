package com.koro.app.integration.vision;

import org.springframework.web.multipart.MultipartFile;

public interface VisionService {
    VisionResult detectLabel(MultipartFile file);
}
