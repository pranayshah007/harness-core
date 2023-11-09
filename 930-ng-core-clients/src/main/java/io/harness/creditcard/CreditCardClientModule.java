/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.creditcard;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.creditcard.remote.CreditCardClient;
import io.harness.creditcard.remote.CreditCardClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

@OwnedBy(PL)
public class CreditCardClientModule extends AbstractModule {
  private static CreditCardClientModule instance;
  private final ServiceHttpClientConfig creditCardClientConfig;
  private final String serviceSecret;
  private final String clientId;

  public CreditCardClientModule(ServiceHttpClientConfig creditCardClientConfig, String serviceSecret, String clientId) {
    this.creditCardClientConfig = creditCardClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  public static CreditCardClientModule getInstance(
      ServiceHttpClientConfig serviceHttpClientConfig, String serviceSecret, String clientId) {
    if (instance == null) {
      instance = new CreditCardClientModule(serviceHttpClientConfig, serviceSecret, clientId);
    }

    return instance;
  }

  @Provides
  @Named("PRIVILEGED")
  @Singleton
  private CreditCardClientFactory privilegedCreditCardClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new CreditCardClientFactory(creditCardClientConfig, serviceSecret, new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, ClientMode.PRIVILEGED);
  }

  @Provides
  @Named("NON_PRIVILEGED")
  @Singleton
  private CreditCardClientFactory nonPrivilegedCreditCardClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new CreditCardClientFactory(creditCardClientConfig, serviceSecret, new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, ClientMode.NON_PRIVILEGED);
  }

  @Override
  protected void configure() {
    bind(CreditCardClient.class)
        .annotatedWith(Names.named(ClientMode.PRIVILEGED.name()))
        .toProvider(Key.get(CreditCardClientFactory.class, Names.named(ClientMode.PRIVILEGED.name())))
        .in(Scopes.SINGLETON);
    bind(CreditCardClient.class)
        .toProvider(Key.get(CreditCardClientFactory.class, Names.named(ClientMode.NON_PRIVILEGED.name())))
        .in(Scopes.SINGLETON);
  }
}
