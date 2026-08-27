package com.cryptocinema.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cryptocinema.dto.MessageResponse;

@RestController
public class TestAccessController {

    @GetMapping("/api/user/test")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public MessageResponse userTest() {
        return new MessageResponse("USER endpoint accessible");
    }

    @GetMapping("/api/admin/test")
    @PreAuthorize("hasRole('ADMIN')")
    public MessageResponse adminTest() {
        return new MessageResponse("ADMIN endpoint accessible");
    }
}
