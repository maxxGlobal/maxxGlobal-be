package com.maxx_global.mail;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "aws.ses.access-key=test-access-key",
        "aws.ses.secret-key=test-secret-key",
        "aws.ses.region=eu-west-1",
        "aws.ses.from-email=test@example.com"
})
public class AWSCredentialsTest {

    @Value("${aws.ses.access-key:}")
    private String accessKey;

    @Value("${aws.ses.secret-key:}")
    private String secretKey;

    @Value("${aws.ses.region:}")
    private String region;

    @Value("${aws.ses.from-email:}")
    private String fromEmail;

    @Test
    public void testAWSCredentialsAreConfigured() {
        // Environment variables'dan AWS credentials'ları kontrol et

        assertNotNull(accessKey, "AWS Access Key should be configured");
        assertNotNull(secretKey, "AWS Secret Key should be configured");
        assertNotNull(region, "AWS Region should be configured");
        assertNotNull(fromEmail, "AWS From Email should be configured");

        assertFalse(accessKey.isEmpty(), "AWS Access Key should not be empty");
        assertFalse(secretKey.isEmpty(), "AWS Secret Key should not be empty");
        assertFalse(region.isEmpty(), "AWS Region should not be empty");
        assertFalse(fromEmail.isEmpty(), "AWS From Email should not be empty");

        // Test environment için placeholder değerleri kontrol et
        assertEquals("test-access-key", accessKey);
        assertEquals("test-secret-key", secretKey);
        assertEquals("eu-west-1", region);
        assertEquals("test@example.com", fromEmail);
    }

    @Test
    public void testCredentialsFormat() {
        // Format validation testleri
        assertTrue(fromEmail.contains("@"), "From email should be a valid email format");
        assertTrue(region.matches("[a-z]+-[a-z]+-[0-9]+"), "Region should be in valid AWS format");
    }
}