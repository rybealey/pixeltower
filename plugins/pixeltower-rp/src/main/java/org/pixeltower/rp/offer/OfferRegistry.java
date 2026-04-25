package org.pixeltower.rp.offer;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Static lookup of {@link OfferService} implementations keyed by their
 * {@code key()} (e.g. {@code "heal"}). Populated at plugin load via
 * {@link #register} from {@code PixeltowerRP.onEmulatorLoaded}.
 *
 * Thread-safety: writes happen at boot, reads happen on every
 * {@code :offer} command. Backed by a concurrent map for safety.
 */
public final class OfferRegistry {

    private static final ConcurrentMap<String, OfferService> SERVICES = new ConcurrentHashMap<>();

    private OfferRegistry() {}

    public static void register(OfferService service) {
        SERVICES.put(service.key().toLowerCase(), service);
    }

    public static Optional<OfferService> lookup(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(SERVICES.get(key.toLowerCase()));
    }
}
