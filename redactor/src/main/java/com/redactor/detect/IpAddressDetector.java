package com.redactor.detect;

import com.redactor.model.PiiType;
import org.springframework.stereotype.Component;

/**
 * IPv4 addresses. The octet-range check in {@link #accept} is what stops this
 * from matching version strings like "2.16.1.3", which look identical to the
 * regex alone.
 */
@Component
public class IpAddressDetector extends AbstractRegexDetector {

    public IpAddressDetector() {
        super("(?<![\\d.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\d.])");
    }

    @Override
    public PiiType type() {
        return PiiType.IP_ADDRESS;
    }

    @Override
    protected boolean accept(String value, String fullText, int start, int end) {
        String[] octets = value.split("\\.");
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.length() > 1 && octet.charAt(0) == '0') {
                return false;
            }
            int n = Integer.parseInt(octet);
            if (n > 255) {
                return false;
            }
        }
        return true;
    }
}
