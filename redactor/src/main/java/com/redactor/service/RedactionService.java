package com.redactor.service;

import com.redactor.detect.PiiDetector;
import com.redactor.model.PiiMatch;
import com.redactor.model.PiiType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs every registered {@link PiiDetector} and turns their combined,
 * overlapping output into one clean list of matches, plus hands out
 * consistent fake replacements.
 *
 * <p>Spring injects {@code List<PiiDetector>} with every {@code @Component}
 * bean that implements the interface - add a new detector class and it shows
 * up here with no other change.
 */
@Service
public class RedactionService {

    private final List<PiiDetector> detectors;
    private final PseudonymGenerator pseudonymGenerator;

    public RedactionService(List<PiiDetector> detectors, PseudonymGenerator pseudonymGenerator) {
        this.detectors = detectors;
        this.pseudonymGenerator = pseudonymGenerator;
    }

    /**
     * Returns every PII match in {@code text}: non-overlapping, sorted
     * left-to-right.
     *
     * <p>Detectors often disagree on the same span - an e-mail's domain can
     * also read as part of a company name. Ties are resolved by preferring
     * the more certain PII type first (see {@link PiiType#priority()}), then
     * the longer match; anything left over that overlaps a match already kept
     * is discarded.
     */
    public List<PiiMatch> findAll(String text) {
        List<PiiMatch> all = new ArrayList<>();
        for (PiiDetector detector : detectors) {
            all.addAll(detector.detect(text));
        }

        all.sort(Comparator
                .comparingInt((PiiMatch m) -> m.type().priority()).reversed()
                .thenComparing(PiiMatch::length, Comparator.reverseOrder()));

        List<PiiMatch> kept = new ArrayList<>();
        for (PiiMatch candidate : all) {
            boolean overlapsKept = kept.stream().anyMatch(k -> k.overlaps(candidate));
            if (!overlapsKept) {
                kept.add(candidate);
            }
        }

        kept.sort(Comparator.comparingInt(PiiMatch::start));
        return kept;
    }

    /** Same real value always maps to the same fake value across the whole document. */
    public String pseudonymFor(PiiType type, String original) {
        return pseudonymGenerator.pseudonymFor(type, original);
    }
}
