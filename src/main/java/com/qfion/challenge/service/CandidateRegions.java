package com.qfion.challenge.service;

import java.util.Map;
import java.util.Locale;

public final class CandidateRegions {
    private CandidateRegions() {}
    public static final Map<String,String> STATES = Map.ofEntries(
        Map.entry("AL","Alabama"),
        Map.entry("AK","Alaska"),
        Map.entry("AZ","Arizona"),
        Map.entry("AR","Arkansas"),
        Map.entry("CA","California"),
        Map.entry("CO","Colorado"),
        Map.entry("CT","Connecticut"),
        Map.entry("DE","Delaware"),
        Map.entry("DC","District of Columbia"),
        Map.entry("FL","Florida"),
        Map.entry("GA","Georgia"),
        Map.entry("HI","Hawaii"),
        Map.entry("ID","Idaho"),
        Map.entry("IL","Illinois"),
        Map.entry("IN","Indiana"),
        Map.entry("IA","Iowa"),
        Map.entry("KS","Kansas"),
        Map.entry("KY","Kentucky"),
        Map.entry("LA","Louisiana"),
        Map.entry("ME","Maine"),
        Map.entry("MD","Maryland"),
        Map.entry("MA","Massachusetts"),
        Map.entry("MI","Michigan"),
        Map.entry("MN","Minnesota"),
        Map.entry("MS","Mississippi"),
        Map.entry("MO","Missouri"),
        Map.entry("MT","Montana"),
        Map.entry("NE","Nebraska"),
        Map.entry("NV","Nevada"),
        Map.entry("NH","New Hampshire"),
        Map.entry("NJ","New Jersey"),
        Map.entry("NM","New Mexico"),
        Map.entry("NY","New York"),
        Map.entry("NC","North Carolina"),
        Map.entry("ND","North Dakota"),
        Map.entry("OH","Ohio"),
        Map.entry("OK","Oklahoma"),
        Map.entry("OR","Oregon"),
        Map.entry("PA","Pennsylvania"),
        Map.entry("RI","Rhode Island"),
        Map.entry("SC","South Carolina"),
        Map.entry("SD","South Dakota"),
        Map.entry("TN","Tennessee"),
        Map.entry("TX","Texas"),
        Map.entry("UT","Utah"),
        Map.entry("VT","Vermont"),
        Map.entry("VA","Virginia"),
        Map.entry("WA","Washington"),
        Map.entry("WV","West Virginia"),
        Map.entry("WI","Wisconsin"),
        Map.entry("WY","Wyoming"));
    public static boolean valid(String code) {
        return code.isEmpty() || STATES.containsKey(code) || code.equals("UNKNOWN") || code.equals("NON_US");
    }
    public static String label(String code) {
        return STATES.getOrDefault(code, "NON_US".equals(code) ? "Outside US" : "Unknown");
    }
    public static String stateCode(String code, String name) {
        String key = code == null ? "" : code.toUpperCase(Locale.ROOT).replaceFirst("^US-", "");
        if (STATES.containsKey(key)) return key;
        return STATES.entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(name))
            .map(Map.Entry::getKey).findFirst().orElse("UNKNOWN");
    }
}
