# Proposal

Ships as: `feat(velocity): add link commands to the proxy plugin`

## Why

Account links (`add-account-links`) start in the game: the player, authenticated by Mojang on the proxy, requests a one-time code and redeems it in Discord or another service. Players therefore need in-game commands to get a code, to see and remove their links, and to set public profile links. The Velocity proxy plugin already talks to Otis on every join, so the commands belong there and work the same on every server behind the proxy.

## What Changes

- New proxy commands for players:
  - `/link <discord|twitch|youtube>` requests a link code. It shows the code as a clickable chat component that copies it to the clipboard, with the expiry time and a short hint where to redeem it.
  - `/unlink <provider>` removes a link.
  - `/links` lists the player's links and marks each as verified or unverified.
  - `/social <provider> <handle|url>` sets a public, unverified profile link for any supported provider.
- Each command has its own permission node: `otis.command.link`, `otis.command.unlink`, `otis.command.links` and `otis.command.social`.
- Calls to Otis run asynchronously and never block the proxy. Problem responses map to messages for the player: rate limited, invalid value, provider already linked, player unknown, Otis unreachable.
- All player-facing text is translated. English is the fallback and German is shipped too, rendered per player locale through Adventure's global translator.
- The plugin's existing join/quit sync is unchanged. Older plugin versions keep working against Otis. The new commands are available only after the proxies are updated, which requires a proxy restart, independently of the backend rollout.

## Capabilities

### New Capabilities
- `link-commands`: the in-game commands for linking, listing, unlinking and setting profile links, their permissions, asynchronous behavior, error messages and translations.

### Modified Capabilities

## Impact

- **velocity-plugin:**
  - new command classes, a translation registry and resource bundles `otis_en.properties` and `otis_de.properties`
  - uses `OtisClients.newApiClient()` for the new calls
  - the shadow JAR still contains no Adventure classes; Velocity provides Adventure
- **Dependencies:** none new. It uses Velocity's API (Brigadier commands, Adventure, MiniMessage, `TranslationStore`), which `velocity-api` provides at runtime.
- **User-facing text:** new translation keys under `otis.link.*`, in `en` (fallback) and `de`.
- **Backend / client:** unchanged, apart from consuming the links API from `add-account-links`.
- **Deployment:** proxies must be restarted to load the new plugin. The backend does not depend on it.
- **Depends on:** `add-account-links`.
