package escuelaing.edu.co.truckcar.User_Identity.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetMailerTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        when(provider.getObject()).thenReturn(sender);
        return provider;
    }

    @Test
    void sendsTheCodeInTheSubjectAndBodyFromTheProjectAccount() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        PasswordResetMailer mailer = new PasswordResetMailer(provider(sender), "truckcar.app@gmail.com", "smtp.gmail.com");

        mailer.sendCode("laura@truckcar.co", "Laura", "482913", 10);

        var captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("Su código para cambiar la contraseña de TruckCar: 482913");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("laura@truckcar.co");
        assertThat(sent.getFrom()[0].toString()).contains("truckcar.app@gmail.com").contains("TruckCar");
    }

    @Test
    void withoutSmtpNothingIsSent() {
        JavaMailSender sender = mock(JavaMailSender.class);
        PasswordResetMailer mailer = new PasswordResetMailer(provider(sender), "", "");
        assertThat(mailer.isConfigured()).isFalse();
        mailer.sendCode("laura@truckcar.co", "Laura", "482913", 10);
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void theBodyEscapesTheNameAndTellsHowLongItLasts() {
        String html = PasswordResetMailer.html("<script>alert(1)</script>", "482913", 10);
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;").contains("482913").contains("10 minutos");
        assertThat(PasswordResetMailer.text(null, "482913", 10)).startsWith("Hola conductor,").contains("482913");
    }

    @Test
    void theLogNeverShowsTheFullEmail() {
        assertThat(PasswordResetMailer.mask("laura@truckcar.co")).isEqualTo("l***@truckcar.co");
        assertThat(PasswordResetMailer.mask("a@x.co")).isEqualTo("***@x.co");
    }
}
