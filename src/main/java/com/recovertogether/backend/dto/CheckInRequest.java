package com.recovertogether.backend.dto;

import com.recovertogether.backend.enums.CheckInStatus;

public class CheckInRequest
{
    private CheckInStatus status;
    private String note;

    public CheckInStatus getStatus(){return status;}

    public void setStatus(CheckInStatus status) {this.status = status;}

    public String getNote() {return note;}

    public void setNote(String note) {this.note = note;}
}
