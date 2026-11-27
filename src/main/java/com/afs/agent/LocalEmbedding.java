package com.afs.agent;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Locale;

/**
 * Dependency-free embedding used by the retrieval layer.
 *
 * <p>Production deployments swap this for a hosted or self-hosted embedding model; locally we
 * hash word unigrams plus character trigrams into a fixed 384-dimension space and L2-normalise.
 * That keeps the RAG path runnable with zero credentials and zero per-token cost, which is the
 * same trade-off used when an embedding call is too expensive relative to the corpus size.
 */
final class LocalEmbedding {
    static final int DIM = 384;

    private LocalEmbedding() {}

    static float[] embed(String text) {
        float[] vector = new float[DIM];
        if (text == null || text.isBlank()) return vector;
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        for (String token : normalized.split("\\s+")) {
            if (token.isEmpty()) continue;
            add(vector, token, 1.0f);
            for (int i = 0; i + 3 <= token.length(); i++) {
                add(vector, "#" + token.substring(i, i + 3), 0.35f);
            }
        }
        double norm = 0;
        for (float value : vector) norm += value * value;
        if (norm > 0) {
            float inverse = (float) (1.0 / Math.sqrt(norm));
            for (int i = 0; i < DIM; i++) vector[i] *= inverse;
        }
        return vector;
    }

    private static void add(float[] vector, String token, float weight) {
        int hash = token.hashCode();
        int index = Math.floorMod(hash, DIM);
        boolean negative = ((hash >>> 16) & 1) == 1;
        vector[index] += negative ? -weight : weight;
    }

    static double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length != right.length) return 0;
        double dot = 0;
        for (int i = 0; i < left.length; i++) dot += left[i] * right[i];
        return dot;
    }

    /** Half-precision storage keeps a 384-dim vector around 1 KB of text. */
    static String encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 2);
        for (float value : vector) buffer.putShort((short) Math.round(value * 32_767f));
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    static float[] decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return new float[DIM];
        byte[] bytes = Base64.getDecoder().decode(encoded);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] vector = new float[bytes.length / 2];
        for (int i = 0; i < vector.length; i++) vector[i] = buffer.getShort() / 32_767f;
        return vector;
    }

    /** Rough token estimate for cost accounting when the provider does not report usage. */
    static int tokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, Math.round(text.length() / 4.0f));
    }
}
