package com.dockflow.dockflow.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record DocumentRegistrationRequest(

    @NotBlank 
    String originalFilename,

    @NotBlank
    String contentType,

    @PositiveOrZero
    long sizeBytes
    
) {

}
