package com.tayyar.auth;

import java.util.Locale;

public final class EmailAddress {
    private EmailAddress() {}

    public static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
