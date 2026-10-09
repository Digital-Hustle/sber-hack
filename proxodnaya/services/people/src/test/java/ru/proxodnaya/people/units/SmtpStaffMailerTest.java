package ru.proxodnaya.people.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.config.properties.StaffProperties;
import ru.proxodnaya.people.features.staff.gateway.SmtpStaffMailer;

/**
 * Без настроенной почты боевой вход не имитирует отправленное письмо.
 */
class SmtpStaffMailerTest {

    @Test
    void missingSmtp_withoutDebugLogging_isUnavailable() {
        // given
        SmtpStaffMailer mailer = mailer(false);
        // when / then
        assertThatThrownBy(() -> mailer.loginCode("user@example.com", "123456", 10))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }

    @Test
    void explicitLogCodesMode_retainsLocalDebugBehavior() {
        // when / then
        assertThatCode(() -> mailer(true).loginCode("user@example.com", "123456", 10)).doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private SmtpStaffMailer mailer(boolean logCodes) {
        ObjectProvider<JavaMailSender> sender = mock(ObjectProvider.class);
        StaffProperties props = mock(StaffProperties.class);
        when(props.logCodes()).thenReturn(logCodes);
        return new SmtpStaffMailer(sender, props, "");
    }
}
