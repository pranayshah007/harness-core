/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.okhttp.OkHttpClientFactory;
import okhttp3.OkHttpClient;

public class Fabric8HttpClientFactory extends OkHttpClientFactory {
  private final KubernetesHelperService kubernetesHelperService;
  private final Config config;

  public Fabric8HttpClientFactory(Config config, KubernetesHelperService kubernetesHelperService) {
    this.config = config;
    this.kubernetesHelperService = kubernetesHelperService;
  }

  @Override
  protected void additionalConfig(OkHttpClient.Builder builder) {
    kubernetesHelperService.configureHttpClientBuilder(builder, config);
  }
}
