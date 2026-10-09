package ru.proxodnaya.people.features.staff.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.config.properties.StaffProperties;

/**
 * Письма через SMTP ({@code SMTP_*}). Без {@code SMTP_HOST} письма не уходят: код пишется в лог только при
 * {@code STAFF_LOG_CODES=true} — это для локальной разработки, на стенде флаг выключен.
 */
@Component
@ConditionalOnProperty(name = "people.shared-mail.enabled", havingValue = "false", matchIfMissing = true)
public class SmtpStaffMailer implements StaffMailer {

    private static final Logger LOG = LoggerFactory.getLogger(SmtpStaffMailer.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final StaffProperties props;
    private final String host;

    public SmtpStaffMailer(ObjectProvider<JavaMailSender> sender, StaffProperties props, @Value("${spring.mail.host:}") String host) {
        this.sender = sender;
        this.props = props;
        this.host = host;
    }

    @Override
    public void loginCode(String email, String code, long minutes) {
        String text = "Код для входа в кабинет «Проходная»: " + code + "\n\n"
                + "Код действует " + minutes + " мин. Никому его не сообщайте — сотрудники «Проходной» его не спрашивают.\n"
                + "Если вы не запрашивали вход, просто проигнорируйте письмо.";
        if (!send(email, "Код входа в кабинет", text)) {
            if (props.logCodes()) {
                LOG.warn("SMTP не настроен, код входа для {}: {}", email, code);
            } else {
                throw new DomainException(ErrorCode.SERVICE_UNAVAILABLE, "Почта ещё не подключена — обратитесь к администратору");
            }
        }
    }

    @Override
    public void invitation(String email, String name, String role, String invitedBy) {
        String text = "Здравствуйте, " + name + "!\n\n"
                + invitedBy + " пригласил(а) вас в кабинет «Проходная» с ролью «" + role + "».\n"
                + "Войдите по адресу " + props.loginUrl() + " — введите эту почту, и мы пришлём код. Пароль не нужен.";
        send(email, "Приглашение в «Проходную»", text);
    }

    private boolean send(String email, String subject, String text) {
        JavaMailSender mail = sender.getIfAvailable();
        if (host.isBlank() || mail == null) {
            LOG.warn("SMTP не настроен (SMTP_HOST) — письмо «{}» не отправлено", subject.replaceAll("\\d", "•"));
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.mailFrom());
        message.setTo(email);
        message.setSubject(subject);
        message.setText(text);
        try {
            mail.send(message);
            return true;
        } catch (MailException error) {
            LOG.error("Письмо не отправлено: {}", error.getMessage());
            throw new DomainException(ErrorCode.SERVICE_UNAVAILABLE, "Не удалось отправить письмо — попробуйте через минуту");
        }
    }
}
