/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.instanceng;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.kryo.KryoConverterFactory;
import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;

import com.google.inject.Provider;

@OwnedBy(HarnessTeam.CE)
public class InstanceNGResourceHttpClientFactory
    extends AbstractHttpClientFactory implements Provider<InstanceNGResourceClient> {
  public InstanceNGResourceHttpClientFactory(ServiceHttpClientConfig ngManagerClientConfig, String serviceSecret,
      ServiceTokenGenerator serviceTokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId,
      ClientMode clientMode) {
    super(
        ngManagerClientConfig, serviceSecret, serviceTokenGenerator, kryoConverterFactory, clientId, false, clientMode);
  }

  @Override
  public InstanceNGResourceClient get() {
    return getRetrofit().create(InstanceNGResourceClient.class);
  }
}
