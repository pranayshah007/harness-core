/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.account;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@OwnedBy(PL)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
public class AccountHttpClientFactory extends AbstractHttpClientFactory implements Provider<AccountClient> {
  public AccountHttpClientFactory(ServiceHttpClientConfig secretManagerConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId,
      ClientMode clientMode) {
    super(secretManagerConfig, serviceSecret, tokenGenerator, kryoConverterFactory, clientId, false, clientMode);
  }

  @Override
  public AccountClient get() {
    return getRetrofit().create(AccountClient.class);
  }
}
