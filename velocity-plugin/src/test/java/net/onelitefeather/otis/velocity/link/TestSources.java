package net.onelitefeather.otis.velocity.link;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Command sources for tests: a player (a {@link Proxy} that answers only what the commands use) and a console.
 */
final class TestSources {

    private TestSources() {
    }

    /**
     * A command source that records what it is sent.
     */
    interface Recording extends CommandSource {
        List<Component> received();
    }

    static Recording player(UUID uuid, String... permissions) {
        Set<String> granted = Set.of(permissions);
        List<Component> received = new ArrayList<>();
        return (Recording) Proxy.newProxyInstance(
                TestSources.class.getClassLoader(),
                new Class<?>[]{Player.class, Recording.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "hasPermission" -> granted.contains((String) args[0]);
                    case "getPermissionValue" -> granted.contains((String) args[0]) ? Tristate.TRUE : Tristate.FALSE;
                    case "received" -> received;
                    case "sendMessage" -> {
                        if (args.length == 1 && args[0] instanceof Component component) {
                            received.add(component);
                            yield null;
                        }
                        throw new UnsupportedOperationException(method.toString());
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "TestPlayer";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    static Recording console(String... permissions) {
        Set<String> granted = Set.of(permissions);
        List<Component> received = new ArrayList<>();
        return new Recording() {
            @Override
            public Tristate getPermissionValue(String permission) {
                return granted.contains(permission) ? Tristate.TRUE : Tristate.FALSE;
            }

            @Override
            public void sendMessage(Component message) {
                received.add(message);
            }

            @Override
            public List<Component> received() {
                return received;
            }
        };
    }
}
