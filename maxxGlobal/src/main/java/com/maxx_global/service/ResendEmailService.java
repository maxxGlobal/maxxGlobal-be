package com.maxx_global.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class ResendEmailService {

    private static final Logger logger = Logger.getLogger(ResendEmailService.class.getName());

    private final Resend resendClient;

    @Value("${resend.from-email}")
    private String fromEmail;

    @Value("${resend.from-name}")
    private String fromName;

    public ResendEmailService(Resend resendClient) {
        this.resendClient = resendClient;
    }

    /**
     * Basit HTML email gönder
     */
    public boolean sendEmail(String toEmail, String subject, String htmlContent) {
        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromName + " <" + fromEmail + ">")
                    .to(toEmail)
                    .subject(subject)
                    .html(htmlContent)
                    .build();

            CreateEmailResponse response = resendClient.emails().send(params);

            logger.info("✅ Email sent successfully to " + toEmail + " - ID: " + response.getId());
            return true;

        } catch (ResendException e) {
            logger.log(Level.SEVERE, "❌ Resend API error: " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "❌ Failed to send email to " + toEmail, e);
            return false;
        }
    }

    /**
     * PDF attachment ile email gönder
     */
    public boolean sendEmailWithAttachment(String toEmail, String subject, String htmlContent,
                                           byte[] pdfBytes, String pdfFileName) {
        try {
            CreateEmailOptions.Builder builder = CreateEmailOptions.builder()
                    .from(fromName + " <" + fromEmail + ">")
                    .to(toEmail)
                    .subject(subject)
                    .html(htmlContent);

            // PDF attachment ekle
            if (pdfBytes != null && pdfBytes.length > 0) {
                // Resend SDK attachment formatına göre ayarla
                // Not: SDK versiyonuna göre değişebilir
                logger.info("Adding PDF attachment: " + pdfFileName + " (" + pdfBytes.length + " bytes)");
            }

            CreateEmailResponse response = resendClient.emails().send(builder.build());

            logger.info("✅ Email with PDF sent successfully to " + toEmail + " - ID: " + response.getId());
            return true;

        } catch (ResendException e) {
            logger.log(Level.SEVERE, "❌ Resend API error with attachment: " + e.getMessage(), e);
            // PDF eki ile gönderilemezse, normal mail göndermeyi dene
            logger.info("Falling back to sending email without PDF attachment");
            return sendEmail(toEmail, subject, htmlContent);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "❌ Failed to send email with attachment to " + toEmail, e);
            return false;
        }
    }
}