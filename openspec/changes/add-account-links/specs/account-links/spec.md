# Spec Delta

## Purpose

Lets a player connect their Minecraft identity to external accounts (Discord, Twitch, YouTube and others), verified through one-time link codes or as unverified public profile links, so that every OneLiteFeather service reads the same link registry from Otis.

## ADDED Requirements

### Requirement: Supported providers
The system SHALL support the providers `discord`, `twitch`, `youtube`, `x`, `tiktok` and `github`, identified by these lowercase names in paths and bodies. A request naming any other provider SHALL be rejected with status 400 and problem type `https://otis.onelitefeather.net/problems/unsupported-provider`.

#### Scenario: Unknown provider rejected
- **WHEN** a client requests a link code for provider `myspace`
- **THEN** the response status is 400 with problem type `https://otis.onelitefeather.net/problems/unsupported-provider`

#### Scenario: Provider names are lowercase
- **WHEN** a client lists the links of a player with a Discord link
- **THEN** the link's provider is returned as `discord`

### Requirement: Link codes are issued for known players
The system SHALL issue a link code via `POST /v1/players/{playerUuid}/link-codes` with body `{"provider": "<provider>"}`. The response SHALL have status 201 and body `{"code": "XXXX-XXXX", "provider": "<provider>", "expiresAt": "<timestamp>"}`. A code SHALL consist of 8 characters from the Crockford Base32 alphabet (digits and uppercase letters without I, L, O, U), formatted as two groups of four separated by `-`, and SHALL expire 10 minutes after issue. Codes SHALL be generated with a cryptographically secure random source and SHALL be stored only as a hash, never in plain text. Issuing a code for a player unknown to Otis SHALL return 404 with problem type `https://otis.onelitefeather.net/problems/player-not-found`.

#### Scenario: Code issued
- **WHEN** the proxy requests a `discord` link code for a stored player at time T
- **THEN** the response status is 201, the code matches `^[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$` and `expiresAt` equals T plus 10 minutes

#### Scenario: Unknown player
- **WHEN** a link code is requested for a player uuid that is not stored
- **THEN** the response status is 404 with problem type `https://otis.onelitefeather.net/problems/player-not-found`

#### Scenario: Code not stored in plain text
- **WHEN** a code has been issued
- **THEN** no stored column contains the code characters in plain text

### Requirement: One open code per player and provider
The system SHALL keep at most one open (unexpired, unused) code per player and provider; issuing a new code SHALL invalidate the previous open code for the same player and provider. Codes for different providers SHALL be independent.

#### Scenario: New code replaces old code
- **WHEN** a player is issued code A for `discord` and then code B for `discord`
- **THEN** redeeming code A fails with status 410 and redeeming code B succeeds

#### Scenario: Codes for different providers coexist
- **WHEN** a player is issued a `discord` code and then a `twitch` code
- **THEN** both codes can be redeemed for their own provider

### Requirement: Link code issuance is rate limited
The system SHALL issue at most 5 link codes per player within any 60-minute window; a further request SHALL be rejected with status 429 and problem type `https://otis.onelitefeather.net/problems/link-code-rate-limited`.

#### Scenario: Sixth code within an hour
- **WHEN** a player has been issued 5 codes within the last 60 minutes and requests another
- **THEN** the response status is 429 with problem type `https://otis.onelitefeather.net/problems/link-code-rate-limited`

#### Scenario: Window passes
- **WHEN** 60 minutes have passed since the oldest of 5 issued codes
- **THEN** a new code is issued with status 201

### Requirement: Redeeming a code creates a verified link
The system SHALL redeem a code via `POST /v1/link-codes/redeem` with body `{"code", "provider", "externalId", "displayName"?}`. The code SHALL be accepted case-insensitively and with or without the `-` separator. A valid code SHALL be consumed (single use) and SHALL create a verified link between the code's player and the given external account, answering 201 with the link and the player uuid. A code that is unknown, expired, already used, or issued for a different provider SHALL be rejected with the same status 410 and problem type `https://otis.onelitefeather.net/problems/link-code-invalid`, without revealing which case applies.

#### Scenario: Successful redeem
- **WHEN** the Discord bot redeems a valid `discord` code with externalId `123456789012345678` and displayName `meinerlp`
- **THEN** the response status is 201 and the player's links contain a verified `discord` link with that externalId and displayName

#### Scenario: Lenient code input
- **WHEN** a valid code `K7Q4-MZ2A` is redeemed as `k7q4mz2a`
- **THEN** the redeem succeeds

#### Scenario: Code used twice
- **WHEN** a code that was already redeemed is redeemed again
- **THEN** the response status is 410 with problem type `https://otis.onelitefeather.net/problems/link-code-invalid`

#### Scenario: Expired code
- **WHEN** a code is redeemed 10 minutes and 1 second after it was issued
- **THEN** the response status is 410 with problem type `https://otis.onelitefeather.net/problems/link-code-invalid`

#### Scenario: Provider mismatch
- **WHEN** a code issued for `twitch` is redeemed with provider `discord`
- **THEN** the response status is 410 with problem type `https://otis.onelitefeather.net/problems/link-code-invalid` and the code stays redeemable for `twitch`

### Requirement: External accounts and providers are linked at most once
An external account (provider plus externalId) SHALL be linked to at most one player, and a player SHALL have at most one link per provider. Redeeming a code for an external account that is verified-linked to another player SHALL be rejected with status 409 and problem type `https://otis.onelitefeather.net/problems/external-account-already-linked`. Redeeming a code when the player already has a verified link for that provider SHALL be rejected with status 409 and problem type `https://otis.onelitefeather.net/problems/provider-already-linked`; the player has to unlink first. In both cases the code SHALL stay unconsumed.

#### Scenario: External account taken by another player
- **WHEN** Discord account 42 is verified-linked to player A and player B redeems a code with externalId 42
- **THEN** the response status is 409 with problem type `https://otis.onelitefeather.net/problems/external-account-already-linked` and player A keeps the link

#### Scenario: Player already verified-linked
- **WHEN** player A has a verified `discord` link and redeems a new `discord` code for another Discord account
- **THEN** the response status is 409 with problem type `https://otis.onelitefeather.net/problems/provider-already-linked`

#### Scenario: Re-linking after unlink
- **WHEN** player A unlinks `discord` and then redeems a new `discord` code
- **THEN** the redeem succeeds with status 201

### Requirement: Unverified profile links
The system SHALL let a player set a public, unverified link via `PUT /v1/players/{playerUuid}/links/{provider}` with body `{"value": "<handle or https URL>"}`. The value SHALL be at most 200 characters and SHALL be either a handle matching the provider's handle format or an `https` URL on the provider's own domain; otherwise the request SHALL be rejected with status 400 and problem type `https://otis.onelitefeather.net/problems/invalid-link-value`. Setting an unverified link SHALL answer 200 and replace a previous unverified link for that provider. It SHALL be rejected with status 409 and problem type `https://otis.onelitefeather.net/problems/provider-already-linked` if the player has a verified link for that provider. Redeeming a code for a provider with an unverified link SHALL replace it with the verified link.

#### Scenario: Unverified link set
- **WHEN** a player sets `youtube` to `https://www.youtube.com/@onelitefeather`
- **THEN** the response status is 200 and the player's links contain an unverified `youtube` link with that value

#### Scenario: Foreign domain rejected
- **WHEN** a player sets `twitch` to `https://evil.example/twitch`
- **THEN** the response status is 400 with problem type `https://otis.onelitefeather.net/problems/invalid-link-value`

#### Scenario: Verified link not overwritten
- **WHEN** a player with a verified `discord` link sets an unverified `discord` value
- **THEN** the response status is 409 and the verified link is unchanged

#### Scenario: Redeem upgrades unverified link
- **WHEN** a player with an unverified `twitch` link redeems a `twitch` code
- **THEN** the player has exactly one `twitch` link and it is verified

### Requirement: Listing, unlinking and lookup
The system SHALL list a player's links via `GET /v1/players/{playerUuid}/links` as a JSON array of `{provider, externalId, value, displayName, verified, linkedAt}` (status 200, empty array when none). The system SHALL delete a link via `DELETE /v1/players/{playerUuid}/links/{provider}` with status 204, also when no link exists. The system SHALL answer `GET /v1/links/{provider}/{externalId}` with status 200 and the player uuid plus the link for a verified link, or status 404 with problem type `https://otis.onelitefeather.net/problems/link-not-found` otherwise. Unverified links SHALL NOT be found by lookup. Requests for unknown players SHALL return 404 `player-not-found`.

#### Scenario: Empty list
- **WHEN** a stored player without links is listed
- **THEN** the response status is 200 with an empty array

#### Scenario: Unlink is idempotent
- **WHEN** a client deletes the `discord` link of a player twice
- **THEN** both responses have status 204 and the player has no `discord` link

#### Scenario: Lookup by Discord account
- **WHEN** the Discord bot looks up `discord` account 123456789012345678 that is verified-linked to player P
- **THEN** the response status is 200 and contains player uuid P

#### Scenario: Lookup ignores unverified links
- **WHEN** a player has an unverified `x` link with value `onelitefeather` and a client looks up `x`/`onelitefeather`
- **THEN** the response status is 404 with problem type `https://otis.onelitefeather.net/problems/link-not-found`

### Requirement: Links are removed with their player
Deleting a player through the existing player delete endpoint SHALL delete all of that player's links and open codes; the endpoint's response SHALL be unchanged.

#### Scenario: Player deletion cascades
- **WHEN** a player with a verified `discord` link is deleted via `POST /otis/delete/{owner}`
- **THEN** the response is the same as before this change and a lookup of that Discord account returns 404

### Requirement: Link data stays out of telemetry
The system SHALL create one INTERNAL span per link use case named `links.code.create`, `links.redeem`, `links.list`, `links.put`, `links.delete` or `links.lookup`, carrying the provider and an outcome attribute, and the player uuid where the player is known. Link codes, external ids, display names and link values SHALL NOT appear in span attributes or log messages. Expected outcomes (invalid code, conflict, not found, rate limited) SHALL NOT mark the span as error.

#### Scenario: Redeem span without secrets
- **WHEN** a code is redeemed successfully
- **THEN** a span `links.redeem` exists with the provider, the player uuid and outcome `linked`, and no attribute contains the code, the externalId or the displayName

#### Scenario: Invalid code is not an error
- **WHEN** an expired code is redeemed
- **THEN** the `links.redeem` span has outcome `invalid` and its status is not ERROR

### Requirement: Existing API and storage stay compatible
The change SHALL only add tables and endpoints: existing tables SHALL NOT be altered, existing endpoints SHALL keep their behavior, and the generated client's existing public API SHALL stay unchanged.

#### Scenario: Upgrade keeps player data
- **WHEN** the new version starts against a database with players and settings
- **THEN** all existing rows are unchanged and the tables `account_link` and `link_code` exist

#### Scenario: Old plugin keeps working
- **WHEN** a velocity-plugin built before this change syncs a player join
- **THEN** it receives the same status codes as before this change
