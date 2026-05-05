package com.email_invoice.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendInvoice(String toEmail, byte[] pdf) throws Exception {

        MimeMessage message = mailSender.createMimeMessage();

        MimeMessageHelper helper =
                new MimeMessageHelper(message, true);

        helper.setTo(toEmail);
        helper.setSubject("Your Ticket Invoice");
        helper.setText("Please find your invoice attached.");

        helper.addAttachment(
                "invoice.pdf",
                new ByteArrayResource(pdf)
        );

        mailSender.send(message);
    }
}