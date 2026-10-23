# Spec Delta

## Purpose

Gives players in-game commands on the proxy to obtain link codes, list and remove their account links and set public profile links, with translated feedback and without ever blocking the proxy.

## ADDED Requirements

### Requirement: Request a link code in game
The proxy plugin SHALL provide `/link <provider>` for players with permission `otis.command.link`, where provider is one of `discord`, `twitch` or `youtube`. On success it SHALL show the code returned by Otis as a clickable component that copies the code to the clipboard, together with the expiry time and where to redeem it. The command SHALL only be available to players, not to the console.

#### Scenario: Code shown and copyable
- **WHEN** a player with permission runs `/link discord` and Otis returns code `K7Q4-MZ2A`
- **THEN** the player sees a message containing `K7Q4-MZ2A` whose click action copies `K7Q4-MZ2A` to the clipboard, plus the expiry time

#### Scenario: Provider without code linking
- **WHEN** a player runs `/link github`
- **THEN** the player sees a message that `github` can only be set with `/social` and no request is sent to Otis

#### Scenario: Missing permission
- **WHEN** a player without `otis.command.link` runs `/link discord`
- **THEN** the command is not executed and no request is sent to Otis

#### Scenario: Console rejected
- **WHEN** the console runs `/link discord`
- **THEN** a message states that only players can use the command

### Requirement: List and remove links in game
The plugin SHALL provide `/links` (permission `otis.command.links`) listing the player's links with provider, displayed value or display name, and a verified/unverified marker, and `/unlink <provider>` (permission `otis.command.unlink`) removing the link for that provider with a confirmation message, also when no link existed.

#### Scenario: Links listed
- **WHEN** a player with a verified `discord` link and an unverified `youtube` link runs `/links`
- **THEN** both links are shown, `discord` marked verified and `youtube` marked unverified

#### Scenario: No links
- **WHEN** a player without links runs `/links`
- **THEN** a message states that no accounts are linked and how to use `/link`

#### Scenario: Unlink confirmed
- **WHEN** a player runs `/unlink discord`
- **THEN** Otis receives `DELETE /v1/players/{uuid}/links/discord` and the player sees a confirmation

### Requirement: Set public profile links in game
The plugin SHALL provide `/social <provider> <handle|url>` (permission `otis.command.social`) for every supported provider, setting an unverified public link in Otis and confirming it. Invalid values and attempts to overwrite a verified link SHALL be answered with a specific message.

#### Scenario: Profile link set
- **WHEN** a player runs `/social youtube https://www.youtube.com/@onelitefeather`
- **THEN** Otis receives the unverified link and the player sees a confirmation

#### Scenario: Invalid value
- **WHEN** Otis answers with problem `invalid-link-value`
- **THEN** the player sees a message explaining the accepted handle or URL format

#### Scenario: Verified link protected
- **WHEN** Otis answers with problem `provider-already-linked`
- **THEN** the player sees a message that the provider is already verified and must be unlinked first

### Requirement: Commands never block the proxy
Every call to Otis made by these commands SHALL run asynchronously off the command and event threads, and its result SHALL be reported to the player when it arrives. If Otis is unreachable or answers with a server error, the player SHALL see a generic "try again later" message, and the failure SHALL be logged once at WARN without personal data.

#### Scenario: Otis down
- **WHEN** a player runs `/link discord` while Otis is unreachable
- **THEN** the command returns immediately, the player later sees the "try again later" message and one WARN line is logged

#### Scenario: Rate limited
- **WHEN** Otis answers with problem `link-code-rate-limited`
- **THEN** the player sees a message to wait before requesting another code

### Requirement: Player-facing text is translated
All messages of these commands SHALL be translatable components with keys under `otis.link.`, rendered in the player's locale, with English as fallback for every other locale. The plugin SHALL ship English and German translations, and every key used SHALL exist in both.

#### Scenario: German client
- **WHEN** a player with locale `de_DE` runs `/links` without links
- **THEN** the message is shown in German

#### Scenario: Fallback locale
- **WHEN** a player with locale `fr_FR` runs `/links` without links
- **THEN** the message is shown in English

### Requirement: Existing plugin behavior stays unchanged
Adding the commands SHALL NOT change the plugin's join and disconnect synchronization, and the plugin's shadow JAR SHALL NOT contain Adventure classes.

#### Scenario: Join sync unchanged
- **WHEN** a player joins the proxy with the new plugin
- **THEN** the same requests are sent to Otis as by the previous plugin version
