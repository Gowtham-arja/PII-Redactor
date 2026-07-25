package com.redactor.detect;

import com.redactor.model.PiiMatch;
import com.redactor.model.PiiType;

import java.util.List;

/**
 * The one extension point in the project. Every implementation is a Spring
 * {@code @Component}; {@link com.redactor.service.RedactionService} injects
 * {@code List<PiiDetector>} and Spring hands it every bean that implements
 * this interface — so a new PII type is one new class, nothing wired by hand.
 */
public interface PiiDetector {

    /** The category of PII this detector produces. */
    PiiType type();

    /** Find every occurrence in {@code text}. Offsets are relative to {@code text}. */
    List<PiiMatch> detect(String text);
}
