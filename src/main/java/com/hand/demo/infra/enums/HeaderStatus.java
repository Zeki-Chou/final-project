package com.hand.demo.infra.enums;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum HeaderStatus {
    DRAFT,
    INCOUNTING,
    PROCESSING,
    WITHDRAWN,
    REJECTED,
    APPROVED,
    CONFIRMED;

    public static List<String> validSubmitStatus() {
        return Stream.of(HeaderStatus.values())
                .filter(status ->   status.equals(INCOUNTING) ||
                                    status.equals(PROCESSING) ||
                                    status.equals(REJECTED) ||
                                    status.equals(WITHDRAWN))
                .map(Enum::toString)
                .collect(Collectors.toList());
    }

    public static List<String> validManualUpdateStatus() {
        return Stream.of(HeaderStatus.values())
                .filter(status ->   status.equals(DRAFT) ||
                                    status.equals(INCOUNTING) ||
                                    status.equals(WITHDRAWN) ||
                                    status.equals(REJECTED))
                .map(Enum::toString)
                .collect(Collectors.toList());
    }
}
