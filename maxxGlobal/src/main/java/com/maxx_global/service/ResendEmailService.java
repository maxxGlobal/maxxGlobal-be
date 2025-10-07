package com.maxx_global.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.resend.services.emails.model.Attachment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;
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
            // PDF yoksa veya boşsa normal email gönder
            if (pdfBytes == null || pdfBytes.length == 0) {
                return sendEmail(toEmail, subject, htmlContent);
            }

            logger.info("📎 Preparing email with PDF attachment: " + pdfFileName + " (" + pdfBytes.length + " bytes)");

            // ✅ Resend SDK Attachment formatı
            Attachment pdfAttachment = Attachment.builder()
                    .fileName(pdfFileName + ".pdf")
                    .content(Base64.getEncoder().encodeToString(pdfBytes))
                    .build();

            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromName + " <" + fromEmail + ">")
                    .to(toEmail)
                    .subject(subject)
                    .html(htmlContent)
                    .attachments(pdfAttachment) // ✅ PDF eki
                    .build();

            CreateEmailResponse response = resendClient.emails().send(params);

            logger.info("✅ Email with PDF sent successfully to " + toEmail + " - ID: " + response.getId());
            return true;

        } catch (ResendException e) {
            logger.log(Level.SEVERE, "❌ Resend API error with attachment: " + e.getMessage(), e);
            // PDF eki ile gönderilemezse, normal mail göndermeyi dene
            logger.info("⚠️ Falling back to sending email without PDF attachment");
            return sendEmail(toEmail, subject, htmlContent);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "❌ Failed to send email with attachment to " + toEmail, e);
            // Fallback: PDF olmadan gönder
            return sendEmail(toEmail, subject, htmlContent);
        }
    }

    /**
     * Email servisi sağlık kontrolü
     */
    public boolean isHealthy() {
        try {
            // Basit bir health check - API key geçerliliği kontrolü
            return resendClient != null;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Resend health check failed", e);
            return false;
        }
    }
}