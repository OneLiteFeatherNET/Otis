# Design

## Context

See proposal.md - Why. The velocity plugin (`velocity-plugin`) currently has:
- `OtisPlugin`, which loads `config.json`, builds an `ApiClient` via `Configuration.getDefaultApiClient()` and registers `PlayerListener` (join/quit sync)
- no commands and no translations

It compiles against `velocity-api` 4.2.0 (`compileOnly`), which provides Adventure 5.x, MiniMessage and Brigadier at runtime. The shadow JAR bundles `otis-client` but no Adventure classes.

The links API, codes and problem types come from `add-account-links`. The generated client is blocking (Java `HttpClient`, `native` library).

## Goals / Non-Goals

**Goals:**
- Four commands, each with its own permission, translated, asynchronous, unit-testable without a running proxy.

**Non-Goals:**
- Redeeming codes in game (that happens in the external service).
- A Discord bot.
- GUIs or dialogs.
- Changes to the join/quit sync.
- Configuration of messages by server operators beyond the shipped bundles.

## Decisions

### D1 - Commands via Velocity's `BrigadierCommand`

**Registration:**
- `LinkCommands` builds `/link`, `/unlink`, `/links` and `/social` with `BrigadierCommand.literalArgumentBuilder`.
- Provider arguments are word arguments with suggestions: `/link` suggests only discord, twitch and youtube; `/social` and `/unlink` suggest all six providers.
- Permissions are checked with `.requires(source -> source.hasPermission("otis.command.<name>"))`.
- Console checks are done in the executor, which answers with a translatable message.
- Commands are registered in `OtisPlugin.onProxyInitialize` through `CommandManager.register(CommandMeta, BrigadierCommand)`.

**Built-in options considered:**
- Velocity `SimpleCommand`: no typed arguments or suggestions.
- A command framework such as cloud: an extra dependency in the shadow JAR for four commands.

**Tests:** unit tests on the built Brigadier tree:
- the parse of `/link discord` reaches the handler with `discord`
- the permission predicate denies a source without the permission

**SOLID:** SRP. The tree only maps input to `LinkCommandHandler` calls.

### D2 - Logic in `LinkCommandHandler`, Otis behind a `LinkGateway` port

**Handler:**
- `LinkCommandHandler` takes an `Audience` (the player), the player uuid and the arguments.
- It calls a `LinkGateway` interface: `requestCode`, `listLinks`, `unlink`, `setProfileLink`. The interface returns a sealed `GatewayResult` (`Success(value)`, `Problem(slug)`, `Unavailable`).
- The handler maps every result to a translatable component.

**Gateway:**
- `OtisLinkGateway` implements the port with the generated `LinksApi` on an `ApiClient` from `OtisClients.newApiClient()`, with base URI from the existing config.
- It converts `ApiException` via `ProblemDetails.from` into `Problem(slug)`. Connection errors and 5xx become `Unavailable`.

**Mapping:** a pattern `switch` over the sealed result and the problem slug. Unknown slugs fall back to the generic error message.

**Tests:** unit tests with a fake gateway and a capturing `Audience` (an Adventure interface, so no proxy is needed). One test per spec scenario asserts the translation key and arguments:
- code component with `ClickEvent.copyToClipboard`
- github rejected without a gateway call
- empty list, verified/unverified markers
- rate limited, invalid value, provider already linked, unavailable

**SOLID:**
- DIP: the handler depends on the port, not on the generated client.
- OCP: a new problem means a new `case`.

### D3 - Asynchronous execution on virtual threads

**How:**
- The plugin owns an `ExecutorService` from `Executors.newVirtualThreadPerTaskExecutor()`, created on proxy initialize and closed on `ProxyShutdownEvent`.
- Commands submit the gateway call and return `Command.SINGLE_SUCCESS` immediately.
- The result is sent to the player's `Audience` from the virtual thread, which is safe because Velocity's `Player.sendMessage` is thread-safe and there is no game state on a proxy.

**Built-in option considered:** Velocity's `Scheduler`, a cached platform thread pool. It also works, but the blocking HTTP calls are a textbook case for virtual threads (OLF rules: virtual threads for blocking I/O).

**Logging:** `Unavailable` results log one WARN per failure with the provider and the exception type. They never log the player name, uuid or code.

**Tests:**
- The handler takes an `Executor`, and tests pass a direct executor, so there are no sleeps.
- A test asserts the WARN once through a captured SLF4J appender (Logback test dependency only).

**SOLID:** SRP. Threading is separate from the mapping logic.

### D4 - Translations: `MiniMessageTranslationStore` + `GlobalTranslator`

**Store:**
- `OtisTranslations` creates a MiniMessage translation store (`MiniMessageTranslationStore.create(Key.key("otis", "messages"))`) and registers the bundles with `registerAll(locale, ResourceBundle, true)`.
- Bundles: `otis_en.properties` (fallback `Locale.ENGLISH`, set via `defaultLocale`) and `otis_de.properties`, UTF-8, at `velocity-plugin/src/main/resources/lang/`.
- The store is added to `GlobalTranslator.translator()` on initialize and removed on shutdown.
- Messages are `Component.translatable("otis.link.<message>", args...)`. Arguments are components, and MiniMessage tags stay in the strings.

**Before writing code:** verify the exact store API against the Adventure version shipped with `velocity-api` 4.2.0 (Adventure 5.x). If MiniMessage stores are unavailable there, fall back to `TranslationStore.messageFormat` with MessageFormat patterns.

**Keys:**
- `otis.link.code.issued`, `otis.link.code.copy-hover`
- `otis.link.provider.unsupported-for-link`
- `otis.link.list.header`, `otis.link.list.entry.verified`, `otis.link.list.entry.unverified`, `otis.link.list.empty`
- `otis.link.unlinked`, `otis.link.social.set`
- `otis.link.error.rate-limited`, `otis.link.error.invalid-value`, `otis.link.error.already-verified`, `otis.link.error.unavailable`, `otis.link.error.players-only`

**Tests:**
- Unit test: every key in `otis_en` exists in `otis_de` and vice versa, and every key constant used in code exists in the fallback bundle. The keys are collected from a `Messages` constants class, so the test does not scan sources.
- Rendering test: `GlobalTranslator.render` with `Locale.GERMAN` and `Locale.FRENCH` produces German text and the English fallback.

**SOLID:** SRP. Text lives in bundles, not in code.

### D5 - Packaging

- `velocity-api` stays `compileOnly`; tests use it as `testImplementation`.
- The shadow JAR is checked for `net/kyori/` entries; there must be none.
- Plugin metadata version is unchanged.

**Tests:** the shadow JAR content check is a build verification task.

## Risks / Trade-offs

- **[The Adventure 5 translation API differs from 4.x docs]** → Verified in the first task, with a fallback to MessageFormat stores.
- **[Code shown in chat could be seen by screen-sharing viewers]** → The code expires in 10 minutes, is single use, and binds to the player who requested it.
- **[Proxies need a restart to get the commands]** → This is independent of the backend. Old plugins keep working, so the restart can wait for the next maintenance window.
- **[Otis outage]** → Commands fail fast with a translated message, and joins are unaffected.

## Migration Plan

1. Release a plugin version containing this change (published as `otis-plugin` with the next Otis release).
2. Replace the plugin JAR on the proxies at the next planned restart. No config change is needed.
3. Rollback: put the previous JAR back.
