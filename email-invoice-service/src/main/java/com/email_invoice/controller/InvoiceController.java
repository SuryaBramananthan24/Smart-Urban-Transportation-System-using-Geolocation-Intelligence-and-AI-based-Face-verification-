package com.email_invoice.controller;

import com.email_invoice.model.InvoiceRequest;
import com.email_invoice.service.InvoiceService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invoice")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping(consumes = "application/json")
    public String generateInvoice(@RequestBody InvoiceRequest request) throws Exception {

        invoiceService.processInvoice(request);

        return "Invoice generated and sent successfully!";
    }
}