/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.encryption.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;
import static io.harness.encryption.FieldWithPlainTextOrSecretValueHelper.getValueFromPlainTextOrSecretRef;
import static io.harness.k8s.KubernetesHelperService.getKubernetesConfigFromDefaultKubeConfigFile;
import static io.harness.k8s.KubernetesHelperService.getKubernetesConfigFromServiceAccount;
import static io.harness.k8s.KubernetesHelperService.isRunningInCluster;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClientKeyCertDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesOpenIdConnectDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesServiceAccountDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesUserNamePasswordDTO;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesConfig.KubernetesConfigBuilder;
import io.harness.k8s.model.OidcGrantType;

import com.google.inject.Singleton;
import java.util.Base64;

@Singleton
@OwnedBy(CDP)
public class K8sYamlToDelegateDTOMapper {
  public KubernetesConfig createKubernetesConfigFromClusterConfig(KubernetesClusterConfigDTO clusterConfigDTO) {
    return createKubernetesConfigFromClusterConfig(clusterConfigDTO, null);
  }

  public KubernetesConfig createKubernetesConfigFromClusterConfig(
      KubernetesClusterConfigDTO clusterConfigDTO, String namespace) {
    String namespaceNotBlank = isNotBlank(namespace) ? namespace : "default";
    KubernetesCredentialType kubernetesCredentialType = clusterConfigDTO.getCredential().getKubernetesCredentialType();

    switch (kubernetesCredentialType) {
      case INHERIT_FROM_DELEGATE:
        if (isRunningInCluster()) {
          return getKubernetesConfigFromServiceAccount(namespaceNotBlank);
        } else {
          return getKubernetesConfigFromDefaultKubeConfigFile(namespaceNotBlank);
        }

      case MANUAL_CREDENTIALS:
        return getKubernetesConfigFromManualCredentials(
            (KubernetesClusterDetailsDTO) (clusterConfigDTO.getCredential().getConfig()), namespaceNotBlank);

      default:
        throw new UnsupportedOperationException(
            String.format("Unsupported Kubernetes Credential type: [%s]", kubernetesCredentialType));
    }
  }

  private KubernetesConfig getKubernetesConfigFromManualCredentials(
      KubernetesClusterDetailsDTO clusterDetailsDTO, String namespace) {
    KubernetesConfigBuilder kubernetesConfigBuilder =
        KubernetesConfig.builder().masterUrl(clusterDetailsDTO.getMasterUrl()).namespace(namespace);

    // ToDo This does not handle the older KubernetesClusterConfigs which do not have authType set.
    KubernetesAuthDTO authDTO = clusterDetailsDTO.getAuth();
    switch (authDTO.getAuthType()) {
      case USER_PASSWORD:
        kubernetesConfigBuilder.authType(KubernetesClusterAuthType.USER_PASSWORD);
        KubernetesUserNamePasswordDTO userNamePasswordDTO = (KubernetesUserNamePasswordDTO) authDTO.getCredentials();
        kubernetesConfigBuilder.username(
            getValueFromPlainTextOrSecretRef(userNamePasswordDTO.getUsername(), userNamePasswordDTO.getUsernameRef()));
        kubernetesConfigBuilder.password(userNamePasswordDTO.getPasswordRef().getDecryptedValue());
        break;

      case CLIENT_KEY_CERT:
        kubernetesConfigBuilder.authType(KubernetesClusterAuthType.CLIENT_KEY_CERT);
        KubernetesClientKeyCertDTO clientKeyCertDTO = (KubernetesClientKeyCertDTO) authDTO.getCredentials();
        kubernetesConfigBuilder.clientCert(
            addPaddingInSecretIfNeeded(clientKeyCertDTO.getClientCertRef().getDecryptedValue()));
        kubernetesConfigBuilder.clientKey(
            addPaddingInSecretIfNeeded(clientKeyCertDTO.getClientKeyRef().getDecryptedValue()));
        kubernetesConfigBuilder.clientKeyPassphrase(clientKeyCertDTO.getClientKeyPassphraseRef() != null
                ? clientKeyCertDTO.getClientKeyPassphraseRef().getDecryptedValue()
                : null);
        kubernetesConfigBuilder.clientKeyAlgo(clientKeyCertDTO.getClientKeyAlgo());
        kubernetesConfigBuilder.caCert(clientKeyCertDTO.getCaCertRef() != null
                ? addPaddingInSecretIfNeeded(clientKeyCertDTO.getCaCertRef().getDecryptedValue())
                : null);
        break;

      case SERVICE_ACCOUNT:
        kubernetesConfigBuilder.authType(KubernetesClusterAuthType.SERVICE_ACCOUNT);
        KubernetesServiceAccountDTO serviceAccountDTO = (KubernetesServiceAccountDTO) authDTO.getCredentials();
        kubernetesConfigBuilder.serviceAccountTokenSupplier(
            () -> new String(serviceAccountDTO.getServiceAccountTokenRef().getDecryptedValue()));
        kubernetesConfigBuilder.caCert(
            serviceAccountDTO.getCaCertRef() != null ? serviceAccountDTO.getCaCertRef().getDecryptedValue() : null);
        break;

      case OPEN_ID_CONNECT:
        kubernetesConfigBuilder.authType(KubernetesClusterAuthType.OIDC);
        KubernetesOpenIdConnectDTO openIdConnectDTO = (KubernetesOpenIdConnectDTO) authDTO.getCredentials();

        kubernetesConfigBuilder.oidcClientId(openIdConnectDTO.getOidcClientIdRef().getDecryptedValue());
        kubernetesConfigBuilder.oidcSecret(openIdConnectDTO.getOidcSecretRef() != null
                ? openIdConnectDTO.getOidcSecretRef().getDecryptedValue()
                : null);
        String oidcUsername = getSecretAsStringFromPlainTextOrSecretRef(
            openIdConnectDTO.getOidcUsername(), openIdConnectDTO.getOidcUsernameRef());
        kubernetesConfigBuilder.oidcUsername(oidcUsername);
        kubernetesConfigBuilder.oidcPassword(openIdConnectDTO.getOidcPasswordRef().getDecryptedValue());
        kubernetesConfigBuilder.oidcGrantType(OidcGrantType.password);
        kubernetesConfigBuilder.oidcIdentityProviderUrl(openIdConnectDTO.getOidcIssuerUrl());
        kubernetesConfigBuilder.oidcScopes(openIdConnectDTO.getOidcScopes());
        break;

      default:
        throw new UnsupportedOperationException(
            String.format("Unsupported Manual Credential type: [%s]", authDTO.getAuthType()));
    }
    return kubernetesConfigBuilder.build();
  }

  private char[] addPaddingInSecretIfNeeded(char[] secret) {
    try {
      byte[] decodedBytes = Base64.getDecoder().decode(new String(secret));
      String finalSecret = new String(Base64.getEncoder().encode(decodedBytes));
      return finalSecret.toCharArray();
    } catch (Exception ex) {
      return secret;
    }
  }
}
