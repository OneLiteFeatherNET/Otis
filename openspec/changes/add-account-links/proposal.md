# Proposal

Ships as: `feat(links): link external accounts to players with one-time codes`

## Why

Players want to connect their Minecraft identity to Discord, Twitch, YouTube and other platforms. Services need that connection for Discord roles, link rewards and public profile links. Today every such feature would have to keep its own mapping, as plugin-based solutions like DiscordSRV do. Otis already is the central player data store, so it becomes the single link registry that the proxy, lobby, Discord bot and later a website all read from.

## What Changes

- New account link registry per player. A link connects a player (Mojang `playerUuid`) to one external account per provider. The providers are `discord`, `twitch`, `youtube`, `x`, `tiktok` and `github`.
- **Verified links** are created only by redeeming a one-time link code:
  - The game side, which is trusted because the player is authenticated by Mojang on the proxy, requests a code for a provider: `POST /v1/players/{playerUuid}/link-codes`.
  - The redeeming service, for example a Discord bot that knows the Discord user, redeems it with the external account id: `POST /v1/link-codes/redeem`.
  - Codes have 8 characters (Crockford Base32, shown as `XXXX-XXXX`), are valid for 10 minutes, can be used once and are stored only as a hash. A new code replaces the player's previous open code for the same provider. A player may create at most 5 codes per hour.
- **Unverified profile links:** players can set a public handle or URL for a provider (`PUT /v1/players/{playerUuid}/links/{provider}`), which is shown as unverified. Redeeming a code upgrades it to verified. An unverified write never replaces a verified link.
- Listing (`GET /v1/players/{playerUuid}/links`), idempotent unlinking (`DELETE /v1/players/{playerUuid}/links/{provider}`) and a reverse lookup by external account (`GET /v1/links/{provider}/{externalId}`, so services can ask "which player is this Discord user?").
- An external account belongs to at most one player, and a player has at most one link per provider. Conflicts are reported as Problem Details (`external-account-already-linked`, `provider-already-linked`). Unknown, expired, used or mismatching codes all answer the same `link-code-invalid` (410), so the response reveals nothing about the code.
- Links are deleted with their player. No provider OAuth tokens are stored.
- One INTERNAL span per use case. Codes, external ids and display names never appear in spans or logs.
- The client gains the generated links API. Existing endpoints and old plugins are unaffected.

## Capabilities

### New Capabilities
- `account-links`: one-time link codes, verified and unverified account links per player and provider, listing, unlinking and reverse lookup, with uniqueness, privacy and observability rules.

### Modified Capabilities

## Impact

- **Backend:** new controller, service, DTOs, repositories and entities for links and link codes; new problem types; span helper for link use cases.
- **Database:** Flyway V3, additive only. It creates the tables `account_link` and `link_code` (FKs to `otis_player(uuid)` with `ON DELETE CASCADE`) for PostgreSQL, MariaDB and H2. Existing tables are not altered.
- **API:** new `/v1` endpoints only. Existing `/otis`, `/search` and `/v1/players/{playerUuid}/settings` are unchanged.
- **java-client:** hand-maintained spec `java-client/specs/otis-api-1.1.0.yml` is extended. The public API only gains additions, and `velocity-plugin` compiles unchanged.
- **Security:** Otis has no service authentication. Redeem and lookup trust the calling service's `externalId`, so Otis must stay reachable only from internal trusted services, which it is today: it is exposed only on the internal gateway hostname. Service authentication is a follow-up.
- **Privacy:** external ids and display names are personal data. They are kept to the minimum, never logged, and removed on unlink and on player deletion.
- **New dependencies:** none. JDK `SecureRandom` and `MessageDigest` cover code generation and hashing.
- **User-facing text:** none. Problem texts are English developer texts. The player-facing commands come in `add-link-commands`.
- **Follow-ups:** `add-account-link-events` (Kafka events) and `add-link-commands` (proxy commands) build on this change.
