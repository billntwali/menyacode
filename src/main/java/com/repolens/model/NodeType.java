package com.repolens.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NodeType {
    DIRECTORY, FILE;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static NodeType fromJson(String value) {
        return switch (value.toLowerCase()) {
            case "directory" -> DIRECTORY;
            case "file"      -> FILE;
            default          -> throw new IllegalArgumentException("Unknown node type: " + value);
        };
    }
}
