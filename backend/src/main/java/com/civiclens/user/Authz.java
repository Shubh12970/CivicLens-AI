package com.civiclens.user;

import com.civiclens.common.ApiException;
import com.civiclens.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Access to the authenticated principal. */
public final class Authz {

    private Authz() {
    }

    public static Optional<CurrentUser> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static CurrentUser require() {
        return current().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Authentication is required."));
    }
}
