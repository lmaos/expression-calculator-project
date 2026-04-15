package com.clmcat.commons.calculator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.TimeZone;

/**
 * 默认输出格式集合。
 */
final class DefaultOutputFormats {

    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    private static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private DefaultOutputFormats() {
    }

    static void registerDefaults(OutputFormatRegistry registry) {
        registry.register(byte[].class, DefaultOutputFormats::formatByteArray);
        registry.setOption(byte[].class, "mode", "text");
        registry.setOption(byte[].class, "charset", DEFAULT_CHARSET);

        registry.register(File.class, DefaultOutputFormats::formatFile);
        registry.setOption(File.class, "mode", "path");
        registry.setOption(File.class, "charset", DEFAULT_CHARSET);

        registry.register(Date.class, DefaultOutputFormats::formatDate);
        registry.setOption(Date.class, "pattern", DEFAULT_DATE_PATTERN);
    }

    private static String formatByteArray(Object value, OutputFormatContext context) {
        byte[] bytes = (byte[]) value;
        String mode = normalizeMode(context.stringOption("mode", "text"));
        if ("text".equals(mode)) {
            return new String(bytes, context.charsetOption("charset", DEFAULT_CHARSET));
        }
        if ("base64".equals(mode)) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if ("hex".equals(mode)) {
            return toHex(bytes);
        }
        throw new IllegalArgumentException("不支持的 byte[] 输出模式: " + mode);
    }

    private static String formatFile(Object value, OutputFormatContext context) {
        File file = (File) value;
        String mode = normalizeMode(context.stringOption("mode", "path"));
        if ("path".equals(mode)) {
            return file.getPath();
        }
        if ("name".equals(mode)) {
            return file.getName();
        }
        if ("content".equals(mode)) {
            try {
                return new String(Files.readAllBytes(file.toPath()), context.charsetOption("charset", DEFAULT_CHARSET));
            } catch (IOException exception) {
                throw new IllegalArgumentException("读取文件内容失败: " + file, exception);
            }
        }
        throw new IllegalArgumentException("不支持的 File 输出模式: " + mode);
    }

    private static String formatDate(Object value, OutputFormatContext context) {
        String pattern = context.stringOption("pattern", DEFAULT_DATE_PATTERN);
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Date 输出 pattern 不能为空");
        }
        TimeZone timeZone = context.timeZoneOption("timeZone", null);
        try {
            SimpleDateFormat format = new SimpleDateFormat(pattern);
            if (timeZone != null) {
                format.setTimeZone(timeZone);
            }
            return format.format((Date) value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Date 格式化失败: " + pattern, exception);
        }
    }

    private static String normalizeMode(String mode) {
        return mode == null ? "" : mode.trim().toLowerCase();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            int normalized = value & 0xFF;
            if (normalized < 16) {
                builder.append('0');
            }
            builder.append(Integer.toHexString(normalized).toUpperCase());
        }
        return builder.toString();
    }
}
