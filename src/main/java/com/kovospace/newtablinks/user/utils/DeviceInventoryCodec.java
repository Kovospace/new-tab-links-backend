package com.kovospace.newtablinks.user.utils;

import com.kovospace.newtablinks.user.dtos.DeviceInventoryProfileDto;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns an installation's inventory report into the JSON stored on its device row, and back.
 *
 * <p>The stored shape is the reported list of profiles exactly, so what the devices page reads is
 * what the installation sent. Named fields through the application's own mapper, never Java
 * serialization.</p>
 *
 * @since 0.0.18
 */
@Component
public class DeviceInventoryCodec {

    private static final TypeReference<List<DeviceInventoryProfileDto>> PROFILE_LIST_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;

    /**
     * Creates the codec.
     *
     * @param objectMapper the application's JSON mapper
     */
    public DeviceInventoryCodec(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serialises a validated report for storage.
     *
     * @param profiles the reported profiles, with their workspaces
     * @return the JSON to store
     */
    public String encode(final List<DeviceInventoryProfileDto> profiles) {
        return objectMapper.writeValueAsString(profiles);
    }

    /**
     * Reads a stored report back.
     *
     * @param inventoryJson the stored JSON, never {@code null}
     * @return the reported profiles, with their workspaces, in the reported order
     */
    public List<DeviceInventoryProfileDto> decode(final String inventoryJson) {
        return objectMapper.readValue(inventoryJson, PROFILE_LIST_TYPE);
    }
}
