/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.kubernetesMapper;

import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.MANUAL_CREDENTIALS;
import static io.harness.encryption.Scope.ACCOUNT;
import static io.harness.encryption.SecretRefData.SECRET_DELIMINITER;
import static io.harness.encryption.SecretRefData.SECRET_DOT_DELIMINITER;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.embedded.kubernetescluster.K8sClientKeyCert;
import io.harness.connector.entities.embedded.kubernetescluster.K8sOpenIdConnect;
import io.harness.connector.entities.embedded.kubernetescluster.K8sServiceAccount;
import io.harness.connector.entities.embedded.kubernetescluster.K8sUserNamePassword;
import io.harness.connector.entities.embedded.kubernetescluster.KubernetesClusterConfig;
import io.harness.connector.entities.embedded.kubernetescluster.KubernetesClusterDetails;
import io.harness.connector.entities.embedded.kubernetescluster.KubernetesDelegateDetails;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClientKeyCertDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesOpenIdConnectDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesServiceAccountDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesUserNamePasswordDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.UnexpectedException;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class KubernetesEntityToDTOTest extends CategoryTest {
  @InjectMocks KubernetesEntityToDTO kubernetesEntityToDTO;
  @Mock KubernetesConfigCastHelper kubernetesConfigCastHelper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(kubernetesConfigCastHelper.castToKubernetesDelegateCredential(any())).thenCallRealMethod();
    when(kubernetesConfigCastHelper.castToManualKubernetesCredentials(any())).thenCallRealMethod();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testCreateK8ClusterConfigDTOForDelegateCredentials() {
    String delegateName = "testDeleagete";
    KubernetesDelegateDetails delegateCredential =
        KubernetesDelegateDetails.builder().delegateSelectors(Collections.singleton(delegateName)).build();
    Connector connector =
        KubernetesClusterConfig.builder().credentialType(INHERIT_FROM_DELEGATE).credential(delegateCredential).build();
    KubernetesClusterConfigDTO connectorDTO =
        kubernetesEntityToDTO.createConnectorDTO((KubernetesClusterConfig) connector);
    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getDelegateSelectors()).isEqualTo(null);
    assertThat(connectorDTO.getCredential().getKubernetesCredentialType()).isEqualTo(INHERIT_FROM_DELEGATE);
    assertThat(connectorDTO.getCredential().getConfig()).isEqualTo(null);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testCreateK8ClusterConfigDTOForDelegateCredentialsWithWrongType() {
    String delegateName = "testDeleagete";
    KubernetesDelegateDetails delegateCredential =
        KubernetesDelegateDetails.builder().delegateSelectors(Collections.singleton(delegateName)).build();
    Connector connector =
        KubernetesClusterConfig.builder().credentialType(MANUAL_CREDENTIALS).credential(delegateCredential).build();
    KubernetesClusterConfigDTO connectorDTO =
        kubernetesEntityToDTO.createConnectorDTO((KubernetesClusterConfig) connector);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testCreateK8ClusterConfigDTOForManualCredentialsWithWrongType() {
    String masterURL = "masterURL";
    String userName = "userName";
    String passwordIdentifier = "passwordIdentifier";
    String passwordRef = "account" + SECRET_DELIMINITER + passwordIdentifier;
    K8sUserNamePassword k8sUserNamePassword =
        K8sUserNamePassword.builder().userName(userName).passwordRef(passwordRef).build();
    KubernetesClusterDetails kubernetesClusterDetails = KubernetesClusterDetails.builder()
                                                            .masterUrl(masterURL)
                                                            .authType(KubernetesAuthType.USER_PASSWORD)
                                                            .auth(k8sUserNamePassword)
                                                            .build();
    Connector connector = KubernetesClusterConfig.builder()
                              .credentialType(INHERIT_FROM_DELEGATE)
                              .credential(kubernetesClusterDetails)
                              .build();
    KubernetesClusterConfigDTO connectorDTO =
        kubernetesEntityToDTO.createConnectorDTO((KubernetesClusterConfig) connector);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testCreateK8ClusterConfigDTOForUserNamePassword() {
    String masterURL = "masterURL";
    String userName = "userName";
    String passwordIdentifier = "passwordIdentifier";
    String passwordRef = ACCOUNT + SECRET_DOT_DELIMINITER + passwordIdentifier;
    K8sUserNamePassword k8sUserNamePassword =
        K8sUserNamePassword.builder().userName(userName).passwordRef(passwordRef).build();
    KubernetesClusterDetails kubernetesClusterDetails = KubernetesClusterDetails.builder()
                                                            .masterUrl(masterURL)
                                                            .authType(KubernetesAuthType.USER_PASSWORD)
                                                            .auth(k8sUserNamePassword)
                                                            .build();
    Connector connector = KubernetesClusterConfig.builder()
                              .credentialType(MANUAL_CREDENTIALS)
                              .credential(kubernetesClusterDetails)
                              .build();
    KubernetesClusterConfigDTO connectorDTO =
        kubernetesEntityToDTO.createConnectorDTO((KubernetesClusterConfig) connector);
    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getCredential().getConfig()).isNotNull();
    assertThat(connectorDTO.getCredential().getKubernetesCredentialType()).isEqualTo(MANUAL_CREDENTIALS);
    KubernetesClusterDetailsDTO credentialDTO = (KubernetesClusterDetailsDTO) connectorDTO.getCredential().getConfig();
    assertThat(credentialDTO).isNotNull();
    assertThat(credentialDTO.getMasterUrl()).isNotNull();
    KubernetesUserNamePasswordDTO kubernetesUserNamePasswordDTO =
        (KubernetesUserNamePasswordDTO) credentialDTO.getAuth().getCredentials();
    assertThat(kubernetesUserNamePasswordDTO.getUsername()).isEqualTo(userName);
    assertThat(kubernetesUserNamePasswordDTO.getPasswordRef())
        .isEqualTo(SecretRefData.builder().identifier(passwordIdentifier).scope(ACCOUNT).build());
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testCreateK8ClusterConfigDTOForClientKeyCert() {
    String clientKeyIdentifer = "clientKeyRef";
    String clientCertIdentifer = "clientCertRef";
    String clientKeyPhraseIdenfiter = "clientKeyPhrase";
    String clientKeyAlgo = "clientKeyAlgo";
    String masterUrl = "https://abc.com";
    String clientKeyRef = ACCOUNT.getYamlRepresentation() + SECRET_DOT_DELIMINITER + clientKeyIdentifer;
    String clientCertRef = ACCOUNT.getYamlRepresentation() + SECRET_DOT_DELIMINITER + clientCertIdentifer;
    String clientKeyPassPhraseRef = ACCOUNT.getYamlRepresentation() + SECRET_DOT_DELIMINITER + clientKeyPhraseIdenfiter;
    String cacertIdentifier = "cacertIdentifier";
    String caCertRef = ACCOUNT.getYamlRepresentation() + SECRET_DOT_DELIMINITER + cacertIdentifier;
    SecretRefData secretRefDataCACert = SecretRefData.builder().identifier(cacertIdentifier).scope(ACCOUNT).build();
    SecretRefData clientKeySecret = SecretRefData.builder().identifier(clientKeyIdentifer).scope(ACCOUNT).build();
    SecretRefData clientCertSecret = SecretRefData.builder().identifier(clientCertIdentifer).scope(ACCOUNT).build();
    SecretRefData clientKeyPassPhraseSecret =
        SecretRefData.builder().identifier(clientKeyPhraseIdenfiter).scope(ACCOUNT).build();
    SecretRefData caCertSecretRef = SecretRefData.builder().identifier(caCertRef).scope(ACCOUNT).build();
    K8sClientKeyCert k8sClientKeyCert = K8sClientKeyCert.builder()
                                            .caCertRef(caCertRef)
                                            .clientKeyRef(clientKeyRef)
                                            .clientCertRef(clientCertRef)
                                            .clientKeyPassphraseRef(clientKeyPassPhraseRef)
                                            .clientKeyAlgo(clientKeyAlgo)
                                            .build();
    KubernetesClusterDetails kubernetesClusterDetails = KubernetesClusterDetails.builder()
                                                            .masterUrl(masterUrl)
                                                            .authType(KubernetesAuthType.CLIENT_KEY_CERT)
                                                            .auth(k8sClientKeyCert)
                                                            .build();
    Connector connector = KubernetesClusterConfig.builder()
                              .credentialType(MANUAL_CREDENTIALS)
                              .credential(kubernetesClusterDetails)
                              .build();
    KubernetesClusterConfigDTO connectorDTO =
        kubernetesEntityToDTO.createConnectorDTO((KubernetesClusterConfig) connector);
    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getCredential().getConfig()).isNotNull();
    assertThat(connectorDTO.getCredential().getKubernetesCredentialType()).isEqualTo(MANUAL_CREDENTIALS);
    KubernetesClusterDetailsDTO credentialDTO = (KubernetesClusterDetailsDTO) connectorDTO.getCredential().getConfig();
    assertThat(credentialDTO).isNotNull();
    assertThat(credentialDTO.getMasterUrl()).isNotNull();
    KubernetesClientKeyCertDTO kubernetesClientKeyCertDTO =
        (KubernetesClientKeyCertDTO) credentialDTO.getAuth().getCredentials();
    assertThat(kubernetesClientKeyCertDTO.getClientKeyRef()).isEqualTo(clientKeySecret);
    assertThat(kubernetesClientKeyCertDTO.getClientCertRef()).isEqualTo(clientCertSecret);
    assertThat(kubernetesClientKeyCertDTO.getClientKeyPassphraseRef()).isEqualTo(clientKeyPassPhraseSecret);
    assertThat(kubernetesClientKeyCertDTO.getClientKeyAlgo()).isEqualTo(clientKeyAlgo);
    assertThat(kubernetesClientKeyCertDTO.getCaCertRef()).isEqualTo(secretRefDataCACert);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testCreateK8ClusterConfigDTOForOIDCConnect() {
    String oidClientIdIdentifer = "oidcClientIdRef";
    String oidcIssuerUrl = "oidcIssuerUrl";
    String oidcPasswordIdentifier = "oidcPasswordRef";
    String oidcScopes = "oidcScopes";
    String oidcSecretIdentifer = "oidcSecretRef";
    String oidcUsername = "oidcUsername";
    String masterUrl = "https://abc.com";
    String oidcClientIdRef = ACCOUNT.getYamlRepresentation() + SECRET_DOT_DELIMINITER + oidClientIdIdentifer;
    String oidcPassordRef = ACCOUNT.getYamlRepresentation() + SECRET_DOT_DELIMINITER + oidcPasswordIdentifier;
    String oidcSecretRef = ACCOUNT.getYamlRepresentation() + SECRET_DOT_DELIMINITER + oidcSecretIdentifer;
    SecretRefData oidcClientId = SecretRefData.builder().identifier(oidClientIdIdentifer).scope(ACCOUNT).build();
    SecretRefData oidcPassword = SecretRefData.builder().identifier(oidcPasswordIdentifier).scope(ACCOUNT).build();
    SecretRefData oidcSecret = SecretRefData.builder().identifier(oidcSecretIdentifer).scope(ACCOUNT).build();
    K8sOpenIdConnect k8sOpenIdConnect = K8sOpenIdConnect.builder()
                                            .oidcClientIdRef(oidcClientIdRef)
                                            .oidcIssuerUrl(oidcIssuerUrl)
                                            .oidcPasswordRef(oidcPassordRef)
                                            .oidcScopes(oidcScopes)
                                            .oidcSecretRef(oidcSecretRef)
                                            .oidcUsername(oidcUsername)
                                            .build();
    KubernetesClusterDetails kubernetesClusterDetails = KubernetesClusterDetails.builder()
                                                            .masterUrl(masterUrl)
                                                            .authType(KubernetesAuthType.OPEN_ID_CONNECT)
                                                            .auth(k8sOpenIdConnect)
                                                            .build();
    Connector connector = KubernetesClusterConfig.builder()
                              .credentialType(MANUAL_CREDENTIALS)
                              .credential(kubernetesClusterDetails)
                              .build();
    KubernetesClusterConfigDTO connectorDTO =
        kubernetesEntityToDTO.createConnectorDTO((KubernetesClusterConfig) connector);
    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getCredential().getConfig()).isNotNull();
    assertThat(connectorDTO.getCredential().getKubernetesCredentialType()).isEqualTo(MANUAL_CREDENTIALS);
    KubernetesClusterDetailsDTO credentialDTO = (KubernetesClusterDetailsDTO) connectorDTO.getCredential().getConfig();
    assertThat(credentialDTO).isNotNull();
    assertThat(credentialDTO.getMasterUrl()).isNotNull();
    KubernetesOpenIdConnectDTO kubernetesOpenIdConnectDTO =
        (KubernetesOpenIdConnectDTO) credentialDTO.getAuth().getCredentials();
    assertThat(kubernetesOpenIdConnectDTO.getOidcClientIdRef()).isEqualTo(oidcClientId);
    assertThat(kubernetesOpenIdConnectDTO.getOidcIssuerUrl()).isEqualTo(oidcIssuerUrl);
    assertThat(kubernetesOpenIdConnectDTO.getOidcPasswordRef()).isEqualTo(oidcPassword);
    assertThat(kubernetesOpenIdConnectDTO.getOidcScopes()).isEqualTo(oidcScopes);
    assertThat(kubernetesOpenIdConnectDTO.getOidcSecretRef()).isEqualTo(oidcSecret);
    assertThat(kubernetesOpenIdConnectDTO.getOidcUsername()).isEqualTo(oidcUsername);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testCreateK8ClusterConfigDTOForServiceAccount() {
    String masterURL = "masterURL";
    String serviceAccountKeyIdentifier = "serviceAccountKeyIdentifier";
    String serviceAccountRef = ACCOUNT.getYamlRepresentation() + SECRET_DOT_DELIMINITER + serviceAccountKeyIdentifier;
    SecretRefData serviceAccountsecret =
        SecretRefData.builder().identifier(serviceAccountKeyIdentifier).scope(ACCOUNT).build();
    K8sServiceAccount k8sServiceAccount =
        K8sServiceAccount.builder().serviceAcccountTokenRef(serviceAccountRef).build();
    KubernetesClusterDetails kubernetesClusterDetails = KubernetesClusterDetails.builder()
                                                            .masterUrl(masterURL)
                                                            .authType(KubernetesAuthType.SERVICE_ACCOUNT)
                                                            .auth(k8sServiceAccount)
                                                            .build();
    Connector connector = KubernetesClusterConfig.builder()
                              .credentialType(MANUAL_CREDENTIALS)
                              .credential(kubernetesClusterDetails)
                              .build();
    KubernetesClusterConfigDTO connectorDTO =
        kubernetesEntityToDTO.createConnectorDTO((KubernetesClusterConfig) connector);
    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getCredential().getConfig()).isNotNull();
    assertThat(connectorDTO.getCredential().getKubernetesCredentialType()).isEqualTo(MANUAL_CREDENTIALS);
    KubernetesClusterDetailsDTO credentialDTO = (KubernetesClusterDetailsDTO) connectorDTO.getCredential().getConfig();
    assertThat(credentialDTO).isNotNull();
    assertThat(credentialDTO.getMasterUrl()).isNotNull();
    KubernetesServiceAccountDTO kubernetesUserNamePasswordDTO =
        (KubernetesServiceAccountDTO) credentialDTO.getAuth().getCredentials();
    assertThat(kubernetesUserNamePasswordDTO.getServiceAccountTokenRef()).isEqualTo(serviceAccountsecret);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateK8ClusterConfigDTOForServiceAccountCaCertRef() {
    String masterURL = "masterURL";
    String serviceAccountKeyIdentifier = "serviceAccountKeyIdentifier";
    String caCertKeyIdentifier = "caCertKeyIdentifier";
    String serviceAccountRef = ACCOUNT.getYamlRepresentation() + SECRET_DOT_DELIMINITER + serviceAccountKeyIdentifier;
    String caCertAccountRef = ACCOUNT.getYamlRepresentation() + SECRET_DOT_DELIMINITER + caCertKeyIdentifier;
    SecretRefData serviceAccountsecret =
        SecretRefData.builder().identifier(serviceAccountKeyIdentifier).scope(ACCOUNT).build();
    SecretRefData caCertSecret = SecretRefData.builder().identifier(caCertKeyIdentifier).scope(ACCOUNT).build();
    K8sServiceAccount k8sServiceAccount =
        K8sServiceAccount.builder().serviceAcccountTokenRef(serviceAccountRef).caCertRef(caCertAccountRef).build();
    KubernetesClusterDetails kubernetesClusterDetails = KubernetesClusterDetails.builder()
                                                            .masterUrl(masterURL)
                                                            .authType(KubernetesAuthType.SERVICE_ACCOUNT)
                                                            .auth(k8sServiceAccount)
                                                            .build();
    Connector connector = KubernetesClusterConfig.builder()
                              .credentialType(MANUAL_CREDENTIALS)
                              .credential(kubernetesClusterDetails)
                              .build();
    KubernetesClusterConfigDTO connectorDTO =
        kubernetesEntityToDTO.createConnectorDTO((KubernetesClusterConfig) connector);
    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getCredential().getConfig()).isNotNull();
    assertThat(connectorDTO.getCredential().getKubernetesCredentialType()).isEqualTo(MANUAL_CREDENTIALS);
    KubernetesClusterDetailsDTO credentialDTO = (KubernetesClusterDetailsDTO) connectorDTO.getCredential().getConfig();
    assertThat(credentialDTO).isNotNull();
    assertThat(credentialDTO.getMasterUrl()).isNotNull();
    KubernetesServiceAccountDTO kubernetesUserNamePasswordDTO =
        (KubernetesServiceAccountDTO) credentialDTO.getAuth().getCredentials();
    assertThat(kubernetesUserNamePasswordDTO.getServiceAccountTokenRef()).isEqualTo(serviceAccountsecret);
    assertThat(kubernetesUserNamePasswordDTO.getCaCertRef()).isEqualTo(caCertSecret);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testCreateK8ClusterConfigDTOForUserNamePasswordWithWrongType() {
    KubernetesClusterDetails kubernetesClusterDetails = KubernetesClusterDetails.builder()
                                                            .authType(KubernetesAuthType.SERVICE_ACCOUNT)
                                                            .auth(K8sUserNamePassword.builder().build())
                                                            .build();
    Connector connector = KubernetesClusterConfig.builder()
                              .credentialType(MANUAL_CREDENTIALS)
                              .credential(kubernetesClusterDetails)
                              .build();
    KubernetesClusterConfigDTO connectorDTO =
        kubernetesEntityToDTO.createConnectorDTO((KubernetesClusterConfig) connector);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testCreateK8ClusterConfigDTOForClientKeyCertForWrongType() {
    KubernetesClusterDetails kubernetesClusterDetails = KubernetesClusterDetails.builder()
                                                            .authType(KubernetesAuthType.SERVICE_ACCOUNT)
                                                            .auth(K8sClientKeyCert.builder().build())
                                                            .build();
    Connector connector = KubernetesClusterConfig.builder()
                              .credentialType(MANUAL_CREDENTIALS)
                              .credential(kubernetesClusterDetails)
                              .build();
    KubernetesClusterConfigDTO connectorDTO =
        kubernetesEntityToDTO.createConnectorDTO((KubernetesClusterConfig) connector);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testCreateK8ClusterConfigDTOForServiceAccountForWrongType() {
    KubernetesClusterDetails kubernetesClusterDetails = KubernetesClusterDetails.builder()
                                                            .authType(KubernetesAuthType.USER_PASSWORD)
                                                            .auth(K8sServiceAccount.builder().build())
                                                            .build();
    Connector connector = KubernetesClusterConfig.builder()
                              .credentialType(MANUAL_CREDENTIALS)
                              .credential(kubernetesClusterDetails)
                              .build();
    KubernetesClusterConfigDTO connectorDTO =
        kubernetesEntityToDTO.createConnectorDTO((KubernetesClusterConfig) connector);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testCreateK8ClusterConfigDTOForOIDCConnectForWrongType() {
    KubernetesClusterDetails kubernetesClusterDetails = KubernetesClusterDetails.builder()
                                                            .authType(KubernetesAuthType.USER_PASSWORD)
                                                            .auth(K8sOpenIdConnect.builder().build())
                                                            .build();
    Connector connector = KubernetesClusterConfig.builder()
                              .credentialType(MANUAL_CREDENTIALS)
                              .credential(kubernetesClusterDetails)
                              .build();
    KubernetesClusterConfigDTO connectorDTO =
        kubernetesEntityToDTO.createConnectorDTO((KubernetesClusterConfig) connector);
  }
}
