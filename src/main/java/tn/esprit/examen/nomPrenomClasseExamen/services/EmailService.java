package tn.esprit.examen.nomPrenomClasseExamen.services;


import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor

public class EmailService {
    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine springTemplateEngine;

    @Async

    public void SendEmail(String to , String username, EmailTemplateName emailTemplateName
            , String confirmationUrl, String activationCode , String subject)
            throws MessagingException {
        String TemplateName;
        if (emailTemplateName == null) {
            TemplateName = "confirm_email";
        } else {
            TemplateName = emailTemplateName.name();
        }
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(
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

        mimeMessageHelper.setFrom("Laamerisayf@gmail.com");
        mimeMessageHelper.setTo(to);
        mimeMessageHelper.setSubject(subject);

        String template = springTemplateEngine.process(TemplateName, context);
        mimeMessageHelper.setText(template, true);
        javaMailSender.send(mimeMessage);

    }
}