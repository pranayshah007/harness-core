/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType.SERVICE_ACCOUNT;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.MANUAL_CREDENTIALS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesServiceAccountDTO;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.common.collect.ImmutableList;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
public class K8sConnectorHelperTest extends CategoryTest {
  private static final String CONNECTOR_IDENTIFIER = "cid";
  private static final String ACCOUNT_IDENTIFIER = "aid";
  private static final String ORG_IDENTIFIER = "oid";
  private static final String PROJECT_IDENTIFIER = "pid";

  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private SecretManagerClientService ngSecretService;
  @InjectMocks private K8sConnectorHelper k8sConnectorHelper;

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void getEncryptionDetail_InClusterDelegate() throws Exception {
    KubernetesCredentialDTO k8sCredentials =
        KubernetesCredentialDTO.builder().config(null).kubernetesCredentialType(INHERIT_FROM_DELEGATE).build();

    assertThat(k8sConnectorHelper.getEncryptionDetail(createKubernetesClusterConfig(k8sCredentials), ACCOUNT_IDENTIFIER,
                   ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isNull();

    verifyNoInteractions(ngSecretService);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void getEncryptionDetail_ManualCredentials() throws Exception {
    KubernetesCredentialDTO k8sCredentials = KubernetesCredentialDTO.builder()
                                                 .kubernetesCredentialType(MANUAL_CREDENTIALS)
                                                 .config(KubernetesClusterDetailsDTO.builder()
                                                             .masterUrl("http://localhost")
                                                             .auth(createServiceAccountDTO())
                                                             .build())
                                                 .build();

    NGAccess basicNGAccessObject = BaseNGAccess.builder()
                                       .accountIdentifier(ACCOUNT_IDENTIFIER)
                                       .orgIdentifier(ORG_IDENTIFIER)
                                       .projectIdentifier(PROJECT_IDENTIFIER)
                                       .build();

    final EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().fieldName("fname").build();

    when(ngSecretService.getEncryptionDetails(eq(basicNGAccessObject), eq(createServiceAccountDTO().getCredentials())))
        .thenReturn(ImmutableList.of(encryptedDataDetail));

    assertThat(k8sConnectorHelper.getEncryptionDetail(createKubernetesClusterConfig(k8sCredentials), ACCOUNT_IDENTIFIER,
                   ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .containsExactlyInAnyOrder(encryptedDataDetail);

    verify(ngSecretService, times(1));
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void getConnectorConfig() throws Exception {
    Call call = mock(Call.class);

    ConnectorConfigDTO configDTO = KubernetesClusterConfigDTO.builder().build();

    ConnectorDTO connectorDTO =
        ConnectorDTO.builder().connectorInfo(ConnectorInfoDTO.builder().connectorConfig(configDTO).build()).build();

    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Optional.of(connectorDTO))));
    when(connectorResourceClient.get(
             eq(CONNECTOR_IDENTIFIER), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(call);

    assertThat(k8sConnectorHelper.getConnectorConfig(
                   CONNECTOR_IDENTIFIER, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isEqualTo(configDTO);
  }

  private static KubernetesClusterConfigDTO createKubernetesClusterConfig(KubernetesCredentialDTO k8sCredentials) {
    return KubernetesClusterConfigDTO.builder().credential(k8sCredentials).build();
  }

  private static KubernetesAuthDTO createServiceAccountDTO() {
    KubernetesServiceAccountDTO kubernetesServiceAccountDTO =
        KubernetesServiceAccountDTO.builder().serviceAccountTokenRef(new SecretRefData("sdhbjbvjr")).build();
    return KubernetesAuthDTO.builder().authType(SERVICE_ACCOUNT).credentials(kubernetesServiceAccountDTO).build();
  }
}
