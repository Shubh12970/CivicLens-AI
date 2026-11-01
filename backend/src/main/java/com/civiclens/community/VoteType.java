package com.civiclens.community;

/** UP = "I am affected / this matters" (support). DOWN = "not an issue / already fixed". */
public enum VoteType {
    UP(1),
    DOWN(-1);

    private final int value;

    VoteType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static VoteType fromValue(Integer value) {
        if (value == null) {
            return null;
        }
        return value > 0 ? UP : DOWN;
    }
}
