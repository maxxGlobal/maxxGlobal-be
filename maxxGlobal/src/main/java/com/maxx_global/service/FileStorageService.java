package com.maxx_global.service;

import com.maxx_global.config.FileConfigurationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class FileStorageService {

    private static final Logger logger = Logger.getLogger(FileStorageService.class.getName());

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    @Value("${app.file.product-images-dir}")
    private String productImagesDir;

    @Value("${app.file.base-url}")
    private String baseUrl;

    private final FileConfigurationService fileConfigService;

    public FileStorageService(FileConfigurationService fileConfigService) {
        this.fileConfigService = fileConfigService;
    }

    /**
     * Ürün resimlerini kaydet
     * @param images Yüklenecek resim dosyaları
     * @param productId Ürün ID'si
     * @param primaryImageIndex Ana resim index'i
     * @return Kaydedilen resimlerin URL listesi
     */
    public List<String> saveProductImages(List<MultipartFile> images, Long productId, Integer primaryImageIndex) {
        if (images == null || images.isEmpty()) {
            return new ArrayList<>();
        }

        logger.info("Saving " + images.size() + " images for product: " + productId);

        List<String> imageUrls = new ArrayList<>();
        String productFolderPath = createProductFolder(productId);

        for (int i = 0; i < images.size(); i++) {
            MultipartFile image = images.get(i);
            try {
                String imageUrl = saveImage(image, productFolderPath, i,
                        primaryImageIndex != null && primaryImageIndex == i);
                imageUrls.add(imageUrl);

                logger.info("Image saved: " + imageUrl);
            } catch (IOException e) {
                logger.severe("Failed to save image at index " + i + ": " + e.getMessage());
                throw new RuntimeException("Resim kaydetme hatası (Index: " + i + "): " + e.getMessage());
            }
        }

        logger.info("Successfully saved " + imageUrls.size() + " images for product: " + productId);
        return imageUrls;
    }

    /**
     * Tek bir resmi kaydet
     */
    private String saveImage(MultipartFile image, String productFolderPath, int index, boolean isPrimary) throws IOException {
        String originalFilename = image.getOriginalFilename();
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));

        // Dosya adı oluştur
        String filename = generateImageFilename(index, isPrimary, extension);

        // Tam dosya yolu
        Path targetPath = Paths.get(productFolderPath, filename);

        // Dosyayı kaydet
        Files.copy(image.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // URL oluştur ve döndür
        return generateImageUrl(targetPath);
    }

    /**
     * Ürün için klasör oluştur
     */
    private String createProductFolder(Long productId) {
        LocalDate now = LocalDate.now();
        String year = now.format(DateTimeFormatter.ofPattern("yyyy"));
        String month = now.format(DateTimeFormatter.ofPattern("MM"));

        // uploads/products/2025/01/product-123
        String folderPath = String.join("/",
                uploadDir,
                productImagesDir,
                year,
                month,
                "product-" + productId
        );

        // Klasörün var olduğundan emin ol
        fileConfigService.ensureDirectoryExists(folderPath);

        logger.info("Product folder ready: " + folderPath);
        return folderPath;
    }

    /**
     * Resim dosya adı oluştur
     */
    private String generateImageFilename(int index, boolean isPrimary, String extension) {
        String prefix = isPrimary ? "primary" : "image-" + (index + 1);
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return prefix + "-" + uuid + extension;
    }

    /**
     * Resim URL'i oluştur
     */
    /**
     * Resim URL'i oluştur
     */
    private String generateImageUrl(Path imagePath) {
        try {
            // Tam dosya yolunu al
            String fullPath = imagePath.toString().replace("\\", "/");

            String relativePath;
            if (fullPath.contains(uploadDir + "/")) {
                relativePath = fullPath.substring(fullPath.indexOf(uploadDir + "/") + (uploadDir + "/").length());
            } else {
                // Fallback: upload directory'den sonrasını al
                int uploadDirIndex = fullPath.indexOf(uploadDir);
                if (uploadDirIndex != -1) {
                    relativePath = fullPath.substring(uploadDirIndex + uploadDir.length() + 1);
                } else {
                    throw new RuntimeException("Upload directory bulunamadı path'de: " + fullPath);
                }
            }

            // Final URL'i oluştur
            String finalUrl = baseUrl + "/uploads/" + relativePath;

            logger.info("Generated image URL: " + finalUrl);
            logger.info("Full path: " + fullPath);
            logger.info("Relative path: " + relativePath);

            return finalUrl;

        } catch (Exception e) {
            logger.severe("Error generating image URL: " + e.getMessage());
            throw new RuntimeException("URL oluşturma hatası: " + e.getMessage());
        }
    }

    /**
     * Tek bir resim dosyasını sil
     */
    public void deleteImageFile(String imageUrl) {
        try {
            // URL'den dosya yolunu çıkar
            // http://localhost:8080/uploads/products/2025/01/product-123/primary-abc123.jpg
            // -> uploads/products/2025/01/product-123/primary-abc123.jpg
            String relativePath = imageUrl.replace(baseUrl + "/", "");
            Path imagePath = Paths.get(relativePath);

            if (Files.exists(imagePath)) {
                Files.delete(imagePath);
                logger.info("Deleted image file: " + imagePath);
            } else {
                logger.warning("Image file not found: " + imagePath);
            }

        } catch (Exception e) {
            logger.severe("Failed to delete image file: " + imageUrl + " - " + e.getMessage());
            throw new RuntimeException("Resim dosyası silinemedi: " + e.getMessage());
        }
    }
}