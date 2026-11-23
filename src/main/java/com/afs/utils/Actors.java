package com.afs.utils;

import com.afs.controller.filter.KiwiContext;

import java.security.Principal;

/**
 * Resolves the acting CMS account. The signed {@code admin-name} header is the only
 * trustworthy identity in local CMS mode; {@link KiwiContext} is populated by the
 * authentication interceptor after signature verification. Spring's {@link Principal}
 * is used as a fallback for simulation/Basic-auth deployments.
 */
public final class Actors {
    private Actors() {
    }

    public static String name(Principal principal) {
        try {
            if (KiwiContext.getContext() != null && KiwiContext.getUser() != null
                    && KiwiContext.getUser().getName() != null
                    && !KiwiContext.getUser().getName().isBlank()) {
                return KiwiContext.getUser().getName();
            }
        } catch (Exception ignored) {
            // Context may not be bound for public/simulation endpoints.
        }
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal.getName();
        }
        return "system";
    }
}
