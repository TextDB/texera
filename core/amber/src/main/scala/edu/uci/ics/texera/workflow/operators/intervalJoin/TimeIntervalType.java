package edu.uci.ics.texera.workflow.operators.intervalJoin;

import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serializable;


public enum TimeIntervalType implements Serializable {
    YEAR("year"),
    MONTH("month"),
    DAY("day"),
    HOUR("hour"),
    MINUTE("minute"),
    SECOND("second");
    private final String name;

    TimeIntervalType(String name) {
        this.name = name;
    }

    @JsonValue
    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.getName();
    }
}


