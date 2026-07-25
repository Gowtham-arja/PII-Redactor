package com.redactor.detect;

import com.redactor.model.PiiType;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Dates of birth only, not dates in general. The document has ~200 unrelated
 * dates (filings, board resolutions); redacting all of them would wreck the
 * document for no privacy benefit. This only fires within ~40 characters after
 * a birth cue such as "date of birth" or "born on" - group 1 is the date
 * itself, so the cue text is left in place.
 *
 * <p>Consequence, stated plainly: a birth date with no nearby cue is a missed
 * case by design. That trade-off is explained in the README.
 */
@Component
public class DateOfBirthDetector extends AbstractRegexDetector {

    private static final String MONTHS =
            "January|February|March|April|May|June|July|August|September|October|November|December";

    private static final String DATE =
            "(?:\\d{1,2}\\s+(?:" + MONTHS + ")\\s+\\d{4}"
          + "|(?:" + MONTHS + ")\\s+\\d{1,2},?\\s+\\d{4}"
          + "|\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4})";

    public DateOfBirthDetector() {
        super(Pattern.compile(
                "(?i)(?:date\\s+of\\s+birth|born\\s+on|born|D\\.?O\\.?B\\.?)"
              + "[^.\\n]{0,40}?(" + DATE + ")"));
    }

    @Override
    public PiiType type() {
        return PiiType.DATE_OF_BIRTH;
    }

    @Override
    protected int captureGroup() {
        return 1;
    }
}
