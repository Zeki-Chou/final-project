package com.hand.demo.infra.enums;

import java.util.ArrayList;
import java.util.List;

public enum HeaderStatus {
    DRAFT,
    INCOUNTING,
    PROCESSING,
    WITHDRAWN,
    REJECTED,
    APPROVED,
    CONFIRMED;

    public static List<String> validSubmitStatus() {
        List<String> statusList = new ArrayList<>();
        statusList.add(INCOUNTING.name());
        statusList.add(PROCESSING.name());
        statusList.add(REJECTED.name());
        statusList.add(WITHDRAWN.name());
        return statusList;
    }

    public static List<String> validManualUpdateStatus() {
        List<String> statusList = new ArrayList<>();
        statusList.add(DRAFT.name());
        statusList.add(INCOUNTING.name());
        statusList.add(WITHDRAWN.name());
        statusList.add(REJECTED.name());
        return statusList;
    }
}
