/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.opaclient;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.Provider;

@OwnedBy(HarnessTeam.PIPELINE)
public class OpaClientFactory extends AbstractHttpClientFactory implements Provider<OpaServiceClient> {
  public OpaClientFactory(ServiceHttpClientConfig clientConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId) {
    super(clientConfig, serviceSecret, tokenGenerator, kryoConverterFactory, clientId);
  }

  @Override
  public OpaServiceClient get() {
    return getSafeOkRetrofit().create(OpaServiceClient.class);
  }
}
