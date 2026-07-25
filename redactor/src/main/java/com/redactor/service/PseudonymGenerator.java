package com.redactor.service;

import com.redactor.model.PiiType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates fake replacement values and remembers them, so that "Kushal
 * Subbayya Hegde" reads as the same fake person everywhere it appears in the
 * document, and "cs.connect@kshinternational.com" always becomes the same
 * fake e-mail.
 */
@Component
public class PseudonymGenerator {

    private static final List<String> FAKE_PERSON_NAMES = List.of(
            "John Doe", "Jane Smith", "Peter Parker", "Mary Johnson", "Robert Brown",
            "Emily Davis", "Michael Wilson", "Sarah Miller", "David Anderson", "Laura Thomas");

    private static final List<String> FAKE_ORG_NAMES = List.of(
            "Acme Metals Limited", "Globex Industries Limited", "Initech Solutions LLP",
            "Umbrella Holdings Private Limited", "Stark Enterprises Limited",
            "Wayne Manufacturing Limited", "Wonka Industries Limited");

    private final Map<String, String> assigned = new LinkedHashMap<>();
    private final Map<PiiType, Integer> counters = new EnumMap<>(PiiType.class);

    public synchronized String pseudonymFor(PiiType type, String original) {
        String key = type + "|" + normalise(type, original);
        return assigned.computeIfAbsent(key, k -> {
            int n = counters.merge(type, 1, Integer::sum);
            return generate(type, n);
        });
    }

    private String generate(PiiType type, int n) {
        return switch (type) {
            case PERSON -> nth(FAKE_PERSON_NAMES, n, "Person " + n);
            case ORGANIZATION -> nth(FAKE_ORG_NAMES, n, "Company " + n + " Limited");
            case EMAIL -> "user" + n + "@example.com";
            case PHONE -> String.format("+91 90%08d", n);
            case ADDRESS -> n + " Example Street, Sample City " + String.format("%06d", 100000 + n);
            case SSN -> String.format("123-45-%04d", n);
            case CREDIT_CARD -> String.format("4000 0000 0000 %04d", n % 10000);
            case DATE_OF_BIRTH -> String.format("%02d January %d", 1 + (n % 28), 1990 + (n % 20));
            case IP_ADDRESS -> "10.0.0." + (1 + (n % 254));
        };
    }

    private String nth(List<String> pool, int n, String fallback) {
        return (n - 1) < pool.size() ? pool.get(n - 1) : fallback;
    }

    /**
     * For PERSON, key on first+last name only, so a full name and its
     * shortened form later in the document resolve to the same alias.
     */
    private String normalise(PiiType type, String value) {
        String base = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
        if (type == PiiType.PERSON) {
            String[] parts = base.split(" ");
            if (parts.length >= 2) {
                return parts[0] + "_" + parts[parts.length - 1];
            }
        }
        return base;
    }
}
