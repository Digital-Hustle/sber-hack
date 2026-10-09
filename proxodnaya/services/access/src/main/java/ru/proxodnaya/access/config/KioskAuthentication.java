package ru.proxodnaya.access.config;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import ru.proxodnaya.access.features.kiosk.api.KioskPrincipal;

/**
 * Аутентификация терминала по подписи запроса: роль {@code KIOSK}, принципал — {@link KioskPrincipal}.
 */
public class KioskAuthentication extends AbstractAuthenticationToken {

    private final transient KioskPrincipal principal;

    public KioskAuthentication(KioskPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_KIOSK")));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
