package io.harness.app;

import com.google.common.collect.ImmutableSet;
import io.harness.serializer.*;

public class MockDelegateRegistrars {
    public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
            ImmutableSet.<Class<? extends KryoRegistrar>>builder()
                    .addAll(DelegateTaskRegistrars.kryoRegistrars)
                    .build();
}
