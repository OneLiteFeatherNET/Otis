package net.onelitefeather.otis.events;

import io.micronaut.serde.annotation.Serdeable;

/**
 * A CloudEvents 1.0 envelope in structured JSON mode, restricted to the attributes Otis sets. A plain record
 * serialized by Micronaut Serde replaces the CloudEvents SDK for these eight fields.
 *
 * @param specversion     always {@code 1.0}
 * @param id              unique per event; consumers deduplicate by it
 * @param source          always {@code /otis}
 * @param type            e.g. {@code net.onelitefeather.otis.account.linked}
 * @param subject         the Mojang uuid of the player
 * @param time            when the change happened, ISO-8601 in UTC
 * @param datacontenttype always {@code application/json}
 * @param data            the facts of the change
 */
@Serdeable
public record CloudEvent(String specversion, String id, String source, String type, String subject, String time,
                         String datacontenttype, LinkEventData data) {

    public static final String SPEC_VERSION = "1.0";
    public static final String SOURCE = "/otis";
    public static final String CONTENT_TYPE = "application/json";
}
