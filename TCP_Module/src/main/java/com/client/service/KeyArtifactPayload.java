package com.client.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

final class KeyArtifactPayload
{
    static final String ARTIFACT_TYPE = "FST-KEY1";
    static final String PUBLIC_KEY_TYPE = "public";
    static final String PRIVATE_KEY_TYPE = "private";
    static final String LEGACY_PUBLIC_PREFIX = "FST-PUB1:";
    static final String LEGACY_PRIVATE_PREFIX = "FST-PRIV1:";

    private static final Gson GSON = new Gson();

    private KeyArtifactPayload() {}

    static String publicArtifact(String publicKey)
    {
        JsonObject object = baseArtifact(PUBLIC_KEY_TYPE);
        object.addProperty("publicKey", publicKey);
        return GSON.toJson(object);
    }

    static String privateArtifact(String privateKey)
    {
        JsonObject object = baseArtifact(PRIVATE_KEY_TYPE);
        object.addProperty("privateKey", privateKey);
        return GSON.toJson(object);
    }

    static String extractPublicKey(String text)
    {
        String normalized = normalizeText(text);
        String jsonValue = extractFromJson(normalized, PUBLIC_KEY_TYPE, "publicKey");
        if(jsonValue != null)
        {
            return extractPublicKey(jsonValue);
        }
        String labeledValue = extractFromLabeledText(normalized, "publicKey");
        if(labeledValue != null)
        {
            return extractPublicKey(labeledValue);
        }
        if(normalized.startsWith(LEGACY_PUBLIC_PREFIX))
        {
            return normalized.substring(LEGACY_PUBLIC_PREFIX.length());
        }
        if(normalized.startsWith("FST1:") || normalized.startsWith("FST-TEXT1:") || normalized.startsWith(LEGACY_PRIVATE_PREFIX))
        {
            throw new IllegalArgumentException("QR/text payload is not a public key artifact");
        }
        return normalized;
    }

    static String extractPrivateKey(String text)
    {
        String normalized = normalizeText(text);
        String jsonValue = extractFromJson(normalized, PRIVATE_KEY_TYPE, "privateKey");
        if(jsonValue != null)
        {
            return extractPrivateKey(jsonValue);
        }
        String labeledValue = extractFromLabeledText(normalized, "privateKey");
        if(labeledValue != null)
        {
            return extractPrivateKey(labeledValue);
        }
        if(normalized.startsWith(LEGACY_PRIVATE_PREFIX))
        {
            return normalized.substring(LEGACY_PRIVATE_PREFIX.length());
        }
        if(normalized.startsWith(LEGACY_PUBLIC_PREFIX) || normalized.startsWith("FST1:") || normalized.startsWith("FST-TEXT1:"))
        {
            throw new IllegalArgumentException("QR/text payload is not a private key artifact");
        }
        return normalized;
    }

    static String stripPemEnvelope(String text, String pemType)
    {
        String begin = "-----BEGIN " + pemType + "-----";
        if(text == null || !text.contains(begin))
        {
            return text == null ? "" : text.trim();
        }
        StringBuilder builder = new StringBuilder(text.length());
        for(String line : text.split("\\R"))
        {
            String trimmed = line.trim();
            if(trimmed.startsWith("-----BEGIN ") || trimmed.startsWith("-----END "))
            {
                continue;
            }
            builder.append(trimmed);
        }
        return builder.toString();
    }

    static boolean containsPemEnvelope(String text, String pemType)
    {
        return text != null && text.contains("-----BEGIN " + pemType + "-----");
    }

    static String normalizePemEnvelope(String text, String pemType)
    {
        String begin = "-----BEGIN " + pemType + "-----";
        String end = "-----END " + pemType + "-----";
        if(text == null || !text.contains(begin))
        {
            return text == null ? "" : text.trim();
        }
        StringBuilder body = new StringBuilder(text.length());
        for(String line : text.split("\\R"))
        {
            String trimmed = line.trim();
            if(trimmed.isEmpty() || trimmed.startsWith("-----BEGIN ") || trimmed.startsWith("-----END "))
            {
                continue;
            }
            body.append(trimmed);
        }
        return pemFromBase64(body.toString(), pemType);
    }

    static String pemFromBase64(String base64, String pemType)
    {
        String compact = removeWhitespace(base64 == null ? "" : base64);
        StringBuilder builder = new StringBuilder(compact.length() + 80);
        builder.append("-----BEGIN ").append(pemType).append("-----\n");
        for(int i = 0; i < compact.length(); i += 64)
        {
            builder.append(compact, i, Math.min(i + 64, compact.length())).append('\n');
        }
        builder.append("-----END ").append(pemType).append("-----\n");
        return builder.toString();
    }

    static String removeWhitespace(String text)
    {
        StringBuilder builder = new StringBuilder(text.length());
        for(int i = 0; i < text.length(); i++)
        {
            if(!Character.isWhitespace(text.charAt(i)))
            {
                builder.append(text.charAt(i));
            }
        }
        return builder.toString();
    }

    private static JsonObject baseArtifact(String keyType)
    {
        JsonObject object = new JsonObject();
        object.addProperty("artifactType", ARTIFACT_TYPE);
        object.addProperty("keyType", keyType);
        return object;
    }

    private static String normalizeText(String text)
    {
        String normalized = text == null ? "" : text.trim();
        if(normalized.startsWith("\uFEFF"))
        {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    private static String extractFromJson(String text, String expectedKeyType, String keyField)
    {
        if(!text.startsWith("{"))
        {
            return null;
        }
        try
        {
            JsonObject object = JsonParser.parseString(text).getAsJsonObject();
            if(isKeyArtifact(object))
            {
                String keyType = stringField(object, "keyType");
                if(!expectedKeyType.equals(keyType))
                {
                    throw new IllegalArgumentException("QR/text payload is not a " + expectedKeyType + " key artifact");
                }
                return stringField(object, keyField);
            }
            if(object.has("qrText") && !object.get("qrText").isJsonNull())
            {
                return object.get("qrText").getAsString();
            }
            if(object.has(keyField) && !object.get(keyField).isJsonNull())
            {
                return object.get(keyField).getAsString();
            }
        }
        catch(JsonSyntaxException | IllegalStateException | ClassCastException ex)
        {
            if(ex instanceof IllegalArgumentException)
            {
                throw ex;
            }
        }
        return null;
    }

    private static boolean isKeyArtifact(JsonObject object)
    {
        return object.has("artifactType")
                && !object.get("artifactType").isJsonNull()
                && ARTIFACT_TYPE.equals(object.get("artifactType").getAsString());
    }

    private static String stringField(JsonObject object, String fieldName)
    {
        if(!object.has(fieldName) || object.get(fieldName).isJsonNull())
        {
            throw new IllegalArgumentException("Key artifact field is required: " + fieldName);
        }
        return object.get(fieldName).getAsString();
    }

    private static String extractFromLabeledText(String text, String keyField)
    {
        for(String line : text.split("\\R"))
        {
            String trimmed = line.trim();
            int separator = trimmed.indexOf(':');
            if(separator < 0)
            {
                separator = trimmed.indexOf('=');
            }
            if(separator <= 0)
            {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            if(keyField.equalsIgnoreCase(key) || "qrText".equalsIgnoreCase(key))
            {
                return trimmed.substring(separator + 1).trim();
            }
        }
        return null;
    }
}
