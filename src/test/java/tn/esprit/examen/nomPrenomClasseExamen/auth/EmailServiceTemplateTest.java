package tn.esprit.examen.nomPrenomClasseExamen.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import tn.esprit.examen.nomPrenomClasseExamen.services.EmailService;
import tn.esprit.examen.nomPrenomClasseExamen.services.EmailTemplateName;

import jakarta.mail.internet.MimeMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the template-name bug: {@code EmailService} must resolve the Thymeleaf template from
 * {@code emailTemplateName.getName()} ("activate_account" / "reset_password"), not from the enum
 * constant name ("ACTIVATE_ACCOUNT" / "RESET_PASSWORD").
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTemplateTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private SpringTemplateEngine templateEngine;
    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "mailUsername", "noreply@anapco.com");
        ReflectionTestUtils.setField(emailService, "configuredFrom", "");
    }

    private MimeMessage realMimeMessage() {
        return new JavaMailSenderImpl().createMimeMessage();
    }

    @Test
    void enumGetNameMapsToTheTemplateFileNames() {
        assertThat(EmailTemplateName.ACTIVATE_ACCOUNT.getName()).isEqualTo("activate_account");
        assertThat(EmailTemplateName.RESET_PASSWORD.getName()).isEqualTo("reset_password");
    }

    @Test
    void activationEmailRendersTheLowercaseTemplate() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
        when(templateEngine.process(eq("activate_account"), any())).thenReturn("<html>ok</html>");

        emailService.SendEmail("user@example.com", "John Doe", EmailTemplateName.ACTIVATE_ACCOUNT,
                "http://localhost:8089/auth/activate-account?token=123456", "123456", "account activation");

        verify(templateEngine).process(eq("activate_account"), any());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void resetPasswordEmailRendersTheLowercaseTemplate() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
        when(templateEngine.process(eq("reset_password"), any())).thenReturn("<html>ok</html>");

        emailService.SendEmail("user@example.com", "John Doe", EmailTemplateName.RESET_PASSWORD,
                "http://localhost:4200/reset-password?token=abc", "abc", "Password Reset Request");

        verify(templateEngine).process(eq("reset_password"), any());
    }

    @Test
    void senderFallsBackToMailUsernameWhenConfiguredFromIsBlank() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
        when(templateEngine.process(eq("activate_account"), any())).thenReturn("<html>ok</html>");

        // Should not throw — "noreply@anapco.com" (mailUsername) is used as the From address.
        emailService.SendEmail("user@example.com", "John Doe", EmailTemplateName.ACTIVATE_ACCOUNT,
                "http://x?token=1", "1", "s");

        verify(mailSender).send(any(MimeMessage.class));
    }
}
