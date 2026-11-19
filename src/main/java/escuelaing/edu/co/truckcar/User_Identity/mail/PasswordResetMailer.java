package escuelaing.edu.co.truckcar.User_Identity.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

/**
 * Envía por SMTP (Gmail con contraseña de aplicación) el código para recuperar la contraseña.
 * <p>
 * Asíncrono: la respuesta de /password/forgot tarda lo mismo exista o no la cuenta (no revela qué correos están
 * registrados). Sin SMTP configurado no envía nada y lo deja en el log, sin el código.
 */
@Slf4j
@Component
public class PasswordResetMailer {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final String host;

    @org.springframework.beans.factory.annotation.Autowired
    public PasswordResetMailer(ObjectProvider<JavaMailSender> mailSender,
                               @Value("${app.mail.from:}") String from,
                               @Value("${spring.mail.host:}") String host,
                               @Value("${spring.mail.username:}") String username) {
        this.mailSender = mailSender;
        // docker-compose pasa TRUCKCAR_MAIL_FROM vacío si no está en el .env: entonces el remitente es la cuenta SMTP.
        this.from = from == null || from.isBlank() ? username : from;
        this.host = host;
    }

    PasswordResetMailer(ObjectProvider<JavaMailSender> mailSender, String from, String host) {
        this(mailSender, from, host, "");
    }

    public boolean isConfigured() {
        return host != null && !host.isBlank() && from != null && !from.isBlank() && mailSender.getIfAvailable() != null;
    }

    @Async
    public void sendCode(String to, String firstName, String code, long validMinutes) {
        if (!isConfigured()) {
            log.warn("Correo SMTP sin configurar (SMTP_HOST, SMTP_USERNAME, SMTP_PASSWORD): no se envió el código de recuperación");
            return;
        }
        try {
            JavaMailSender sender = mailSender.getObject();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, "TruckCar");
            helper.setTo(to);
            helper.setSubject("Su código para cambiar la contraseña de TruckCar: " + code);
            helper.setText(text(firstName, code, validMinutes), html(firstName, code, validMinutes));
            sender.send(message);
            log.info("Código de recuperación enviado a {}", mask(to));
        } catch (Exception e) {
            // El mensaje de error de JavaMail no incluye credenciales; aun así solo se registra el tipo.
            log.error("No se pudo enviar el código de recuperación a {}: {}", mask(to), e.getClass().getSimpleName());
        }
    }

    static String text(String firstName, String code, long validMinutes) {
        return "Hola " + safeName(firstName) + ",\n\n"
                + "Su código para cambiar la contraseña de TruckCar es: " + code + "\n\n"
                + "Vence en " + validMinutes + " minutos y sirve una sola vez.\n"
                + "Si usted no lo pidió, ignore este correo: su contraseña sigue igual.\n\n"
                + "TruckCar · nunca le pediremos este código por teléfono ni por chat.";
    }

    static String html(String firstName, String code, long validMinutes) {
        String name = HtmlUtils.htmlEscape(safeName(firstName));
        return """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:480px;margin:0 auto;color:#1d1d1b">
                  <h2 style="margin:0 0 12px">Cambiar su contraseña</h2>
                  <p>Hola %s,</p>
                  <p>Use este código en la aplicación TruckCar para cambiar su contraseña:</p>
                  <p style="font-size:32px;font-weight:bold;letter-spacing:8px;background:#f4f1ea;padding:16px;text-align:center;border-radius:12px">%s</p>
                  <p>Vence en %d minutos y sirve una sola vez.</p>
                  <p style="color:#5a574f">Si usted no lo pidió, ignore este correo: su contraseña sigue igual.
                  Nunca le pediremos este código por teléfono ni por chat.</p>
                </div>
                """.formatted(name, code, validMinutes);
    }

    private static String safeName(String firstName) {
        return firstName == null || firstName.isBlank() ? "conductor" : firstName.strip();
    }

    /** d***@gmail.com: para el log, nunca el correo completo. */
    public static String mask(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.charAt(0) + "***" + email.substring(at);
    }
}
