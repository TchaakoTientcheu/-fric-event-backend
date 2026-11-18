package com.africevent.backend.service;

import com.africevent.backend.model.Event;
import com.africevent.backend.model.IssuedTicket;
import com.africevent.backend.model.Order;
import com.africevent.backend.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Envoi des emails : code de verification (synchrone, pour informer le client en cas d'echec)
 * et billets avec QR codes (asynchrone, un echec ne remet jamais en cause un paiement).
 * Aucune adresse ni aucun code n'est ecrit dans les journaux.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);
    private static final String MSG_UNAVAILABLE = "L'envoi d'email n'est pas disponible pour le moment. Veuillez réessayer.";

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final QrCodeService qrCodeService;

    @Value("${app.mail.from:${spring.mail.username:}}")
    private String from;

    @Value("${app.mail.brand:Afric Event}")
    private String brand;

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    /* ==============================
       CODE DE VERIFICATION
    ============================== */
    public void sendOtp(String to, String code, int validityMinutes) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.error("Envoi d'email impossible : serveur SMTP non configuré");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MSG_UNAVAILABLE);
        }

        String text = "Votre code de vérification " + brand + " est : " + code + "\n\n"
                + "Ce code est valable " + validityMinutes + " minutes et ne peut être utilisé qu'une seule fois.\n"
                + "Si vous n'êtes pas à l'origine de cette demande, ignorez simplement ce message.";

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            applyFrom(helper);
            helper.setTo(to);
            helper.setSubject("Votre code de vérification " + brand);
            helper.setText(text, false);
            sender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("Échec d'envoi du code de vérification : {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MSG_UNAVAILABLE);
        }
    }

    /* ==============================
       BILLETS
    ============================== */
    @Async
    public void sendTickets(User user, Order order, Event event, List<IssuedTicket> tickets) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("Billets de la commande {} non envoyés : serveur SMTP non configuré", order.getId());
            return;
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            applyFrom(helper);
            helper.setTo(user.getEmail());
            String titre = event != null && event.getTitre() != null ? event.getTitre() : "votre événement";
            helper.setSubject("Vos billets : " + titre);
            helper.setText(buildTicketsHtml(user, order, event, tickets.size()), true);

            for (int i = 0; i < tickets.size(); i++) {
                byte[] png = decodeDataUri(qrCodeService.toDataUri(tickets.get(i).getCodeQrUnique()));
                helper.addInline("qr" + i, new ByteArrayResource(png), "image/png");
            }
            sender.send(message);
        } catch (MessagingException | RuntimeException e) {
            log.error("Échec d'envoi des billets de la commande {} : {}", order.getId(), e.getClass().getSimpleName());
        }
    }

    private String buildTicketsHtml(User user, Order order, Event event, int count) {
        String titre = event != null ? safe(event.getTitre()) : "";
        String lieu = event != null ? safe(event.getLieu()) : "";
        String date = event != null && event.getDateEvent() != null ? event.getDateEvent().format(DATE_FORMAT) : "";
        String type = safe(order.getTicketTypeNom());

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;color:#0b1c30\">");
        html.append("<h2 style=\"color:#00897b\">").append(HtmlUtils.htmlEscape(brand)).append("</h2>");
        html.append("<p>Bonjour ").append(safe(user.getNom())).append(",</p>");
        html.append("<p>Votre paiement est confirmé. Voici vos billets pour <strong>").append(titre).append("</strong>.</p>");
        html.append("<p>");
        if (!date.isEmpty()) {
            html.append("Date : ").append(date).append("<br>");
        }
        if (!lieu.isEmpty()) {
            html.append("Lieu : ").append(lieu).append("<br>");
        }
        if (!type.isEmpty()) {
            html.append("Type de billet : ").append(type).append("<br>");
        }
        html.append("Référence : ").append(safe(order.getReferenceTransaction())).append("</p>");

        for (int i = 0; i < count; i++) {
            html.append("<div style=\"border:1px solid #dce9ff;border-radius:12px;padding:16px;margin:16px 0;text-align:center\">");
            html.append("<p style=\"margin:0 0 8px;font-weight:bold\">Billet ").append(i + 1).append(" sur ").append(count).append("</p>");
            html.append("<img src=\"cid:qr").append(i).append("\" width=\"200\" height=\"200\" alt=\"QR code du billet\">");
            html.append("<p style=\"margin:8px 0 0;font-size:12px;color:#5b6b7f\">Présentez ce code à l'entrée.</p>");
            html.append("</div>");
        }

        html.append("<p>Retrouvez vos billets à tout moment : <a href=\"").append(ticketsUrl()).append("\">mes billets</a>.</p>");
        html.append("</div>");
        return html.toString();
    }

    /* ==============================
       OUTILS
    ============================== */
    private void applyFrom(MimeMessageHelper helper) throws MessagingException {
        if (StringUtils.hasText(from)) {
            helper.setFrom(from);
        }
    }

    private String ticketsUrl() {
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        return HtmlUtils.htmlEscape(base + "/my-tickets");
    }

    private String safe(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    private byte[] decodeDataUri(String dataUri) {
        return Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1));
    }
}