package com.email_invoice.service;

import com.email_invoice.model.InvoiceRequest;
import com.email_invoice.model.Item;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class PdfService {

    public byte[] generateInvoice(InvoiceRequest request) throws Exception {

        Document document = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        PdfWriter.getInstance(document, out);
        document.open();

        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);

        document.add(new Paragraph("Ticket Invoice", titleFont));
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Customer Name: " + request.getName()));
        document.add(new Paragraph("Pickup: " + request.getPickupLocation()));
        document.add(new Paragraph("Drop: " + request.getDropLocation()));
        document.add(new Paragraph("Distance: " + request.getDistanceTravelled() + " km"));

        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(4);
        table.addCell("Item");
        table.addCell("Quantity");
        table.addCell("Price");
        table.addCell("Total");

        double grandTotal = 0;

        for (Item item : request.getItems()) {

            double total = item.getQuantity() * item.getPrice();
            grandTotal += total;

            table.addCell(item.getItemName());
            table.addCell(String.valueOf(item.getQuantity()));
            table.addCell(String.valueOf(item.getPrice()));
            table.addCell(String.valueOf(total));
        }

        document.add(table);

        document.add(new Paragraph(" "));
        document.add(new Paragraph("Grand Total: ₹ " + grandTotal));

        document.close();

        return out.toByteArray();
    }
}