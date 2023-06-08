/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.githubpackages.client;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.docker.DockerRegistryRestClient;
import io.harness.artifacts.githubpackages.beans.GithubPackagesInternalConfig;
import io.harness.network.Http;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.converter.jaxb.JaxbConverterFactory;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class GithubPackagesRestClientFactoryImpl implements GithubPackagesRestClientFactory {
  @Override
  public GithubPackagesRestClient getGithubPackagesRestClient(
      GithubPackagesInternalConfig githubPackagesInternalConfig) {
    String url = getUrl();

    OkHttpClient okHttpClient = Http.getOkHttpClient(url, githubPackagesInternalConfig.isCertValidationRequired());

    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();

    return retrofit.create(GithubPackagesRestClient.class);
  }

  private String getUrl() {
    return "https://api.github.com";
  }

  @Override
  public DockerRegistryRestClient getGithubPackagesDockerRestClient(
      GithubPackagesInternalConfig githubPackagesInternalConfig) {
    String url = getDockerAPIUrl();
    OkHttpClient okHttpClient = Http.getOkHttpClient(url, githubPackagesInternalConfig.isCertValidationRequired());
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(DockerRegistryRestClient.class);
  }

  public GithubPackagesMavenRestClient getGithubPackagesMavenRestClient(
      GithubPackagesInternalConfig githubPackagesInternalConfig) {
    String url = getMavenUrl();

    OkHttpClient okHttpClient = Http.getOkHttpClient(url, githubPackagesInternalConfig.isCertValidationRequired());

    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addConverterFactory(JaxbConverterFactory.create())
                            .build();

    return retrofit.create(GithubPackagesMavenRestClient.class);
  }

  private String getDockerAPIUrl() {
    return "https://ghcr.io";
  }
  private String getMavenUrl() {
    return "https://maven.pkg.github.com";
  }
}
