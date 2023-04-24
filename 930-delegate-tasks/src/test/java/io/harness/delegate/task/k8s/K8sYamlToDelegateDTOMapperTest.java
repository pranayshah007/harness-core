/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType.CLIENT_KEY_CERT;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType.OPEN_ID_CONNECT;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType.SERVICE_ACCOUNT;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.MANUAL_CREDENTIALS;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClientKeyCertDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesDelegateDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesOpenIdConnectDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesServiceAccountDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesUserNamePasswordDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Collections;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sYamlToDelegateDTOMapperTest extends CategoryTest {
  @InjectMocks K8sYamlToDelegateDTOMapper k8sYamlToDelegateDTOMapper;
  private static final String defaultNamespace = "default";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void createKubernetesConfigFromClusterConfigTestForManualCredentials() {
    String userName = "userName";
    String password = "password";
    String masterUrl = "https://abc.com/";
    String passwordIdentifier = "passwordIdentifer";
    SecretRefData passwordSecretRefData = SecretRefData.builder()
                                              .identifier(passwordIdentifier)
                                              .scope(Scope.ACCOUNT)
                                              .decryptedValue(password.toCharArray())
                                              .build();
    KubernetesAuthDTO kubernetesAuthDTO =
        KubernetesAuthDTO.builder()
            .authType(KubernetesAuthType.USER_PASSWORD)
            .credentials(
                KubernetesUserNamePasswordDTO.builder().username(userName).passwordRef(passwordSecretRefData).build())
            .build();
    KubernetesClusterConfigDTO connectorDTOWithUserNamePassword =
        KubernetesClusterConfigDTO.builder()
            .credential(
                KubernetesCredentialDTO.builder()
                    .kubernetesCredentialType(MANUAL_CREDENTIALS)
                    .config(KubernetesClusterDetailsDTO.builder().masterUrl(masterUrl).auth(kubernetesAuthDTO).build())
                    .build())
            .build();
    KubernetesConfig config =
        k8sYamlToDelegateDTOMapper.createKubernetesConfigFromClusterConfig(connectorDTOWithUserNamePassword);
    assertThat(config).isNotNull();
    assertThat(config.getMasterUrl()).isEqualTo(masterUrl);
    assertThat(config.getUsername()).isEqualTo(userName.toCharArray());
    assertThat(config.getPassword()).isEqualTo(password.toCharArray());
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  @Parameters({"clientKeyPhraseIdentifier", ""})
  public void createKubernetesConfigFromClusterConfigWithNameSpaceForClientKeyCertTest() {
    String clientKey = "clientKey";
    String clientKeyIdentifier = "clientKeyRef";
    String clientCert = "cert";
    String clientCertIdentifier = "clientCertRef";
    String clientKeyPhrase = "clientKeyPhrase";
    String clientKeyPhraseIdentifier = "clientKeyPhraseIdentifier";
    String clientKeyAlgo = "clientKeyAlgo";
    String masterUrl = "https://abc.com/";
    String namespace = "namespace";
    String caCertIdentifier = "caCertIdentifier";
    String caCert = "caCert";
    SecretRefData caCertSecretRef = SecretRefData.builder()
                                        .identifier(caCertIdentifier)
                                        .scope(Scope.ACCOUNT)
                                        .decryptedValue(caCert.toCharArray())
                                        .build();
    SecretRefData clientKeySecret = SecretRefData.builder()
                                        .identifier(clientKeyIdentifier)
                                        .scope(Scope.ACCOUNT)
                                        .decryptedValue(clientKey.toCharArray())
                                        .build();
    SecretRefData clientCertSecret = SecretRefData.builder()
                                         .identifier(clientCertIdentifier)
                                         .scope(Scope.ACCOUNT)
                                         .decryptedValue(clientCert.toCharArray())
                                         .build();

    SecretRefData clientKeyPassPhraseSecret = null;
    if (isNotEmpty(clientKeyPhraseIdentifier)) {
      clientKeyPassPhraseSecret = SecretRefData.builder()
                                      .identifier(clientKeyPhraseIdentifier)
                                      .scope(Scope.ACCOUNT)
                                      .decryptedValue(clientKeyPhrase.toCharArray())
                                      .build();
    }
    KubernetesAuthDTO kubernetesAuthDTO = KubernetesAuthDTO.builder()
                                              .authType(CLIENT_KEY_CERT)
                                              .credentials(KubernetesClientKeyCertDTO.builder()
                                                               .caCertRef(caCertSecretRef)
                                                               .clientKeyRef(clientKeySecret)
                                                               .clientCertRef(clientCertSecret)
                                                               .clientKeyPassphraseRef(clientKeyPassPhraseSecret)
                                                               .clientKeyAlgo(clientKeyAlgo)
                                                               .build())
                                              .build();
    KubernetesClusterConfigDTO connectorDTOWithClientKeyCreds =
        KubernetesClusterConfigDTO.builder()
            .credential(
                KubernetesCredentialDTO.builder()
                    .kubernetesCredentialType(MANUAL_CREDENTIALS)
                    .config(KubernetesClusterDetailsDTO.builder().masterUrl(masterUrl).auth(kubernetesAuthDTO).build())
                    .build())
            .build();
    KubernetesConfig config =
        k8sYamlToDelegateDTOMapper.createKubernetesConfigFromClusterConfig(connectorDTOWithClientKeyCreds, namespace);
    assertThat(config).isNotNull();
    assertThat(config.getMasterUrl()).isEqualTo(masterUrl);
    assertThat(config.getClientKey()).isEqualTo(clientKey.toCharArray());
    assertThat(config.getClientCert()).isEqualTo(clientCert.toCharArray());
    if (isNotEmpty(clientKeyPhraseIdentifier)) {
      assertThat(config.getClientKeyPassphrase()).isEqualTo(clientKeyPhrase.toCharArray());
    }
    assertThat(config.getClientKeyAlgo()).isEqualTo(clientKeyAlgo);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void createKubernetesConfigFromClusterConfigForServiceAccountTest() {
    String serviceAccountKey = "serviceAccountKey";
    String masterUrl = "https://abc.com/";
    String serviceAccountRef = "serviceAccountRef";
    SecretRefData serviceAccountSecretRef = SecretRefData.builder()
                                                .identifier(serviceAccountRef)
                                                .scope(Scope.ACCOUNT)
                                                .decryptedValue(serviceAccountKey.toCharArray())
                                                .build();
    KubernetesAuthDTO kubernetesAuthDTO =
        KubernetesAuthDTO.builder()
            .authType(SERVICE_ACCOUNT)
            .credentials(KubernetesServiceAccountDTO.builder().serviceAccountTokenRef(serviceAccountSecretRef).build())
            .build();
    KubernetesClusterConfigDTO connectorDTOWithServiceAccountCreds =
        KubernetesClusterConfigDTO.builder()
            .credential(
                KubernetesCredentialDTO.builder()
                    .kubernetesCredentialType(MANUAL_CREDENTIALS)
                    .config(KubernetesClusterDetailsDTO.builder().masterUrl(masterUrl).auth(kubernetesAuthDTO).build())
                    .build())
            .build();
    KubernetesConfig config =
        k8sYamlToDelegateDTOMapper.createKubernetesConfigFromClusterConfig(connectorDTOWithServiceAccountCreds, null);
    assertThat(config).isNotNull();
    assertThat(config.getMasterUrl()).isEqualTo(masterUrl);
    assertThat(config.getServiceAccountTokenSupplier().get()).isEqualTo(serviceAccountKey);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void createKubernetesConfigFromClusterConfigForServiceAccountWithCaCertTest() {
    String serviceAccountKey = "serviceAccountKey";
    String masterUrl = "https://abc.com/";
    String serviceAccountRef = "serviceAccountRef";
    String caCertRef = "caCertRef";
    String caCertKey = "caCertKey";
    SecretRefData serviceAccountSecretRef = SecretRefData.builder()
                                                .identifier(serviceAccountRef)
                                                .scope(Scope.ACCOUNT)
                                                .decryptedValue(serviceAccountKey.toCharArray())
                                                .build();
    SecretRefData caCertSecretRef = SecretRefData.builder()
                                        .identifier(caCertRef)
                                        .scope(Scope.ACCOUNT)
                                        .decryptedValue(caCertKey.toCharArray())
                                        .build();

    KubernetesAuthDTO kubernetesAuthDTO = KubernetesAuthDTO.builder()
                                              .authType(SERVICE_ACCOUNT)
                                              .credentials(KubernetesServiceAccountDTO.builder()
                                                               .serviceAccountTokenRef(serviceAccountSecretRef)
                                                               .caCertRef(caCertSecretRef)
                                                               .build())
                                              .build();
    KubernetesClusterConfigDTO connectorDTOWithServiceAccountCreds =
        KubernetesClusterConfigDTO.builder()
            .credential(
                KubernetesCredentialDTO.builder()
                    .kubernetesCredentialType(MANUAL_CREDENTIALS)
                    .config(KubernetesClusterDetailsDTO.builder().masterUrl(masterUrl).auth(kubernetesAuthDTO).build())
                    .build())
            .build();
    KubernetesConfig config =
        k8sYamlToDelegateDTOMapper.createKubernetesConfigFromClusterConfig(connectorDTOWithServiceAccountCreds, null);
    assertThat(config).isNotNull();
    assertThat(config.getMasterUrl()).isEqualTo(masterUrl);
    assertThat(config.getServiceAccountTokenSupplier().get()).isEqualTo(serviceAccountKey);
    assertThat(config.getCaCert()).isEqualTo(caCertKey.toCharArray());
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void createKubernetesConfigFromClusterConfigForOiDCTokenCreds() {
    String oidClientIdIdentifier = "oidcClientIdRef";
    String oidcIssuerUrl = "oidcIssuerUrl";
    String oidcPasswordIdentifier = "oidcPasswordRef";
    String oidcScopes = "oidcScopes";
    String oidcSecretIdentifier = "oidcSecretRef";
    String oidcUsername = "oidcUsername";
    String masterUrl = "https://abc.com";
    String oidcCleintId = "oidcClientId";
    String oidcPassword = "oidcPassword";
    String oidcSecret = "oidcSecret";
    SecretRefData oidcCleintIdRef = SecretRefData.builder()
                                        .identifier(oidClientIdIdentifier)
                                        .scope(Scope.ACCOUNT)
                                        .decryptedValue(oidcCleintId.toCharArray())
                                        .build();
    SecretRefData oidcPasswordSecretRe = SecretRefData.builder()
                                             .identifier(oidcPasswordIdentifier)
                                             .scope(Scope.ACCOUNT)
                                             .decryptedValue(oidcPassword.toCharArray())
                                             .build();
    SecretRefData oidcSecretRef = SecretRefData.builder()
                                      .identifier(oidcSecretIdentifier)
                                      .scope(Scope.ACCOUNT)
                                      .decryptedValue(oidcSecret.toCharArray())
                                      .build();
    KubernetesAuthDTO kubernetesAuthDTO = KubernetesAuthDTO.builder()
                                              .authType(OPEN_ID_CONNECT)
                                              .credentials(KubernetesOpenIdConnectDTO.builder()
                                                               .oidcClientIdRef(oidcCleintIdRef)
                                                               .oidcIssuerUrl(oidcIssuerUrl)
                                                               .oidcPasswordRef(oidcPasswordSecretRe)
                                                               .oidcScopes(oidcScopes)
                                                               .oidcSecretRef(oidcSecretRef)
                                                               .oidcUsername(oidcUsername)
                                                               .build())
                                              .build();
    KubernetesClusterConfigDTO connectorDTOWithOpenIdConnectCred =
        KubernetesClusterConfigDTO.builder()
            .credential(
                KubernetesCredentialDTO.builder()
                    .kubernetesCredentialType(MANUAL_CREDENTIALS)
                    .config(KubernetesClusterDetailsDTO.builder().masterUrl(masterUrl).auth(kubernetesAuthDTO).build())
                    .build())
            .build();
    KubernetesConfig config =
        k8sYamlToDelegateDTOMapper.createKubernetesConfigFromClusterConfig(connectorDTOWithOpenIdConnectCred, null);
    assertThat(config).isNotNull();
    assertThat(config.getMasterUrl()).isEqualTo(masterUrl);
    assertThat(config.getOidcClientId()).isEqualTo(oidcCleintId.toCharArray());
    assertThat(config.getOidcIdentityProviderUrl()).isEqualTo(oidcIssuerUrl);
    assertThat(config.getOidcPassword()).isEqualTo(oidcPassword.toCharArray());
    assertThat(config.getOidcScopes()).isEqualTo(oidcScopes);
    assertThat(config.getOidcSecret()).isEqualTo(oidcSecret.toCharArray());
    assertThat(config.getOidcUsername()).isEqualTo(oidcUsername);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void createKubernetesConfigFromClusterConfigForOIDCTokenNullSecret() {
    String oidClientIdIdentifier = "oidcClientIdRef";
    String oidcIssuerUrl = "oidcIssuerUrl";
    String oidcPasswordIdentifier = "oidcPasswordRef";
    String oidcScopes = "oidcScopes";
    String oidcSecretIdentifier = "oidcSecretRef";
    String oidcUsername = "oidcUsername";
    String masterUrl = "https://abc.com";
    String oidcCleintId = "oidcClientId";
    String oidcPassword = "oidcPassword";

    SecretRefData oidcCleintIdRef = SecretRefData.builder()
                                        .identifier(oidClientIdIdentifier)
                                        .scope(Scope.ACCOUNT)
                                        .decryptedValue(oidcCleintId.toCharArray())
                                        .build();
    SecretRefData oidcPasswordSecretRe = SecretRefData.builder()
                                             .identifier(oidcPasswordIdentifier)
                                             .scope(Scope.ACCOUNT)
                                             .decryptedValue(oidcPassword.toCharArray())
                                             .build();

    KubernetesAuthDTO kubernetesAuthDTO = KubernetesAuthDTO.builder()
                                              .authType(OPEN_ID_CONNECT)
                                              .credentials(KubernetesOpenIdConnectDTO.builder()
                                                               .oidcClientIdRef(oidcCleintIdRef)
                                                               .oidcIssuerUrl(oidcIssuerUrl)
                                                               .oidcPasswordRef(oidcPasswordSecretRe)
                                                               .oidcScopes(oidcScopes)
                                                               .oidcUsername(oidcUsername)
                                                               .build())
                                              .build();

    KubernetesClusterConfigDTO connectorDTOWithOpenIdConnectCred =
        KubernetesClusterConfigDTO.builder()
            .credential(
                KubernetesCredentialDTO.builder()
                    .kubernetesCredentialType(MANUAL_CREDENTIALS)
                    .config(KubernetesClusterDetailsDTO.builder().masterUrl(masterUrl).auth(kubernetesAuthDTO).build())
                    .build())
            .build();

    KubernetesConfig config =
        k8sYamlToDelegateDTOMapper.createKubernetesConfigFromClusterConfig(connectorDTOWithOpenIdConnectCred, null);

    assertThat(config.getOidcClientId()).isEqualTo(oidcCleintId.toCharArray());
    assertThat(config.getOidcIdentityProviderUrl()).isEqualTo(oidcIssuerUrl);
    assertThat(config.getOidcPassword()).isEqualTo(oidcPassword.toCharArray());
    assertThat(config.getOidcScopes()).isEqualTo(oidcScopes);
    assertThat(config.getOidcSecret()).isNull();
    assertThat(config.getOidcUsername()).isEqualTo(oidcUsername);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void createKubernetesConfigFromClusterConfigForInheritFromDelegateCreds() {
    String delegateName = "testDeleagete";
    KubernetesClusterConfigDTO connectorDTOWithDelegateCreds =
        KubernetesClusterConfigDTO.builder()
            .delegateSelectors(Collections.singleton(delegateName))
            .credential(KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(INHERIT_FROM_DELEGATE)
                            .config(KubernetesDelegateDetailsDTO.builder().build())
                            .build())
            .build();
    KubernetesConfig config =
        k8sYamlToDelegateDTOMapper.createKubernetesConfigFromClusterConfig(connectorDTOWithDelegateCreds, null);
    assertThat(config).isNotNull();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void createKubernetesConfigFromClusterConfigForInheritFromDelegateRunningInCluster() {
    final String serviceHost = "svc.cluster.local";
    final String servicePort = "443";
    final KubernetesClusterConfigDTO connectorDTOWithDelegateCreds =
        KubernetesClusterConfigDTO.builder()
            .delegateSelectors(Collections.singleton("delegate"))
            .credential(KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(INHERIT_FROM_DELEGATE)
                            .config(KubernetesDelegateDetailsDTO.builder().build())
                            .build())
            .build();
    String kubernetesServiceHostTemp = System.getProperty("KUBERNETES_SERVICE_HOST");
    String kubernetesServicePortTemp = System.getProperty("KUBERNETES_SERVICE_PORT");
    KubernetesConfig config;
    try {
      System.setProperty("KUBERNETES_SERVICE_HOST", serviceHost);
      System.setProperty("KUBERNETES_SERVICE_PORT", servicePort);

      config =
          k8sYamlToDelegateDTOMapper.createKubernetesConfigFromClusterConfig(connectorDTOWithDelegateCreds, "service");
    } finally {
      if (kubernetesServiceHostTemp != null) {
        System.setProperty("KUBERNETES_SERVICE_HOST", kubernetesServiceHostTemp);
      } else {
        System.getProperties().remove("KUBERNETES_SERVICE_HOST");
      }

      if (kubernetesServicePortTemp != null) {
        System.setProperty("KUBERNETES_SERVICE_PORT", kubernetesServicePortTemp);
      } else {
        System.getProperties().remove("KUBERNETES_SERVICE_PORT");
      }
    }

    assertThat(config.getMasterUrl()).isEqualTo("https://svc.cluster.local:443");
  }
}
