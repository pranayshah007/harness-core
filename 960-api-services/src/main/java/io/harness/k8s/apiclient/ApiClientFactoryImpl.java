/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.apiclient;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.model.KubernetesClusterAuthType.GCP_OAUTH;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.exception.runtime.KubernetesApiClientRuntimeException;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.oidc.OidcTokenRetriever;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.credentials.AccessTokenAuthentication;
import io.kubernetes.client.util.credentials.ClientCertificateAuthentication;
import io.kubernetes.client.util.credentials.UsernamePasswordAuthentication;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

@Singleton
public class ApiClientFactoryImpl implements ApiClientFactory {
  @Inject OidcTokenRetriever oidcTokenRetriever;

  @Override
  public ApiClient getClient(KubernetesConfig kubernetesConfig) {
    return fromKubernetesConfig(kubernetesConfig, oidcTokenRetriever);
  }

  public static ApiClient fromKubernetesConfig(KubernetesConfig kubernetesConfig, OidcTokenRetriever tokenRetriever) {
    // should we cache the client ?
    try {
      return createNewApiClient(kubernetesConfig, tokenRetriever);
    } catch (RuntimeException e) {
      throw new KubernetesApiClientRuntimeException(e.getMessage(), e.getCause());
    } catch (Exception e) {
      throw new KubernetesApiClientRuntimeException(e.getMessage(), e);
    }
  }

  private static ApiClient createNewApiClient(KubernetesConfig kubernetesConfig, OidcTokenRetriever tokenRetriever) {
    // Enable SSL validation only if CA Certificate provided with configuration
    ClientBuilder clientBuilder = new ClientBuilder().setVerifyingSsl(isNotEmpty(kubernetesConfig.getCaCert()));
    if (isNotBlank(kubernetesConfig.getMasterUrl())) {
      clientBuilder.setBasePath(kubernetesConfig.getMasterUrl());
    }
    if (kubernetesConfig.getCaCert() != null) {
      clientBuilder.setCertificateAuthority(decodeIfRequired(kubernetesConfig.getCaCert()));
    }
    if (kubernetesConfig.getServiceAccountTokenSupplier() != null) {
      if (GCP_OAUTH == kubernetesConfig.getAuthType()) {
        clientBuilder.setAuthentication(new GkeTokenAuthentication(kubernetesConfig.getServiceAccountTokenSupplier()));
      } else {
        clientBuilder.setAuthentication(
            new AccessTokenAuthentication(kubernetesConfig.getServiceAccountTokenSupplier().get()));
      }
    } else if (kubernetesConfig.getUsername() != null && kubernetesConfig.getPassword() != null) {
      clientBuilder.setAuthentication(new UsernamePasswordAuthentication(
          new String(kubernetesConfig.getUsername()), new String(kubernetesConfig.getPassword())));
    } else if (kubernetesConfig.getClientCert() != null && kubernetesConfig.getClientKey() != null) {
      clientBuilder.setAuthentication(new ClientCertificateAuthentication(
          decodeIfRequired(kubernetesConfig.getClientCert()), decodeIfRequired(kubernetesConfig.getClientKey())));
    } else if (tokenRetriever != null && KubernetesClusterAuthType.OIDC == kubernetesConfig.getAuthType()) {
      clientBuilder.setAuthentication(new AccessTokenAuthentication(tokenRetriever.getOidcIdToken(kubernetesConfig)));
    } else if (kubernetesConfig.getAzureConfig() != null && kubernetesConfig.getAzureConfig().getAadIdToken() != null) {
//      clientBuilder.setAuthentication(new AzureTokenAuthentication(kubernetesConfig.getAzureConfig().getAadIdToken()));
      clientBuilder.setAuthentication(new AccessTokenAuthentication(kubernetesConfig.getAzureConfig().getAadIdToken()));
    }

    ApiClient apiClient = clientBuilder.build();
    // don't timeout on client-side
    OkHttpClient httpClient = apiClient.getHttpClient()
                                  .newBuilder()
                                  .readTimeout(0, TimeUnit.SECONDS)
                                  .connectTimeout(0, TimeUnit.SECONDS)
                                  .build();
    apiClient.setHttpClient(httpClient);
    return apiClient;
  }

  // try catch is used as logic to detect if value is in base64 or not and no need to keep exception context
  @SuppressWarnings("squid:S1166")
  private static byte[] decodeIfRequired(char[] data) {
    try {
      return Base64.getDecoder().decode(new String(data));
    } catch (IllegalArgumentException ignore) {
      return new String(data).getBytes(UTF_8);
    }
  }
}
