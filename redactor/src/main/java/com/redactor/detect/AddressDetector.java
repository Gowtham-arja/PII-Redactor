package com.redactor.detect;

import com.redactor.model.PiiMatch;
import com.redactor.model.PiiType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Postal addresses. Addresses have no fixed grammar, so instead of matching
 * one directly, this anchors on the one reliably-shaped part of an Indian
 * address - the six-digit PIN code - and expands left to the nearest line
 * break, comma run, or separator. This is the least precise detector in the
 * project; the README says so.
 */
@Component
public class AddressDetector implements PiiDetector {

    private static final Pattern PIN_CODE = Pattern.compile("(?<!\\d)\\d{3}\\s?\\d{3}(?!\\d)");
    private static final int LOOKBACK_CHARS = 150;

    @Override
    public PiiType type() {
        return PiiType.ADDRESS;
    }

    @Override
    public List<PiiMatch> detect(String text) {
        List<PiiMatch> matches = new ArrayList<>();
        Matcher pin = PIN_CODE.matcher(text);

        while (pin.find()) {
            int floor = Math.max(0, pin.start() - LOOKBACK_CHARS);
            int start = lastBoundary(text, floor, pin.start());

            String candidate = text.substring(start, pin.end()).strip();
            if (looksLikeAddress(candidate)) {
                int trimmedStart = pin.end() - candidate.length();
                matches.add(new PiiMatch(trimmedStart, pin.end(), PiiType.ADDRESS, candidate));
            }
        }
        return matches;
    }

    /** Walk back from {@code to} to the nearest line break or separator, no further than {@code from}. */
    private int lastBoundary(String text, int from, int to) {
        for (int i = to - 1; i >= from; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == ';' || c == '|') {
                return i + 1;
            }
        }
        return from;
    }

    /** A bare PIN code isn't an address: require a comma and enough letters
     *  that this isn't just a financial figure. */
    private boolean looksLikeAddress(String candidate) {
        return candidate.length() >= 20
                && candidate.contains(",")
                && candidate.chars().filter(Character::isLetter).count() >= 8;
    }
}
