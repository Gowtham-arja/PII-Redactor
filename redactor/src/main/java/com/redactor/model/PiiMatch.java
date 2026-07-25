package com.redactor.model;

/**
 * One detected span of PII inside a block of text.
 *
 * @param start inclusive character offset
 * @param end   exclusive character offset
 * @param type  what kind of PII this is
 * @param value the exact matched text
 */
public record PiiMatch(int start, int end, PiiType type, String value) {

    public int length() {
        return end - start;
    }

    public boolean overlaps(PiiMatch other) {
        return start < other.end && other.start < end;
    }
}
