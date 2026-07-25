package com.redactor.detect;

import com.redactor.model.PiiType;
import org.springframework.stereotype.Component;

/**
 * Payment card numbers (13-19 digits, optionally spaced/hyphenated), verified
 * with a Luhn checksum. The prospectus has long digit runs (share counts,
 * capital figures); Luhn is what stops those from being false-flagged as cards.
 */
@Component
public class CreditCardDetector extends AbstractRegexDetector {

    public CreditCardDetector() {
        super("(?<![\\d\\-])(?:\\d[ \\-]?){12,18}\\d(?![\\d\\-])");
    }

    @Override
    public PiiType type() {
        return PiiType.CREDIT_CARD;
    }

    @Override
    protected boolean accept(String value, String fullText, int start, int end) {
        String digits = value.replaceAll("\\D", "");
        return digits.length() >= 13 && digits.length() <= 19 && luhnValid(digits);
    }

    private static boolean luhnValid(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
