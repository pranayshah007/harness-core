/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO.builder;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.MANUAL_CREDENTIALS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClientKeyCertDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO.KubernetesClusterConfigDTOBuilder;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesOpenIdConnectDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesServiceAccountDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesUserNamePasswordDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Slf4j
public class KubernetesConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    KubernetesClusterConfig clusterConfig = (KubernetesClusterConfig) settingAttribute.getValue();
    List<String> secretRefs = new ArrayList<>();
    secretRefs.add(clusterConfig.getEncryptedCaCert());
    secretRefs.add(clusterConfig.getEncryptedUsername());
    secretRefs.add(clusterConfig.getEncryptedPassword());
    secretRefs.add(clusterConfig.getEncryptedClientKey());
    secretRefs.add(clusterConfig.getEncryptedClientCert());
    secretRefs.add(clusterConfig.getEncryptedClientKeyPassphrase());
    secretRefs.add(clusterConfig.getEncryptedServiceAccountToken());
    secretRefs.add(clusterConfig.getEncryptedOidcClientId());
    secretRefs.add(clusterConfig.getEncryptedOidcPassword());
    secretRefs.add(clusterConfig.getEncryptedOidcSecret());
    return secretRefs;
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.KUBERNETES_CLUSTER;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    KubernetesClusterConfig clusterConfig = (KubernetesClusterConfig) settingAttribute.getValue();
    KubernetesClusterAuthType authType = clusterConfig.getAuthType();
    KubernetesClusterConfigDTOBuilder builder = builder().delegateSelectors(clusterConfig.getDelegateSelectors());
    KubernetesCredentialDTO credentialDTO;
    String masterUrl = clusterConfig.getMasterUrl();
    String oidcIdentityProviderUrl = clusterConfig.getOidcIdentityProviderUrl();
    SecretRefData usernameRef =
        MigratorUtility.getSecretRefDefaultNull(migratedEntities, clusterConfig.getEncryptedUsername());
    SecretRefData caCertRef =
        MigratorUtility.getSecretRefDefaultNull(migratedEntities, clusterConfig.getEncryptedCaCert());
    SecretRefData passwordRef =
        MigratorUtility.getSecretRefDefaultNull(migratedEntities, clusterConfig.getEncryptedPassword());
    SecretRefData serviceAccountTokenRef =
        MigratorUtility.getSecretRefDefaultNull(migratedEntities, clusterConfig.getEncryptedServiceAccountToken());
    SecretRefData clientIdRef =
        MigratorUtility.getSecretRefDefaultNull(migratedEntities, clusterConfig.getEncryptedOidcClientId());
    SecretRefData oidcPasswordRef =
        MigratorUtility.getSecretRefDefaultNull(migratedEntities, clusterConfig.getEncryptedOidcPassword());
    SecretRefData clientCertRef =
        MigratorUtility.getSecretRefDefaultNull(migratedEntities, clusterConfig.getEncryptedClientCert());
    SecretRefData clientKeyRef =
        MigratorUtility.getSecretRefDefaultNull(migratedEntities, clusterConfig.getEncryptedClientKey());

    if (authType == null) {
      credentialDTO = KubernetesCredentialDTO.builder().kubernetesCredentialType(INHERIT_FROM_DELEGATE).build();
    } else if ((authType.equals(KubernetesClusterAuthType.NONE) && ObjectUtils.allNotNull(masterUrl, passwordRef))
        || authType.equals(KubernetesClusterAuthType.USER_PASSWORD)) {
      credentialDTO = getUsernamePasswordCredentials(masterUrl, clusterConfig.getUsername(), usernameRef, passwordRef);
    } else if ((authType.equals(KubernetesClusterAuthType.NONE)
                   && ObjectUtils.allNotNull(masterUrl, serviceAccountTokenRef))
        || authType.equals(KubernetesClusterAuthType.SERVICE_ACCOUNT)) {
      credentialDTO = getServiceAccountCredentials(masterUrl, serviceAccountTokenRef, caCertRef);
    } else if ((authType.equals(KubernetesClusterAuthType.NONE)
                   && ObjectUtils.allNotNull(masterUrl, oidcIdentityProviderUrl, oidcPasswordRef, clientIdRef))
        || authType.equals(KubernetesClusterAuthType.OIDC)) {
      SecretRefData oidcSecretRef =
          MigratorUtility.getSecretRefDefaultNull(migratedEntities, clusterConfig.getEncryptedOidcSecret());
      credentialDTO = getOidcCredentials(masterUrl, oidcIdentityProviderUrl, clusterConfig.getOidcUsername(),
          clusterConfig.getOidcScopes(), usernameRef, oidcPasswordRef, clientIdRef, oidcSecretRef);
    } else if (authType.equals(KubernetesClusterAuthType.NONE)
        && ObjectUtils.allNotNull(masterUrl, clientCertRef, clientKeyRef)) {
      SecretRefData clientKeyPassphraseRef =
          MigratorUtility.getSecretRefDefaultNull(migratedEntities, clusterConfig.getEncryptedClientKeyPassphrase());
      credentialDTO = getClientKeyCertCredentials(
          masterUrl, caCertRef, clientCertRef, clientKeyRef, clientKeyPassphraseRef, clusterConfig.getClientKeyAlgo());
    } else if (authType.equals(KubernetesClusterAuthType.NONE)) {
      credentialDTO = null;
      log.warn("Kubernetes NG auth does not support this configuration");
    } else {
      throw new InvalidRequestException("K8s Auth type not supported");
    }

    return builder.credential(credentialDTO).build();
  }

  private KubernetesCredentialDTO getUsernamePasswordCredentials(
      String masterUrl, char[] username, SecretRefData usernameRef, SecretRefData encryptedPassword) {
    KubernetesAuthCredentialDTO kubernetesAuthCredentialDTO =
        KubernetesUserNamePasswordDTO.builder()
            .username(isEmpty(username) ? null : new String(username))
            .usernameRef(usernameRef)
            .passwordRef(encryptedPassword)
            .build();
    return getKubernetesCredentialDTO(kubernetesAuthCredentialDTO, masterUrl, KubernetesAuthType.USER_PASSWORD);
  }

  private KubernetesCredentialDTO getServiceAccountCredentials(
      String masterUrl, SecretRefData encryptedServiceAccountTokenRef, SecretRefData caCertRef) {
    KubernetesAuthCredentialDTO kubernetesAuthCredentialDTO =
        KubernetesServiceAccountDTO.builder()
            .serviceAccountTokenRef(encryptedServiceAccountTokenRef)
            .caCertRef(caCertRef)
            .build();
    return getKubernetesCredentialDTO(kubernetesAuthCredentialDTO, masterUrl, KubernetesAuthType.SERVICE_ACCOUNT);
  }

  private KubernetesCredentialDTO getOidcCredentials(String masterUrl, String oidcIdentityProviderUrl, String username,
      String scopes, SecretRefData usernameRef, SecretRefData passwordRef, SecretRefData oidcClientIdRef,
      SecretRefData oidcSecretRef) {
    KubernetesAuthCredentialDTO kubernetesAuthCredentialDTO = KubernetesOpenIdConnectDTO.builder()
                                                                  .oidcIssuerUrl(oidcIdentityProviderUrl)
                                                                  .oidcUsername(username)
                                                                  .oidcUsernameRef(usernameRef)
                                                                  .oidcClientIdRef(oidcClientIdRef)
                                                                  .oidcPasswordRef(passwordRef)
                                                                  .oidcSecretRef(oidcSecretRef)
                                                                  .oidcScopes(scopes)
                                                                  .build();
    return getKubernetesCredentialDTO(kubernetesAuthCredentialDTO, masterUrl, KubernetesAuthType.OPEN_ID_CONNECT);
  }

  private KubernetesCredentialDTO getClientKeyCertCredentials(String masterUrl, SecretRefData caCertRef,
      SecretRefData clientCertRef, SecretRefData clientKeyRef, SecretRefData clientKeyPassphraseRef,
      String clientKeyAlgo) {
    KubernetesAuthCredentialDTO kubernetesAuthCredentialDTO = KubernetesClientKeyCertDTO.builder()
                                                                  .caCertRef(caCertRef)
                                                                  .clientCertRef(clientCertRef)
                                                                  .clientKeyRef(clientKeyRef)
                                                                  .clientKeyPassphraseRef(clientKeyPassphraseRef)
                                                                  .clientKeyAlgo(clientKeyAlgo)
                                                                  .build();
    return getKubernetesCredentialDTO(kubernetesAuthCredentialDTO, masterUrl, KubernetesAuthType.CLIENT_KEY_CERT);
  }

  private KubernetesCredentialDTO getKubernetesCredentialDTO(KubernetesAuthCredentialDTO kubernetesAuthCredentialDTO,
      String masterUrl, KubernetesAuthType kubernetesAuthType) {
    return KubernetesCredentialDTO.builder()
        .kubernetesCredentialType(MANUAL_CREDENTIALS)
        .config(KubernetesClusterDetailsDTO.builder()
                    .masterUrl(masterUrl)
                    .auth(KubernetesAuthDTO.builder()
                              .authType(kubernetesAuthType)
                              .credentials(kubernetesAuthCredentialDTO)
                              .build())
                    .build())
        .build();
  }
}
