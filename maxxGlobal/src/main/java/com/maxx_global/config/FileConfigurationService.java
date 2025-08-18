package com.maxx_global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

@Component
@Order(1) // İlk önce çalışsın
public class FileConfigurationService implements CommandLineRunner {

    private static final Logger logger = Logger.getLogger(FileConfigurationService.class.getName());

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    @Value("${app.file.product-images-dir}")
    private String productImagesDir;

    @Override
    public void run(String... args) throws Exception {
        logger.info("Initializing file directory structure...");
        createDirectoryStructure();
        validateDirectoryPermissions();
        logger.info("File system initialization completed successfully");
    }

    private void createDirectoryStructure() {
        try {
            // Ana upload klasörü
            Path uploadPath = createDirectoryIfNotExists(uploadDir);

            // Product images klasörü
            Path productImagesPath = uploadPath.resolve(productImagesDir);
            createDirectoryIfNotExists(productImagesPath.toString());

            // Test için .gitkeep dosyası oluştur
            createGitKeepFile(uploadPath);

        } catch (Exception e) {
            logger.severe("CRITICAL: Failed to create directory structure: " + e.getMessage());
            throw new RuntimeException("Dosya klasör yapısı oluşturulamadı. Uygulama başlatılamıyor: " + e.getMessage());
        }
    }

    private Path createDirectoryIfNotExists(String directoryPath) throws IOException {
        Path path = Paths.get(directoryPath);

        if (!Files.exists(path)) {
            Files.createDirectories(path);
            logger.info("✓ Created directory: " + path.toAbsolutePath());
        } else {
            logger.info("✓ Directory exists: " + path.toAbsolutePath());
        }

        return path;
    }

    private void validateDirectoryPermissions() {
        Path uploadPath = Paths.get(uploadDir);

        if (!Files.isWritable(uploadPath)) {
            throw new RuntimeException("Upload klasörüne yazma izni yok: " + uploadPath.toAbsolutePath());
        }

        if (!Files.isReadable(uploadPath)) {
            throw new RuntimeException("Upload klasöründen okuma izni yok: " + uploadPath.toAbsolutePath());
        }

        logger.info("✓ Directory permissions validated");
    }

    private void createGitKeepFile(Path uploadPath) {
        try {
            Path gitKeepPath = uploadPath.resolve(".gitkeep");
            if (!Files.exists(gitKeepPath)) {
                Files.createFile(gitKeepPath);
                logger.info("✓ Created .gitkeep file");
            }
        } catch (Exception e) {
            logger.warning("Could not create .gitkeep file: " + e.getMessage());
        }
    }

    /**
     * Runtime'da klasör oluşturma (sadece ihtiyaç durumunda)
     */
    public void ensureDirectoryExists(String directoryPath) {
        try {
            Path path = Paths.get(directoryPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("Created runtime directory: " + path.toAbsolutePath());
            }
        } catch (Exception e) {
            logger.severe("Failed to create runtime directory: " + directoryPath + " - " + e.getMessage());
            throw new RuntimeException("Klasör oluşturulamadı: " + directoryPath);
        }
    }

    /**
     * Disk alanı kontrolü
     */
    public boolean hasEnoughSpace(long requiredBytes) {
        try {
            Path uploadPath = Paths.get(uploadDir);
            long usableSpace = Files.getFileStore(uploadPath).getUsableSpace();
            return usableSpace > requiredBytes;
        } catch (Exception e) {
            logger.warning("Could not check disk space: " + e.getMessage());
            return true; // Güvenli tarafta kal
        }
    }
}