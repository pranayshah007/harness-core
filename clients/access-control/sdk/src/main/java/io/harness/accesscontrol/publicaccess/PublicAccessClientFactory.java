/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.publicaccess;

import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;

import com.google.inject.Provider;

public class PublicAccessClientFactory extends AbstractHttpClientFactory implements Provider<PublicAccessClient> {
  protected PublicAccessClientFactory(ServiceHttpClientConfig httpClientConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, String clientId) {
    super(httpClientConfig, serviceSecret, tokenGenerator, null, clientId, false, ClientMode.PRIVILEGED);
  }

  @Override
  public PublicAccessClient get() {
    return getRetrofit().create(PublicAccessClient.class);
  }
}
