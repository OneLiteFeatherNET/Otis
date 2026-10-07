package net.onelitefeather.otis.service;

import net.onelitefeather.otis.dto.PlayerSettingDTO;

/**
 * Outcome of storing a setting. Sealed, so callers handle every case in an exhaustive {@code switch}.
 */
public sealed interface PutResult {

    /** @return the setting as it is stored after the put */
    PlayerSettingDTO setting();

    /** The setting did not exist and was created with version 1. */
    record Created(PlayerSettingDTO setting) implements PutResult {
    }

    /** The value changed; version and update time moved on. */
    record Updated(PlayerSettingDTO setting) implements PutResult {
    }

    /** The value was semantically equal to the stored one; nothing changed. */
    record Unchanged(PlayerSettingDTO setting) implements PutResult {
    }
}
