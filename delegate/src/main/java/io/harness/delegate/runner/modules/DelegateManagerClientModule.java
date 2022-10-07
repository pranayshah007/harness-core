/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.runner.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.runner.managerclient.DelegateAgentManagerClient;
import io.harness.delegate.runner.managerclient.DelegateAgentManagerClientFactory;
import io.harness.security.TokenGenerator;;

public class DelegateManagerClientModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DelegateAgentManagerClient.class).toProvider(DelegateAgentManagerClientFactory.class);
    }

    @Provides
    @Singleton
    public TokenGenerator getTokenGenerator(DelegateConfiguration delegateConfiguration) {
        return new TokenGenerator(delegateConfiguration.getAccountId(), delegateConfiguration.getDelegateToken());
    }
}
