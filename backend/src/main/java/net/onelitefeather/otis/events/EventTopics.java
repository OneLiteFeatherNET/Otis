package net.onelitefeather.otis.events;

/** Names and sizing of the Kafka topics Otis publishes to. */
public final class EventTopics {

    /** Account link events; the message key is the player's Mojang uuid. */
    public static final String ACCOUNT_LINKS = "otis.account-links";
    /** Partitions of {@link #ACCOUNT_LINKS} when Otis creates it. */
    public static final int PARTITIONS = 3;
    /** Replication factor of {@link #ACCOUNT_LINKS} when Otis creates it. */
    public static final short REPLICATION_FACTOR = 3;

    private EventTopics() {
    }
}
