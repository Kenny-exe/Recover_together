package com.recovertogether.backend.dto;

public class SupportRequestDto {

    private String note;

    public SupportRequestDto() {
    }

    public SupportRequestDto(String note) {
        this.note = note;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
