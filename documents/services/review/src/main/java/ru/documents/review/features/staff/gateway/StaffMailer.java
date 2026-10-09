package ru.documents.review.features.staff.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import ru.documents.common.exception.DomainException;
import ru.documents.review.config.StaffProperties;

/**
 * Письма сотрудникам через общий почтовый узел сервера ({@code SMTP_*}). Без {@code SMTP_HOST} письма не уходят:
 * код пишется в лог только при {@code STAFF_LOG_CODES=true}, иначе вход по коду недоступен (честная ошибка).
 */
@Component
public class StaffMailer {

    private static final Logger LOG = LoggerFactory.getLogger(StaffMailer.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final StaffProperties props;
    private final String host;

    public StaffMailer(ObjectProvider<JavaMailSender> sender, StaffProperties props,
                       @Value("${spring.mail.host:}") String host) {
        this.sender = sender;
        this.props = props;
        this.host = host;
    }

    /**
     * Письмо с кодом входа.
     *
     * @param email   кому
     * @param code    6 цифр
     * @param minutes сколько действует
     */
    public void loginCode(String email, String code, long minutes) {
        String text = "Код для входа в рабочее место «Документы»: " + code + "\n\n"
                + "Код действует " + minutes + " мин. Никому его не сообщайте.\n"
                + "Если вы не запрашивали вход, просто проигнорируйте письмо.";
        if (configured()) {
            send(email, "Код входа: " + code, text);
        } else if (props.logCodes() || props.testMode()) {
            LOG.warn("staff.code-logged SMTP не настроен, код входа для {}: {}", email, code);
        } else {
            throw new DomainException("MAIL_UNAVAILABLE", 503, "Почта для кодов входа не настроена",
                    "Сообщите администратору стенда");
        }
    }

    /**
     * Приглашение: как войти. Не отправилось — не страшно, сотрудник всё равно может запросить код.
     *
     * @param email кому
     * @param name  имя
     * @param role  роль по-русски
     */
    public void invitation(String email, String name, String role) {
        if (!configured()) {
            return;
        }
        String text = "Здравствуйте, " + name + "!\n\n"
                + "Вас добавили в рабочее место «Документы» с ролью «" + role + "».\n"
                + "Войдите по адресу " + props.loginUrl() + " — введите эту почту, и мы пришлём код. Пароль не нужен.";
        try {
            send(email, "Приглашение в «Документы»", text);
        } catch (DomainException e) {
            LOG.warn("staff.invite-not-sent code={}", e.code());
        }
    }

    private boolean configured() {
        return !host.isBlank() && sender.getIfAvailable() != null;
    }

    private void send(String email, String subject, String text) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(props.mailFrom());
        m.setTo(email);
        m.setSubject(subject);
        m.setText(text);
        try {
            sender.getObject().send(m);
        } catch (MailException e) {
            LOG.error("staff.mail-failed {}", e.getClass().getSimpleName());
            throw new DomainException("MAIL_UNAVAILABLE", 503, "Не удалось отправить письмо",
                    "Повторите через минуту");
        }
    }
}
