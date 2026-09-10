package com.cryptocinema.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cryptocinema.dto.AdminTicketResponse;
import com.cryptocinema.dto.TicketValidationResponse;
import com.cryptocinema.service.TicketService;

@RestController
@RequestMapping("/api/admin/tickets")
public class AdminTicketController {

    private final TicketService ticketService;

    public AdminTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/{ticketCode}")
    public AdminTicketResponse inspect(@PathVariable String ticketCode) {
        return ticketService.inspect(ticketCode);
    }

    @PostMapping("/{ticketCode}/validate")
    public TicketValidationResponse validate(@PathVariable String ticketCode) {
        return ticketService.validate(ticketCode);
    }
}
