package com.smd.grimreaper.enums;

public enum EnumReaperAttackState {
    IDLE(0),
    PRE(1),
    POST(2),
    BLOCK(3);

    private final int id;

    EnumReaperAttackState(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    public static EnumReaperAttackState fromId(int id) {
        for (EnumReaperAttackState state : values()) {
            if (state.getId() == id) {
                return state;
            }
        }
        return IDLE;
    }
}