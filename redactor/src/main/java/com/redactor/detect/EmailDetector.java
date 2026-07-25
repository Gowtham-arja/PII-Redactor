package com.redactor.detect;

import com.redactor.model.PiiType;
import org.springframework.stereotype.Component;

@Component
public class EmailDetector extends AbstractRegexDetector {

    public EmailDetector() {
        super("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    }

    @Override
    public PiiType type() {
        return PiiType.EMAIL;
    }
}
