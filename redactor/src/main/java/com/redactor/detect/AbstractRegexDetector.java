package com.redactor.detect;

import com.redactor.model.PiiMatch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for detectors whose PII has a rigid textual shape (email, phone,
 * SSN, ...). Subclasses supply a pattern and may override {@link #accept} to
 * apply a check regex can't express, such as a Luhn checksum.
 */
public abstract class AbstractRegexDetector implements PiiDetector {

    private final Pattern pattern;

    protected AbstractRegexDetector(String regex) {
        this(Pattern.compile(regex));
    }

    protected AbstractRegexDetector(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public List<PiiMatch> detect(String text) {
        List<PiiMatch> matches = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return matches;
        }
        Matcher matcher = pattern.matcher(text);
        int group = captureGroup();
        while (matcher.find()) {
            if (matcher.start(group) < 0) {
                continue;
            }
            String value = matcher.group(group);
            if (value == null || value.isBlank()) {
                continue;
            }
            if (accept(value, text, matcher.start(group), matcher.end(group))) {
                matches.add(new PiiMatch(matcher.start(group), matcher.end(group), type(), value));
            }
        }
        return matches;
    }

    /**
     * Which capture group carries the PII. Defaults to 0 (the whole match);
     * override when the pattern needs context that must survive redaction, e.g.
     * matching "DOB: 4 May 1990" but keeping the label "DOB:" in the output.
     */
    protected int captureGroup() {
        return 0;
    }

    /** Post-regex validation. Return false to reject a syntactic match. */
    protected boolean accept(String value, String fullText, int start, int end) {
        return true;
    }
}
