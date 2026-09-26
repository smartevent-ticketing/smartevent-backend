package com.smartevent.infrastructure.mail;

import com.smartevent.common.util.QrCodeUtils;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailServiceTest {
    @Test
    void ticketEmailContainsInlineQrAndPngAttachment() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        EmailService service = new EmailService();
        ReflectionTestUtils.setField(service, "mailSender", sender);
        ReflectionTestUtils.setField(service, "senderEmail", "tickets@example.test");
        ReflectionTestUtils.setField(service, "mailEnabled", true);

        byte[] png = QrCodeUtils.generateQrCodeBytes("TCK-QR.test", 120, 120);
        service.sendTicketEmail("buyer@example.test", "TCK-123", "Concert", "A1",
                "data:image/png;base64," + Base64.getEncoder().encodeToString(png));

        verify(sender).send(message);
        message.saveChanges();
        List<BodyPart> parts = new ArrayList<>();
        collectParts((Multipart) message.getContent(), parts);
        assertTrue(parts.stream().anyMatch(part -> {
            try {
                return part.isMimeType("text/html") && part.getContent().toString().contains("cid:ticket-qr");
            } catch (Exception ex) { return false; }
        }));
        assertTrue(parts.stream().anyMatch(part -> {
            try {
                return part.getHeader("Content-ID") != null
                        && part.getHeader("Content-ID")[0].contains("ticket-qr")
                        && part.isMimeType("image/png")
                        && java.util.Arrays.equals(png, part.getInputStream().readAllBytes());
            } catch (Exception ex) { return false; }
        }));
        assertTrue(parts.stream().anyMatch(part -> {
            try {
                return "Ve_TCK-123.png".equals(part.getFileName())
                        && java.util.Arrays.equals(png, part.getInputStream().readAllBytes());
            } catch (Exception ex) { return false; }
        }));
    }

    @Test
    void disabledMailDoesNotClaimSuccess() {
        EmailService service = new EmailService();
        ReflectionTestUtils.setField(service, "mailEnabled", false);
        assertThrows(MailSendException.class,
                () -> service.sendInvoiceEmail("buyer@example.test", "INV-1", java.math.BigDecimal.TEN, "today"));
    }

    private void collectParts(Multipart multipart, List<BodyPart> parts) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.getContent() instanceof Multipart nested) collectParts(nested, parts);
            else parts.add(part);
        }
    }
}
