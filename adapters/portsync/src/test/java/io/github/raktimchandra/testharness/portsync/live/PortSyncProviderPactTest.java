package io.github.raktimchandra.testharness.portsync.live;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import io.github.raktimchandra.testharness.portsync.PortSync;
import io.github.raktimchandra.testharness.portsync.PortSyncReady;
import java.net.URI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Verifies the adapter's consumer contract ({@code contracts/pacts/
 * testharness-portsync-adapter-portsync-core.json}, from contract.PortSyncConsumerPactTest)
 * against the running PortSync core: each interaction is replayed and the real response is
 * matched against the contract. Runs in portsync.yml with {@code -Dportsync.live}.
 */
@Provider("portsync-core")
@PactFolder("../../contracts/pacts")
class PortSyncProviderPactTest {

    @BeforeAll
    static void portSyncIsReady() {
        PortSyncReady.await(PortSync.get());
    }

    @BeforeEach
    void target(PactVerificationContext context) {
        URI api = URI.create(PortSync.get().apiUrl());
        context.setTarget(new HttpTestTarget(api.getHost(), api.getPort(), api.getPath().isEmpty() ? "/" : api.getPath()));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void interaction(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
