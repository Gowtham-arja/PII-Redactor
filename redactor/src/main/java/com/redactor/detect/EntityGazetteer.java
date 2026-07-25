package com.redactor.detect;

import com.redactor.model.PiiMatch;
import com.redactor.model.PiiType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Learns the person and company names the document introduces about itself,
 * so they can be found again anywhere in the document - even far from the one
 * sentence that first named them.
 *
 * <p>Why not just run a generic name-recognition model? A Red Herring
 * Prospectus is a highly structured legal document: it introduces its people
 * and companies in a handful of fixed phrasings - "Contact Person: X",
 * "Mr. X", "X Private Limited". Reading those introductions is far more
 * reliable here than guessing from surrounding words the way a generic model
 * would.
 *
 * <p>Two-pass use: call {@link #prime} once with the whole document's text,
 * then {@link #findPersons} / {@link #findOrganisations} per paragraph.
 * Matches come back longest-name-first, so "Kushal Subbayya Hegde" is
 * consumed before the shorter "Kushal Hegde" that could otherwise be replaced
 * as if it were a different person.
 */
@Component
public class EntityGazetteer {

    private static final String NAME_TOKEN = "[A-Z][A-Za-z'\\-]+";
    private static final String PERSON_NAME = NAME_TOKEN + "(?:\\s+" + NAME_TOKEN + "){1,3}";

    private static final List<Pattern> PERSON_CUES = List.of(
            Pattern.compile("Contact\\s+Person\\s*[:\\-]\\s*(" + PERSON_NAME + ")"),
            Pattern.compile("Compliance\\s+Officer\\s*[,:]\\s*(" + PERSON_NAME + ")"),
            Pattern.compile("(?:Mr|Ms|Mrs|Dr|Shri|Smt)\\.?\\s+(" + PERSON_NAME + ")"),
            Pattern.compile("\\bbeing\\s+(" + PERSON_NAME + ")\\b")
    );

    private static final Pattern ORGANISATION_CUE = Pattern.compile(
            "\\b((?:" + NAME_TOKEN + "|&|of|and|the)"
          + "(?:\\s+(?:" + NAME_TOKEN + "|&|of|and|the)){0,6}"
          + "\\s+(?:Private\\s+Limited|Limited|LLP|Trust|Ltd\\.?))\\b");

    private static final Set<String> ORG_STOPWORDS = Set.of(
            "the company", "our company", "a company", "the issuer",
            "the trust", "a trust", "our trust", "family trust");

    private final Set<String> persons = new LinkedHashSet<>();
    private final Set<String> organisations = new LinkedHashSet<>();

    private List<String> sortedPersons = List.of();
    private List<String> sortedOrganisations = List.of();

    /** Pass A - call once with the full document text before redacting anything. */
    public synchronized void prime(String corpus) {
        persons.clear();
        organisations.clear();

        for (Pattern cue : PERSON_CUES) {
            Matcher m = cue.matcher(corpus);
            while (m.find()) {
                addPerson(m.group(1));
            }
        }

        Matcher org = ORGANISATION_CUE.matcher(corpus);
        while (org.find()) {
            addOrganisation(org.group(1));
        }

        sortedPersons = longestFirst(persons);
        sortedOrganisations = longestFirst(organisations);
    }

    /** Pass B - find every harvested person name inside one block of text. */
    public List<PiiMatch> findPersons(String text) {
        return findFromList(text, sortedPersons, PiiType.PERSON);
    }

    /** Pass B - find every harvested organisation name inside one block of text. */
    public List<PiiMatch> findOrganisations(String text) {
        return findFromList(text, sortedOrganisations, PiiType.ORGANIZATION);
    }

    private List<PiiMatch> findFromList(String text, List<String> names, PiiType type) {
        List<PiiMatch> matches = new ArrayList<>();
        for (String name : names) {
            Matcher m = Pattern.compile("\\b" + Pattern.quote(name) + "\\b", Pattern.CASE_INSENSITIVE)
                                .matcher(text);
            while (m.find()) {
                matches.add(new PiiMatch(m.start(), m.end(), type, m.group()));
            }
        }
        return matches;
    }

    private void addPerson(String raw) {
        String name = normaliseWhitespace(raw);
        String[] parts = name.split("\\s+");
        if (parts.length < 2) {
            return;
        }
        persons.add(name);
        if (parts.length >= 3) {
            // "Kushal Subbayya Hegde" also appears shortened to "Kushal Hegde" later on.
            persons.add(parts[0] + " " + parts[parts.length - 1]);
        }
    }

    private void addOrganisation(String raw) {
        String name = normaliseWhitespace(raw);
        if (name.length() < 6 || ORG_STOPWORDS.contains(name.toLowerCase(Locale.ROOT))) {
            return;
        }
        organisations.add(name);
    }

    private static String normaliseWhitespace(String s) {
        return s.replaceAll("\\s+", " ").strip();
    }

    private static List<String> longestFirst(Set<String> names) {
        return names.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }
}
