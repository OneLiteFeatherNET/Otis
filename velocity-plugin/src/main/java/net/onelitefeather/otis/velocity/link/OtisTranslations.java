package net.onelitefeather.otis.velocity.link;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore;
import net.kyori.adventure.text.renderer.TranslatableComponentRenderer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * The plugin's translation store: MiniMessage strings from the bundles {@code lang/otis_en.properties}
 * (fallback) and {@code lang/otis_de.properties}, registered with Adventure's {@link GlobalTranslator}
 * so that Velocity renders every {@code otis.link.*} component in the player's locale.
 */
public final class OtisTranslations implements AutoCloseable {

    private static final Key STORE_KEY = Key.key("otis", "messages");
    private static final List<Locale> SHIPPED = List.of(Locale.ENGLISH, Locale.GERMAN);

    private final MiniMessageTranslationStore store = MiniMessageTranslationStore.create(STORE_KEY);
    private boolean installed;

    public OtisTranslations() {
        this.store.defaultLocale(Locale.ENGLISH);
        for (Locale locale : SHIPPED) {
            this.store.registerAll(locale, bundle(locale), true);
        }
    }

    /**
     * Loads a shipped bundle as UTF-8.
     *
     * @param locale {@link Locale#ENGLISH} or {@link Locale#GERMAN}
     * @return the bundle
     */
    public static @NotNull ResourceBundle bundle(@NotNull Locale locale) {
        String path = "/lang/otis_" + locale.getLanguage() + ".properties";
        try (InputStream in = OtisTranslations.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing translation bundle " + path);
            }
            return new PropertyResourceBundle(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Adds the store to the {@link GlobalTranslator}. Calling it twice has no further effect.
     */
    public void install() {
        if (!installed) {
            installed = GlobalTranslator.translator().addSource(store);
        }
    }

    /**
     * Removes the store from the {@link GlobalTranslator}.
     */
    @Override
    public void close() {
        if (installed) {
            GlobalTranslator.translator().removeSource(store);
            installed = false;
        }
    }

    /**
     * Renders translatable components with this store only, without touching global state.
     *
     * @param component the component to render
     * @param locale    the viewer's locale
     * @return the rendered component
     */
    public @NotNull Component render(@NotNull Component component, @NotNull Locale locale) {
        return TranslatableComponentRenderer.usingTranslationSource(store).render(component, locale);
    }
}
