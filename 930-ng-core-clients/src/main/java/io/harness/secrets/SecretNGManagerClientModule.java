/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.secrets.remote.SecretNGManagerHttpClientFactory;
import io.harness.secrets.services.NonPrivilegedSecretNGManagerClientServiceImpl;
import io.harness.secrets.services.PrivilegedSecretNGManagerClientServiceImpl;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

@OwnedBy(PL)
public class SecretNGManagerClientModule extends AbstractModule {
  private final ServiceHttpClientConfig serviceHttpClientConfig;
  private final String serviceSecret;
  private final String clientId;

  public SecretNGManagerClientModule(
      ServiceHttpClientConfig serviceHttpClientConfig, String serviceSecret, String clientId) {
    this.serviceHttpClientConfig = serviceHttpClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  @Provides
  @Named("PRIVILEGED")
  private SecretNGManagerHttpClientFactory privilegedSecretNGManagerHttpClientFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new SecretNGManagerHttpClientFactory(serviceHttpClientConfig, serviceSecret, new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, ClientMode.PRIVILEGED);
  }

  @Provides
  @Named("NON_PRIVILEGED")
  private SecretNGManagerHttpClientFactory nonPrivilegedSecretNGManagerHttpClientFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new SecretNGManagerHttpClientFactory(serviceHttpClientConfig, serviceSecret, new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, ClientMode.NON_PRIVILEGED);
  }

  @Override
  protected void configure() {
    bind(SecretNGManagerClient.class)
        .annotatedWith(Names.named(ClientMode.PRIVILEGED.name()))
        .toProvider(Key.get(SecretNGManagerHttpClientFactory.class, Names.named(ClientMode.PRIVILEGED.name())))
        .in(Scopes.SINGLETON);
    bind(SecretNGManagerClient.class)
        .toProvider(Key.get(SecretNGManagerHttpClientFactory.class, Names.named(ClientMode.NON_PRIVILEGED.name())))
        .in(Scopes.SINGLETON);

    bind(SecretManagerClientService.class).to(NonPrivilegedSecretNGManagerClientServiceImpl.class).in(Scopes.SINGLETON);
    bind(SecretManagerClientService.class)
        .annotatedWith(Names.named(ClientMode.PRIVILEGED.name()))
        .to(PrivilegedSecretNGManagerClientServiceImpl.class)
        .in(Scopes.SINGLETON);
    bind(SecretManagerClientService.class).to(NonPrivilegedSecretNGManagerClientServiceImpl.class).in(Scopes.SINGLETON);
  }
}
