package tn.esprit.examen.nomPrenomClasseExamen.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Sends transactional emails (account activation, password reset) rendered from Thymeleaf templates.
 *
 * <p>Sending is <b>synchronous</b>: the caller (registration / forgot-password) must be able to see
 * an SMTP or template failure and report it, instead of returning success while the email silently
 * fails on a background thread.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine springTemplateEngine;

    /** Optional explicit sender; falls back to {@code spring.mail.username} when blank. */
    @Value("${application.mailing.from:}")
    private String configuredFrom;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public void SendEmail(String to, String username, EmailTemplateName emailTemplateName,
                          String confirmationUrl, String activationCode, String subject)
            throws MessagingException {

        String templateName = emailTemplateName == null ? "confirm_email" : emailTemplateName.getName();

        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                MimeMessageHelper.MULTIPART_MODE_MIXED,
                StandardCharsets.UTF_8.name()
        );

        Map<String, Object> properties = new HashMap<>();
        properties.put("username", username);
        properties.put("confirmationUrl", confirmationUrl);
        properties.put("activation_Code", activationCode);

        Context context = new Context();
        context.setVariables(properties);

        helper.setFrom(resolveFrom());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(springTemplateEngine.process(templateName, context), true);

        javaMailSender.send(mimeMessage);
    }

    private String resolveFrom() {
        if (configuredFrom != null && !configuredFrom.isBlank()) {
            return configuredFrom;
        }
        return mailUsername;
    }
}
