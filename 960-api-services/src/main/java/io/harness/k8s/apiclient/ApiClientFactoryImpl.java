/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.apiclient;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.model.KubernetesClusterAuthType.GCP_OAUTH;
import static io.harness.network.Http.getProxyPassword;
import static io.harness.network.Http.getProxyUserName;

import static java.nio.charset.StandardCharsets.UTF_8;
import static okhttp3.Protocol.HTTP_1_1;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.runtime.KubernetesApiClientRuntimeException;
import io.harness.exception.runtime.utils.KubernetesCertificateType;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.oidc.OidcTokenRetriever;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.credentials.AccessTokenAuthentication;
import io.kubernetes.client.util.credentials.ClientCertificateAuthentication;
import io.kubernetes.client.util.credentials.KubeconfigAuthentication;
import io.kubernetes.client.util.credentials.UsernamePasswordAuthentication;
import java.io.StringReader;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Singleton
public class ApiClientFactoryImpl implements ApiClientFactory {
  private static final ConnectionPool connectionPool;
  @Inject OidcTokenRetriever oidcTokenRetriever;
  private static final long DEFAULT_READ_TIMEOUT_SECONDS = 120;
  private static final long DEFAULT_CONNECTION_TIMEOUT_SECONDS = 60;

  private static final String READ_TIMEOUT_ENV_VAR = "K8S_API_CLIENT_READ_TIMEOUT";
  private static final String CONNECT_TIMEOUT_ENV_VAR = "K8S_API_CLIENT_CONNECT_TIMEOUT";

  static {
    connectionPool = new ConnectionPool(32, 5L, TimeUnit.MINUTES);
  }

  @Override
  public ApiClient getClient(KubernetesConfig kubernetesConfig) {
    return fromKubernetesConfig(kubernetesConfig, oidcTokenRetriever);
  }

  public static ApiClient fromKubernetesConfig(KubernetesConfig kubernetesConfig, OidcTokenRetriever tokenRetriever) {
    // should we cache the client ?
    try {
      return createNewApiClient(kubernetesConfig, tokenRetriever, false);
    } catch (RuntimeException e) {
      throw new KubernetesApiClientRuntimeException(
          e.getMessage(), e.getCause(), getKubernetesConfigCertificateType(kubernetesConfig));
    } catch (Exception e) {
      throw new KubernetesApiClientRuntimeException(
          e.getMessage(), e, getKubernetesConfigCertificateType(kubernetesConfig));
    }
  }

  public static ApiClient fromKubernetesConfigWithReadTimeout(
      KubernetesConfig kubernetesConfig, OidcTokenRetriever tokenRetriever) {
    // should we cache the client ?
    try {
      return createNewApiClient(kubernetesConfig, tokenRetriever, true);
    } catch (RuntimeException e) {
      throw new KubernetesApiClientRuntimeException(
          e.getMessage(), e.getCause(), getKubernetesConfigCertificateType(kubernetesConfig));
    } catch (Exception e) {
      throw new KubernetesApiClientRuntimeException(
          e.getMessage(), e, getKubernetesConfigCertificateType(kubernetesConfig));
    }
  }

  private static ApiClient createNewApiClient(
      KubernetesConfig kubernetesConfig, OidcTokenRetriever tokenRetriever, boolean useNewReadTimeoutForValidation) {
    // Enable SSL validation only if CA Certificate provided with configuration
    ClientBuilder clientBuilder = new ClientBuilder().setVerifyingSsl(isNotEmpty(kubernetesConfig.getCaCert()));
    if (isNotBlank(kubernetesConfig.getMasterUrl())) {
      clientBuilder.setBasePath(kubernetesConfig.getMasterUrl());
    }
    if (kubernetesConfig.getCaCert() != null) {
      clientBuilder.setCertificateAuthority(decodeIfRequired(kubernetesConfig.getCaCert()));
    }
    if (kubernetesConfig.getServiceAccountTokenSupplier() != null) {
      addSATokenAuthentication(kubernetesConfig, clientBuilder);
    } else if (kubernetesConfig.getUsername() != null && kubernetesConfig.getPassword() != null) {
      clientBuilder.setAuthentication(new UsernamePasswordAuthentication(
          new String(kubernetesConfig.getUsername()), new String(kubernetesConfig.getPassword())));
    } else if (kubernetesConfig.getClientCert() != null && kubernetesConfig.getClientKey() != null) {
      clientBuilder.setAuthentication(new ClientCertificateAuthentication(
          decodeIfRequired(kubernetesConfig.getClientCert()), decodeIfRequired(kubernetesConfig.getClientKey())));
    } else if (tokenRetriever != null && KubernetesClusterAuthType.OIDC == kubernetesConfig.getAuthType()) {
      clientBuilder.setAuthentication(new AccessTokenAuthentication(tokenRetriever.getOidcIdToken(kubernetesConfig)));
    } else if (kubernetesConfig.getAzureConfig() != null && kubernetesConfig.getAzureConfig().getAadIdToken() != null) {
      //      clientBuilder.setAuthentication(new
      //      AzureTokenAuthentication(kubernetesConfig.getAzureConfig().getAadIdToken()));
      clientBuilder.setAuthentication(new AccessTokenAuthentication(kubernetesConfig.getAzureConfig().getAadIdToken()));
    } else if (kubernetesConfig.isUseKubeconfigAuthentication()) {
      addKubeConfigAuthentication(kubernetesConfig, clientBuilder);
    }

    ApiClient apiClient = clientBuilder.build();
    long connectTimeout =
        K8sApiClientHelper.getTimeout(CONNECT_TIMEOUT_ENV_VAR).orElse(DEFAULT_CONNECTION_TIMEOUT_SECONDS);
    long readTimeout = K8sApiClientHelper.getTimeout(READ_TIMEOUT_ENV_VAR).orElse(DEFAULT_READ_TIMEOUT_SECONDS);

    OkHttpClient.Builder builder = apiClient.getHttpClient()
                                       .newBuilder()
                                       .readTimeout(useNewReadTimeoutForValidation ? readTimeout : 0, TimeUnit.SECONDS)
                                       .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                                       .connectionPool(connectionPool)
                                       .protocols(List.of(HTTP_1_1));
    String user = getProxyUserName();
    if (isNotEmpty(user)) {
      String password = getProxyPassword();
      builder.proxyAuthenticator((route, response) -> {
        if (response == null || response.code() == 407) {
          return null;
        }
        String credential = Credentials.basic(user, password);
        return response.request().newBuilder().header("Proxy-Authorization", credential).build();
      });
    }
    apiClient.setHttpClient(builder.build());
    return apiClient;
  }

  private static void addKubeConfigAuthentication(KubernetesConfig kubernetesConfig, ClientBuilder clientBuilder) {
    String kubeConfigString = K8sApiClientHelper.generateExecFormatKubeconfig(kubernetesConfig);
    try {
      KubeConfig kubeConfig = KubeConfig.loadKubeConfig(new StringReader(kubeConfigString));
      clientBuilder.setAuthentication(new KubeconfigAuthentication(kubeConfig));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void addSATokenAuthentication(KubernetesConfig kubernetesConfig, ClientBuilder clientBuilder) {
    if (GCP_OAUTH == kubernetesConfig.getAuthType()) {
      clientBuilder.setAuthentication(new GkeTokenAuthentication(kubernetesConfig.getServiceAccountTokenSupplier()));
    } else {
      clientBuilder.setAuthentication(
          new AccessTokenAuthentication(kubernetesConfig.getServiceAccountTokenSupplier().get().trim()));
    }
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

  private static KubernetesCertificateType getKubernetesConfigCertificateType(KubernetesConfig kubernetesConfig) {
    if (isNotEmpty(kubernetesConfig.getCaCert()) && isNotEmpty(kubernetesConfig.getClientCert())) {
      return KubernetesCertificateType.BOTH_CA_AND_CLIENT_CERTIFICATE;
    }
    if (isNotEmpty(kubernetesConfig.getCaCert())) {
      return KubernetesCertificateType.CA_CERTIFICATE;
    }
    if (isNotEmpty(kubernetesConfig.getClientCert())) {
      return KubernetesCertificateType.CLIENT_CERTIFICATE;
    }
    return KubernetesCertificateType.NONE;
  }
}
