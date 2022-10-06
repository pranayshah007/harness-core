/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.accesscontrol;

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

@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
@OwnedBy(PL)
public class AccessControlAdminHttpClientFactory
    extends AbstractHttpClientFactory implements Provider<AccessControlAdminClient> {
  public AccessControlAdminHttpClientFactory(ServiceHttpClientConfig accessControlAdminClientConfig,
      String serviceSecret, ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory,
      String clientId, ClientMode clientMode) {
    super(accessControlAdminClientConfig, serviceSecret, tokenGenerator, kryoConverterFactory, clientId, false,
        clientMode);
  }

  @Override
  public AccessControlAdminClient get() {
    return getUnsafeOkRetrofit().create(AccessControlAdminClient.class);
  }
}
