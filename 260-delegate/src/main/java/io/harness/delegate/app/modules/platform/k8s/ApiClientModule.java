/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules.platform.k8s;

import io.harness.delegate.service.core.apiclient.CoreV1ApiWithRetry;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Config;
import java.io.IOException;

public class ApiClientModule extends AbstractModule {
  @Provides
  @Singleton
  public CoreV1Api replacingCoreV1Api(final ApiClient apiClient) {
    return new CoreV1ApiWithRetry(apiClient);
  }

  @Override
  protected void configure() {
    try {
      final var delegateType = System.getenv().get("DELEGATE_TYPE");
      if ("KUBERNETES".equals(delegateType)) {
        bind(ApiClient.class).toInstance(ClientBuilder.cluster().build());
      } else { // K8S platform runner can only be real K8S or local in which case we need different API client
        bind(ApiClient.class).toInstance(Config.defaultClient());
      }
    } catch (IOException e) {
      throw new IllegalStateException("Can't create K8S API client");
    }
  }
}
