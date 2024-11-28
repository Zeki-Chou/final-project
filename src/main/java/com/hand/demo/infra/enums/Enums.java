package com.hand.demo.infra.enums;

public class Enums {

    private Enums() {}

    public static class Extra {
        public enum WmsStatus {
            SUCCESS,
            ERROR,
            SKIP
        }
    }

    public static class InvCountHeader {
        public enum Status {
            DRAFT,
            INCOUNTING,
            PROCESSING,
            WITHDRAWN,
            REJECTED,
            APPROVED,
            CONFIRMED
        }

        public enum countDimension {
            SKU,
            LOT
        }

        public enum countType {
            MONTH,
            YEAR
        }

        public enum countMode {
            VISIBLE_COUNT,
            UNVISIBLE_COUNT
        }

        public enum programValue {
            SUCCESS,
            ERROR,
            SKIP
        }
    }
}
