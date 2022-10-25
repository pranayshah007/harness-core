/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.instancesync;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.kryo.KryoConverterFactory;
import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;

import com.google.inject.Provider;

@OwnedBy(HarnessTeam.DX)
public class InstanceSyncResourceHttpClientFactory
    extends AbstractHttpClientFactory implements Provider<InstanceSyncResourceClient> {
  public InstanceSyncResourceHttpClientFactory(ServiceHttpClientConfig ngManagerClientConfig, String serviceSecret,
      ServiceTokenGenerator serviceTokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId,
      ClientMode clientMode) {
    super(
        ngManagerClientConfig, serviceSecret, serviceTokenGenerator, kryoConverterFactory, clientId, false, clientMode);
  }

  @Override
  public InstanceSyncResourceClient get() {
    return getRetrofit().create(InstanceSyncResourceClient.class);
  }
}
