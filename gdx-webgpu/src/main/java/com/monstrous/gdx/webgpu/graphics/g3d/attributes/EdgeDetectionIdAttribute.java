package com.monstrous.gdx.webgpu.graphics.g3d.attributes;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Attribute;

/**
 * Attribute for encoding edge detection object IDs as a color value in the material.
 * The object ID is encoded in the RGB channels as a 24-bit integer, similar to PickingIdAttribute.
 * This allows multiple objects in a scene to be uniquely identified when using MRT edge detection.
 */
public class EdgeDetectionIdAttribute extends Attribute {
    public static final String Alias = "edgeDetectionId";
    public static final long Type = register(Alias);

    public Color color = new Color(0, 0, 0, 1);

    public EdgeDetectionIdAttribute() {
        super(Type);
    }

    public EdgeDetectionIdAttribute(int objectId) {
        super(Type);
        setObjectId(objectId);
    }

    public EdgeDetectionIdAttribute(Color color) {
        super(Type);
        this.color.set(color);
    }

    public void setObjectId(int objectId) {
        // Use static method to encode ID
        this.color.set(encodeObjectId(objectId));
    }

    public int getObjectId() {
        // Use static method to decode ID from color
        return decodeObjectId(this.color);
    }

    /**
     * Encode an object ID into a Color.
     * Encodes the ID into R, G, and B channels:
     * - R channel: Low byte (0-255)
     * - G channel: Middle byte (0-255)
     * - B channel: High byte (0-255)
     * - A channel: 1 (full alpha)
     *
     * This supports object IDs up to 16,777,215 (2^24 - 1)
     *
     * @param id The object ID to encode (0-16777215)
     * @return A Color with the encoded ID
     */
    public static Color encodeObjectId(int id) {
        id = Math.min(16777215, Math.max(0, id)); // Clamp to 24-bit range

        int r = id & 0xFF;              // Low byte (bits 0-7)
        int g = (id >> 8) & 0xFF;       // Middle byte (bits 8-15)
        int b = (id >> 16) & 0xFF;      // High byte (bits 16-23)

        return new Color(r / 255.0f, g / 255.0f, b / 255.0f, 1.0f);
    }

    /**
     * Decode an object ID from a Color.
     * Decodes the ID from R, G, and B channels.
     *
     * @param color The Color containing the encoded ID
     * @return The decoded object ID (0-16777215)
     */
    public static int decodeObjectId(Color color) {
        int r = Math.round(color.r * 255.0f);
        int g = Math.round(color.g * 255.0f);
        int b = Math.round(color.b * 255.0f);
        return r | (g << 8) | (b << 16);
    }

    /**
     * Decode an object ID from raw RGB byte values.
     * This is used when reading pixels from the GPU.
     *
     * Supports IDs up to 16,777,215 (2^24 - 1)
     *
     * @param r Red channel (0-255) - Low byte
     * @param g Green channel (0-255) - Middle byte
     * @param b Blue channel (0-255) - High byte
     * @return The decoded object ID (0-16777215)
     */
    public static int decodeObjectIdFromBytes(int r, int g, int b) {
        return r | (g << 8) | (b << 16);
    }

    public Color getColor() {
        return this.color;
    }

    @Override
    public Attribute copy() {
        return new EdgeDetectionIdAttribute(this.color);
    }

    @Override
    public int hashCode() {
        return (int) (31 * type + color.toIntBits());
    }

    @Override
    public int compareTo(Attribute o) {
        if (type != o.type)
            return (int) (type - o.type);
        return ((EdgeDetectionIdAttribute) o).color.toIntBits() - color.toIntBits();
    }
}
