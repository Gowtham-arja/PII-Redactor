package com.redactor.model;

/**
 * The 9 PII categories the assignment requires.
 *
 * <p>To add a new type: add a constant here, then add one class implementing
 * {@link com.redactor.detect.PiiDetector} annotated {@code @Component}. Nothing
 * else in the project changes — Spring collects every detector automatically.
 *
 * <p>{@code priority} breaks ties when two detectors both claim the same text.
 * Structured types (an e-mail has only one possible reading) outrank fuzzy ones
 * (a person name is a guess), so an e-mail wins over an organisation match on
 * its own domain.
 */
public enum PiiType {

    EMAIL(100),
    CREDIT_CARD(95),
    SSN(95),
    IP_ADDRESS(85),
    PHONE(75),
    DATE_OF_BIRTH(70),
    ADDRESS(60),
    ORGANIZATION(50),
    PERSON(45);

    private final int priority;

    PiiType(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
