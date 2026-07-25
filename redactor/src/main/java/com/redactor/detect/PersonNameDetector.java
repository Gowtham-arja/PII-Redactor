package com.redactor.detect;

import com.redactor.model.PiiMatch;
import com.redactor.model.PiiType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PersonNameDetector implements PiiDetector {

    private final EntityGazetteer gazetteer;

    public PersonNameDetector(EntityGazetteer gazetteer) {
        this.gazetteer = gazetteer;
    }

    @Override
    public PiiType type() {
        return PiiType.PERSON;
    }

    @Override
    public List<PiiMatch> detect(String text) {
        return gazetteer.findPersons(text);
    }
}
