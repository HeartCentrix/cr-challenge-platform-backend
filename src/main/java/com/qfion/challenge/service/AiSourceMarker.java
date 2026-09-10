package com.qfion.challenge.service;

/** Exact source marker lookup for private admin review, never an automatic cheating verdict. */
public final class AiSourceMarker {
    // v1: a block comment containing exactly 64 soft hyphens (U+00AD). These
    // survive both UTF8 and the local WIN1252 database. Keep in sync with
    // frontend public/app.js; retain old
    // signatures if a later version is added so historical answers remain checkable.
    private static final String V1 = "/*" + "\u00ad".repeat(64) + "*/";

    private AiSourceMarker() {}

    static String signature() { return V1; }

    public static boolean detected(String sourceCode) {
        return sourceCode != null && sourceCode.contains(V1);
    }
}
