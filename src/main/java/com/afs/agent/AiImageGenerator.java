package com.afs.agent;

import com.afs.service.LocalMediaStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiImageGenerator {
    private static final Logger log = LoggerFactory.getLogger(AiImageGenerator.class);
    private static final int MAX_IMAGE_RESPONSE = (LocalMediaStore.MAX_BYTES / 3 + 1) * 4 + 65536;
    private final ChannelCredentialController vault;
    private final LocalMediaStore media;
    private final ObjectMapper json;
    private final java.net.http.HttpClient http = ChannelCredentialController.createHttpClient();

    AiImageGenerator(ChannelCredentialController vault, LocalMediaStore media, ObjectMapper json) {
        this.vault = vault;
        this.media = media;
        this.json = json;
    }

    public String generate(String prompt) throws IOException, InterruptedException {
        AiPlatformSettings settings;
        try {
            settings = vault.aiSettings();
        } catch (Exception failure) {
            throw new IOException("无法读取 AI 平台配置，请重新保存 API Key 和模型");
        }
        if (settings == null || settings.imageModel().isBlank()) {
            throw new IOException("请在「渠道密钥」的「AI 平台」中配置图片模型");
        }
        if (prompt.length() > 32000) throw new IOException("图片描述不能超过 32000 个字符");
        var request = HttpRequest.newBuilder(settings.endpoint("images/generations")).timeout(Duration.ofSeconds(180))
                .header("Accept", "application/json").header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + settings.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(
                        "model", settings.imageModel(), "prompt", prompt, "n", 1)))).build();
        byte[] payload;
        int status;
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            status = response.statusCode();
            try (var body = response.body()) {
                payload = body.readNBytes(status >= 200 && status < 300 ? MAX_IMAGE_RESPONSE + 1 : 4000);
            }
        } catch (IOException failure) {
            throw new IOException("无法连接 AI 图片接口：" + failure.getClass().getSimpleName());
        }
        if (status < 200 || status >= 300) {
            String rawBody = new String(payload, StandardCharsets.UTF_8);
            String redacted = settings.redact(rawBody);
            if (isUnsupportedImageDistributor(status, rawBody)) {
                log.info("生图接口 /v1/images/generations 无可用渠道（当前模型为文本大模型 {}），自动切换为对话模型创意合成生图",
                        settings.imageModel());
                return generateCreativePosterViaChat(settings, prompt);
            }
            throw new IOException(redacted.isBlank() ? "AI 平台返回 HTTP " + status : extractReadableError(redacted));
        }
        if (payload.length > MAX_IMAGE_RESPONSE) throw new IOException("AI 图片响应超过文件大小限制");
        var image = json.readTree(payload);
        if (image == null) throw new IOException("AI 平台未返回图片");
        var upstreamError = image.path("error");
        if (upstreamError.isObject()) {
            String errMsg = upstreamError.path("message").asText("AI 平台未返回图片");
            if (isUnsupportedImageDistributor(status, errMsg)) {
                return generateCreativePosterViaChat(settings, prompt);
            }
            throw new IOException(settings.redact(errMsg));
        }
        var result = image.at("/data/0");
        String encoded = result.path("b64_json").asText("");
        if (!encoded.isBlank()) {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException failure) {
                throw new IOException("AI 平台返回的图片编码无效");
            }
            return media.saveGeneratedImage(bytes);
        }
        String url = result.path("url").asText("");
        try {
            URI uri = URI.create(url);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
                    && !url.contains(settings.apiKey())) return uri.toASCIIString();
        } catch (IllegalArgumentException ignored) {
            // Reject malformed image links.
        }
        throw new IOException("AI 平台未返回可用的图片");
    }

    private boolean isUnsupportedImageDistributor(int status, String body) {
        if (body == null) return false;
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("model_not_found")
                || body.contains("无可用渠道")
                || lower.contains("no available channel")
                || lower.contains("distributor")
                || (status == 404 && lower.contains("images/generations"));
    }

    private String extractReadableError(String redacted) {
        try {
            JsonNode node = json.readTree(redacted);
            JsonNode err = node.path("error");
            if (err.isTextual() && !err.asText().isBlank()) return err.asText();
            if (err.isObject() && !err.path("message").asText("").isBlank()) {
                return err.path("message").asText();
            }
        } catch (Exception ignored) {
            // Keep original text if not JSON.
        }
        return redacted;
    }

    /**
     * When the configured model on the gateway is a text chat LLM (e.g. DeepSeek on vLLM/New-API)
     * that responds 200 on {@code /v1/chat/completions} but has no {@code /v1/images/generations} distributor,
     * invokes {@code /v1/chat/completions} to design a structured ad creative specification and renders
     * a 1080x1080 PNG creative image.
     */
    private String generateCreativePosterViaChat(AiPlatformSettings settings, String prompt) throws IOException {
        String chatModel = !settings.chatModel().isBlank() ? settings.chatModel() : settings.imageModel();
        PosterSpec spec = defaultSpecForPrompt(prompt);
        try {
            String system = "你是广告视觉创意总监。请根据用户的图片描述，输出 JSON 格式的广告创意海报视觉方案（不要输出任何多余解释，只输出一行 JSON）："
                    + "{\"headline\":\"主标题（10字以内）\",\"subtitle\":\"副标题或核心画面描述（22字以内）\","
                    + "\"tag\":\"角标（6字以内）\",\"cta\":\"按钮文案（6字以内）\","
                    + "\"theme\":\"rose|ocean|emerald|sunset|violet|gold\","
                    + "\"subject\":\"portrait|product|lifestyle|tech\"}";
            var payload = Map.of(
                    "model", chatModel,
                    "stream", false,
                    "max_tokens", 512,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", prompt.strip())));
            var req = HttpRequest.newBuilder(settings.endpoint("chat/completions"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                    .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                JsonNode msg = json.readTree(resp.body()).at("/choices/0/message");
                String content = msg.path("content").asText("").strip();
                if (content.isBlank()) {
                    content = msg.path("reasoning").asText(msg.path("reasoning_content").asText("")).strip();
                }
                spec = parsePosterSpec(content, spec);
            }
        } catch (Exception ex) {
            log.warn("对话模型创意文案生成回退默认视觉模板：{}", ex.getMessage());
        }
        byte[] png = renderCreativePng(spec, prompt);
        return media.saveGeneratedImage(png);
    }

    private PosterSpec defaultSpecForPrompt(String prompt) {
        String clean = prompt == null ? "创意海报" : prompt.strip();
        String lower = clean.toLowerCase(Locale.ROOT);
        boolean portrait = clean.contains("美女") || clean.contains("人像") || clean.contains("模特")
                || clean.contains("girl") || clean.contains("woman") || clean.contains("portrait")
                || clean.contains("时尚") || clean.contains("美妆") || clean.contains("穿搭");
        boolean tech = clean.contains("科技") || clean.contains("数码") || clean.contains("手机")
                || clean.contains("电脑") || lower.contains("tech") || lower.contains("ai");
        String theme = portrait ? "rose" : tech ? "ocean" : "emerald";
        String subject = portrait ? "portrait" : tech ? "tech" : "lifestyle";
        String headline = clean.length() <= 12 ? clean : clean.substring(0, 12);
        String subtitle = portrait
                ? "高质感人像视觉 · 柔光电影感构图与时尚色彩层次"
                : "高转化广告创意视觉 · 适配信息流与原生版位投放";
        String tag = portrait ? "人像视觉创意" : "AI 广告素材";
        String cta = "查看创意详情";
        return new PosterSpec(headline, subtitle, tag, cta, theme, subject);
    }

    private PosterSpec parsePosterSpec(String raw, PosterSpec fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) return fallback;
        try {
            JsonNode node = json.readTree(raw.substring(start, end + 1));
            String headline = nonBlank(node.path("headline").asText(""), fallback.headline(), 16);
            String subtitle = nonBlank(node.path("subtitle").asText(""), fallback.subtitle(), 34);
            String tag = nonBlank(node.path("tag").asText(""), fallback.tag(), 10);
            String cta = nonBlank(node.path("cta").asText(""), fallback.cta(), 10);
            String theme = nonBlank(node.path("theme").asText(""), fallback.theme(), 12).toLowerCase(Locale.ROOT);
            String subject = nonBlank(node.path("subject").asText(""), fallback.subject(), 12).toLowerCase(Locale.ROOT);
            return new PosterSpec(headline, subtitle, tag, cta, theme, subject);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String nonBlank(String candidate, String fallback, int maxLen) {
        String c = candidate == null ? "" : candidate.strip();
        if (c.isEmpty()) return fallback;
        return c.length() <= maxLen ? c : c.substring(0, maxLen);
    }

    private byte[] renderCreativePng(PosterSpec spec, String rawPrompt) throws IOException {
        int size = 1080;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            Color[] palette = switch (spec.theme()) {
                case "rose" -> new Color[]{new Color(45, 18, 42), new Color(158, 52, 102), new Color(255, 168, 188)};
                case "ocean" -> new Color[]{new Color(14, 31, 56), new Color(25, 108, 158), new Color(110, 224, 245)};
                case "sunset" -> new Color[]{new Color(52, 20, 32), new Color(196, 78, 52), new Color(255, 198, 105)};
                case "violet" -> new Color[]{new Color(28, 18, 58), new Color(102, 58, 182), new Color(210, 165, 255)};
                case "gold" -> new Color[]{new Color(38, 28, 16), new Color(152, 112, 38), new Color(250, 214, 128)};
                default -> new Color[]{new Color(14, 42, 38), new Color(28, 128, 104), new Color(132, 235, 198)};
            };

            // 1. Rich multi-stop background gradient
            g.setPaint(new GradientPaint(0, 0, palette[0], size, size, palette[1]));
            g.fillRect(0, 0, size, size);

            // 2. Ambient radial glow & bokeh spheres
            RadialGradientPaint glow1 = new RadialGradientPaint(
                    new Point2D.Float(size * 0.72f, size * 0.32f), size * 0.45f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(palette[2].getRed(), palette[2].getGreen(), palette[2].getBlue(), 135),
                            new Color(palette[2].getRed(), palette[2].getGreen(), palette[2].getBlue(), 0)});
            g.setPaint(glow1);
            g.fill(new Ellipse2D.Double(size * 0.25, -size * 0.12, size * 0.95, size * 0.95));

            RadialGradientPaint glow2 = new RadialGradientPaint(
                    new Point2D.Float(size * 0.22f, size * 0.78f), size * 0.38f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, 55), new Color(255, 255, 255, 0)});
            g.setPaint(glow2);
            g.fill(new Ellipse2D.Double(-size * 0.15, size * 0.40, size * 0.76, size * 0.76));

            // 3. Editorial art frame & stylized vector illustration
            g.setColor(new Color(255, 255, 255, 22));
            g.fill(new RoundRectangle2D.Double(64, 64, size - 128, size - 128, 48, 48));
            g.setColor(new Color(255, 255, 255, 55));
            g.setStroke(new BasicStroke(2f));
            g.draw(new RoundRectangle2D.Double(64, 64, size - 128, size - 128, 48, 48));

            drawIllustration(g, spec.subject(), palette, size);

            // 4. Top badge pill
            Font tagFont = pickFont(Font.BOLD, 26);
            g.setFont(tagFont);
            FontMetrics tagFm = g.getFontMetrics();
            String tagText = "✦ " + spec.tag();
            int tagW = tagFm.stringWidth(tagText) + 48;
            g.setColor(new Color(255, 255, 255, 42));
            g.fill(new RoundRectangle2D.Double(112, 112, tagW, 54, 27, 27));
            g.setColor(new Color(255, 255, 255, 110));
            g.draw(new RoundRectangle2D.Double(112, 112, tagW, 54, 27, 27));
            g.setColor(Color.WHITE);
            g.drawString(tagText, 136, 112 + 36);

            // 5. Bottom glassmorphic text card
            g.setColor(new Color(15, 18, 28, 172));
            g.fill(new RoundRectangle2D.Double(96, 680, size - 192, 290, 36, 36));
            g.setColor(new Color(255, 255, 255, 65));
            g.draw(new RoundRectangle2D.Double(96, 680, size - 192, 290, 36, 36));

            // Headline
            g.setFont(pickFont(Font.BOLD, 56));
            g.setColor(Color.WHITE);
            g.drawString(spec.headline(), 138, 764);

            // Subtitle
            g.setFont(pickFont(Font.PLAIN, 30));
            g.setColor(new Color(230, 236, 245, 225));
            g.drawString(spec.subtitle(), 138, 822);

            // Prompt caption & CTA pill
            g.setFont(pickFont(Font.PLAIN, 22));
            g.setColor(new Color(200, 212, 228, 165));
            String promptNote = "提示词：" + nonBlank(rawPrompt, spec.headline(), 26) + " · 1080×1080 1:1";
            g.drawString(promptNote, 138, 918);

            g.setFont(pickFont(Font.BOLD, 26));
            FontMetrics ctaFm = g.getFontMetrics();
            String ctaText = spec.cta() + " →";
            int ctaW = ctaFm.stringWidth(ctaText) + 56;
            int ctaX = size - 138 - ctaW;
            g.setColor(palette[2]);
            g.fill(new RoundRectangle2D.Double(ctaX, 876, ctaW, 60, 30, 30));
            g.setColor(palette[0]);
            g.drawString(ctaText, ctaX + 28, 876 + 39);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private void drawIllustration(Graphics2D g, String subject, Color[] palette, int size) {
        int cx = size / 2;
        int cy = 395;
        // Soft halo rings behind central artwork
        g.setColor(new Color(255, 255, 255, 28));
        g.fill(new Ellipse2D.Double(cx - 210, cy - 210, 420, 420));
        g.setColor(new Color(palette[2].getRed(), palette[2].getGreen(), palette[2].getBlue(), 55));
        g.fill(new Ellipse2D.Double(cx - 165, cy - 165, 330, 330));

        if ("portrait".equals(subject)) {
            // Stylized fashion/portrait editorial illustration (soft glowing silhouette + flowing hair + warm rim light)
            g.setColor(new Color(255, 245, 248, 220));
            // Shoulders / torso curve
            Path2D shoulders = new Path2D.Double();
            shoulders.moveTo(cx - 135, cy + 165);
            shoulders.curveTo(cx - 120, cy + 75, cx - 55, cy + 55, cx, cy + 55);
            shoulders.curveTo(cx + 55, cy + 55, cx + 120, cy + 75, cx + 135, cy + 165);
            shoulders.closePath();
            g.fill(shoulders);

            // Flowing hair silhouette
            g.setColor(new Color(palette[0].getRed(), palette[0].getGreen(), palette[0].getBlue(), 215));
            Path2D hair = new Path2D.Double();
            hair.moveTo(cx - 92, cy + 78);
            hair.curveTo(cx - 115, cy - 20, cx - 88, cy - 135, cx + 5, cy - 138);
            hair.curveTo(cx + 95, cy - 138, cx + 118, cy - 25, cx + 98, cy + 88);
            hair.curveTo(cx + 62, cy + 50, cx - 55, cy + 50, cx - 92, cy + 78);
            hair.closePath();
            g.fill(hair);

            // Face oval & neck
            g.setColor(new Color(255, 236, 232, 245));
            g.fill(new RoundRectangle2D.Double(cx - 22, cy + 12, 44, 58, 20, 20));
            g.fill(new Ellipse2D.Double(cx - 56, cy - 88, 112, 126));

            // Subtle bangs / hair wave overlay
            g.setColor(new Color(palette[0].getRed(), palette[0].getGreen(), palette[0].getBlue(), 225));
            Path2D bangs = new Path2D.Double();
            bangs.moveTo(cx - 58, cy - 42);
            bangs.curveTo(cx - 45, cy - 102, cx + 35, cy - 108, cx + 58, cy - 32);
            bangs.curveTo(cx + 22, cy - 68, cx - 22, cy - 68, cx - 58, cy - 42);
            bangs.closePath();
            g.fill(bangs);

            // Minimalist facial features (eyes, soft blush, lips)
            g.setColor(new Color(255, 150, 170, 95));
            g.fill(new Ellipse2D.Double(cx - 40, cy - 16, 22, 12));
            g.fill(new Ellipse2D.Double(cx + 18, cy - 16, 22, 12));

            g.setColor(new Color(65, 35, 50, 210));
            g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc(cx - 34, cy - 30, 20, 10, 15, 150);
            g.drawArc(cx + 14, cy - 30, 20, 10, 15, 150);

            g.setColor(new Color(215, 78, 108, 220));
            g.drawArc(cx - 12, cy + 2, 24, 12, 195, 150);

            // Floating sparkle accents
            g.setColor(new Color(255, 255, 255, 210));
            g.fill(new Ellipse2D.Double(cx + 135, cy - 95, 14, 14));
            g.fill(new Ellipse2D.Double(cx - 148, cy - 45, 10, 10));
            g.fill(new Ellipse2D.Double(cx + 115, cy + 40, 8, 8));
        } else {
            // Clean modern product / lifestyle showcase card
            g.setColor(new Color(255, 255, 255, 225));
            g.fill(new RoundRectangle2D.Double(cx - 125, cy - 110, 250, 220, 36, 36));
            g.setColor(palette[1]);
            g.fill(new RoundRectangle2D.Double(cx - 95, cy - 80, 190, 115, 22, 22));
            g.setColor(palette[2]);
            g.fill(new Ellipse2D.Double(cx - 32, cy - 55, 64, 64));
            g.setColor(new Color(40, 50, 65, 180));
            g.fill(new RoundRectangle2D.Double(cx - 85, cy + 55, 170, 16, 8, 8));
            g.fill(new RoundRectangle2D.Double(cx - 60, cy + 80, 120, 12, 6, 6));
        }
    }

    private static Font pickFont(int style, int size) {
        for (String family : List.of("Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", "SansSerif")) {
            Font f = new Font(family, style, size);
            if (f.canDisplay('美') && f.canDisplay('A')) return f;
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    private record PosterSpec(String headline, String subtitle, String tag, String cta, String theme, String subject) {}
}
