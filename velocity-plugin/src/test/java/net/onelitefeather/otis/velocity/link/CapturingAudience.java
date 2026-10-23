package net.onelitefeather.otis.velocity.link;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Test double that records the messages an {@link Audience} receives.
 */
final class CapturingAudience implements Audience {

    private final OtisTranslations TRANSLATIONS = new OtisTranslations();

    private final List<Component> messages = new ArrayList<>();

    @Override
    public void sendMessage(@NotNull Component message) {
        messages.add(message);
    }

    List<Component> messages() {
        return List.copyOf(messages);
    }

    /**
     * @return the translation keys of the received messages, in order
     */
    List<String> keys() {
        return messages.stream()
                .map(message -> message instanceof TranslatableComponent translatable ? translatable.key() : "<not translatable>")
                .toList();
    }

    /**
     * @return the received messages rendered in {@code locale} as plain text
     */
    List<String> plain(Locale locale) {
        return messages.stream()
                .map(message -> PlainTextComponentSerializer.plainText().serialize(TRANSLATIONS.render(message, locale)))
                .toList();
    }

    /**
     * @return the payload of the first copy-to-clipboard click event in the rendered message, if any
     */
    Optional<String> copiedText(int index, Locale locale) {
        return find(TRANSLATIONS.render(messages.get(index), locale));
    }

    private Optional<String> find(Component component) {
        ClickEvent click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.COPY_TO_CLIPBOARD
                && click.payload() instanceof ClickEvent.Payload.Text text) {
            return Optional.of(text.value());
        }
        for (Component child : component.children()) {
            Optional<String> found = find(child);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
