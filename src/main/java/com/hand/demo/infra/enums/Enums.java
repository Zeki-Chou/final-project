package com.hand.demo.infra.enums;

public class Enums {

    private Enums() {}

    public static class InvCountHeader {
        public enum Status {
            DRAFT,
            INCOUNTING,
            PROCESSING,
            WITHDRAWN,
            REJECTED
        }
    }
}
