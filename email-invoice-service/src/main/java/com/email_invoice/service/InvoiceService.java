package com.email_invoice.service;

import com.email_invoice.model.InvoiceRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

    @Autowired
    private PdfService pdfService;

    @Autowired
    private EmailService emailService;

    public void processInvoice(InvoiceRequest request) throws Exception {

        byte[] pdf = pdfService.generateInvoice(request);

        emailService.sendInvoice(request.getEmail(), pdf);
    }
}