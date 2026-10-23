package net.onelitefeather.otis.service;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import net.onelitefeather.otis.links.LinkCodes;

import java.security.SecureRandom;

/** Provides the link code generator with a cryptographically secure random source. */
@Factory
class LinkCodesFactory {

    @Singleton
    LinkCodes linkCodes() {
        return new LinkCodes(new SecureRandom());
    }
}
