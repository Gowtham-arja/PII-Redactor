package com.redactor.detect;

import com.redactor.model.PiiMatch;
import com.redactor.model.PiiType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrganizationDetector implements PiiDetector {

    private final EntityGazetteer gazetteer;

    public OrganizationDetector(EntityGazetteer gazetteer) {
        this.gazetteer = gazetteer;
    }

    @Override
    public PiiType type() {
        return PiiType.ORGANIZATION;
    }

    @Override
    public List<PiiMatch> detect(String text) {
        return gazetteer.findOrganisations(text);
    }
}
