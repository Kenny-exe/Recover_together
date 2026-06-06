package com.recovertogether.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MessageRequest
{
    @NotBlank
    @Size(max = 1000)
    private String content;

    public String getContent() {return content;}

    public void setContent(String content) {this.content = content;}
}
