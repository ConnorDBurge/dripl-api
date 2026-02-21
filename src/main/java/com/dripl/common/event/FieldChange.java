package com.dripl.common.event;

public record FieldChange(String field, Object oldValue, Object newValue) {
}
