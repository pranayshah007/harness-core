/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifacts.docker.client;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.docker.DockerRegistryRestClient;
import io.harness.artifacts.docker.HarborRestClient;
import io.harness.artifacts.docker.beans.DockerInternalConfig;
import io.harness.network.Http;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class DockerRestClientFactoryImpl implements DockerRestClientFactory {
  @Override
  public DockerRegistryRestClient getDockerRegistryRestClient(DockerInternalConfig dockerConfig) {
    OkHttpClient okHttpClient =
        Http.getOkHttpClient(dockerConfig.getDockerRegistryUrl(), dockerConfig.isCertValidationRequired());
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(dockerConfig.getDockerRegistryUrl())
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(DockerRegistryRestClient.class);
  }

  public static HarborRestClient getHarborRestClient(DockerInternalConfig dockerConfig) {
    OkHttpClient okHttpClient =
        Http.getOkHttpClient(dockerConfig.getDockerRegistryUrl(), dockerConfig.isCertValidationRequired());
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(dockerConfig.getDockerRegistryUrl())
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(HarborRestClient.class);
  }
}
