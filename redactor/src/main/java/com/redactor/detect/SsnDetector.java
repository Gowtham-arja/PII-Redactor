package com.redactor.detect;

import com.redactor.model.PiiType;
import org.springframework.stereotype.Component;

/**
 * US Social Security Numbers. The negative lookaheads mirror the SSA's own
 * allocation rules (area 000/666/900-999 and group 00 are never issued), which
 * keeps the pattern from firing on placeholder-style digit strings.
 *
 * <p>This corpus has zero real SSNs — there is nothing to catch here, and the
 * README says so rather than claiming a false 100%.
 */
@Component
public class SsnDetector extends AbstractRegexDetector {

    public SsnDetector() {
        super("\\b(?!000|666|9\\d\\d)\\d{3}-(?!00)\\d{2}-(?!0000)\\d{4}\\b");
    }

    @Override
    public PiiType type() {
        return PiiType.SSN;
    }
}
