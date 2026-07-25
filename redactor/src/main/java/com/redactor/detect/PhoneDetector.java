package com.redactor.detect;

import com.redactor.model.PiiType;
import org.springframework.stereotype.Component;

/**
 * Indian phone numbers: either an explicit "+ 91 ..." country code (the source
 * document writes a space after the plus sign) or a bare 10-digit mobile
 * number starting 6-9. The bare-number branch is kept narrow on purpose —
 * widening it to any 10 digits collides with share counts and rupee amounts,
 * which a prospectus has thousands of.
 */
@Component
public class PhoneDetector extends AbstractRegexDetector {

    public PhoneDetector() {
        super("\\+\\s?91[\\s\\-]?\\d[\\d\\s\\-]{7,14}\\d"
            + "|(?<![\\d\\-/])[6-9]\\d{9}(?![\\d\\-/])");
    }

    @Override
    public PiiType type() {
        return PiiType.PHONE;
    }

    @Override
    protected boolean accept(String value, String fullText, int start, int end) {
        long digits = value.chars().filter(Character::isDigit).count();
        return digits >= 10 && digits <= 13;
    }
}
