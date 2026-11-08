package com.example.webapp.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.Locale;

@Service
public class CaptchaService {
    private static final String KEY = CaptchaService.class.getName();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random;
    private final Clock clock;
    public CaptchaService() { this(new SecureRandom(), Clock.systemUTC()); }
    CaptchaService(SecureRandom random, Clock clock) { this.random = random; this.clock = clock; }
    private record Challenge(byte[] digest, long expiresAt) implements Serializable {}

    public byte[] issue(HttpSession session) throws IOException {
        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < 5; i++) answer.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        synchronized (session) { session.setAttribute(KEY, new Challenge(hash(answer.toString()), clock.millis() + 120_000)); }
        var image = new BufferedImage(220, 72, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(240, 247, 245));g.fillRect(0, 0, 220, 72);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < 12; i++) {
                g.setColor(new Color(150 + random.nextInt(70), 170 + random.nextInt(60), 180 + random.nextInt(50)));
                g.drawLine(random.nextInt(220), random.nextInt(72), random.nextInt(220), random.nextInt(72));
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            for (int i = 0; i < 5; i++) {
                var transform = g.getTransform();
                g.translate(18 + 39 * i, 49 + random.nextInt(7) - 3);
                g.rotate((random.nextInt(25) - 12) * Math.PI / 180);
                g.setColor(new Color(25 + random.nextInt(45), 50 + random.nextInt(50), 60 + random.nextInt(50)));
                g.drawString(answer.substring(i, i + 1), 0, 0);g.setTransform(transform);
            }
        } finally { g.dispose(); }
        var bytes = new ByteArrayOutputStream();ImageIO.write(image, "png", bytes);return bytes.toByteArray();
    }
    public boolean consume(HttpSession session, String answer) {
        if (session == null) return false;
        Challenge challenge;
        synchronized (session) { challenge = (Challenge) session.getAttribute(KEY);session.removeAttribute(KEY); }
        return challenge != null && challenge.expiresAt() > clock.millis() && answer != null && answer.length() <= 10 &&
            MessageDigest.isEqual(challenge.digest(), hash(answer.strip().toUpperCase(Locale.ROOT)));
    }
    private static byte[] hash(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
