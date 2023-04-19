/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.resources.gcp.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.JELENA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.gcp.GcpHelperService;
import io.harness.cdng.k8s.resources.gcp.GcpResponseDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpDelegateDetailsDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.gcp.GcpTaskType;
import io.harness.delegate.task.gcp.request.GcpListClustersRequest;
import io.harness.delegate.task.gcp.request.GcpRequest;
import io.harness.delegate.task.gcp.response.GcpClusterListTaskResponse;
import io.harness.encryption.SecretRefData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class GcpResourceServiceImplTest extends CategoryTest {
  private static String ACCOUNT_ID = "accountId";
  private static String ORG_IDENTIFIER = "orgIdentifier";
  private static String PROJECT_IDENTIFIER = "projectIdentifier";

  @Mock private GcpHelperService gcpHelperService;

  @InjectMocks GcpResourceServiceImpl gcpResourceService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldGetClusterNamesSuccess() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();
    GcpConnectorDTO gcpConnectorDTO = getConnector();
    when(gcpHelperService.getConnector(identifierRef)).thenReturn(gcpConnectorDTO);
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    when(gcpHelperService.getEncryptionDetails(eq(gcpConnectorDTO), any(NGAccess.class)))
        .thenReturn(Arrays.asList(encryptedDataDetail));
    when(gcpHelperService.executeSyncTask(
             any(BaseNGAccess.class), any(GcpRequest.class), eq(GcpTaskType.LIST_CLUSTERS), eq("list GCP clusters")))
        .thenReturn(GcpClusterListTaskResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .clusterNames(Arrays.asList("cluster1", "cluster2"))
                        .build());

    GcpResponseDTO responseDTO =
        gcpResourceService.getClusterNames(identifierRef, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getClusterNames()).containsExactly("cluster1", "cluster2");

    ArgumentCaptor<BaseNGAccess> ngAccessCaptor = ArgumentCaptor.forClass(BaseNGAccess.class);
    ArgumentCaptor<GcpListClustersRequest> requestCaptor = ArgumentCaptor.forClass(GcpListClustersRequest.class);
    verify(gcpHelperService, times(1))
        .executeSyncTask(
            ngAccessCaptor.capture(), requestCaptor.capture(), eq(GcpTaskType.LIST_CLUSTERS), eq("list GCP clusters"));
    BaseNGAccess ngAccess = ngAccessCaptor.getValue();
    GcpListClustersRequest request = requestCaptor.getValue();

    assertThat(ngAccess.getAccountIdentifier()).isEqualTo("accountId");
    assertThat(ngAccess.getOrgIdentifier()).isEqualTo("orgIdentifier");
    assertThat(ngAccess.getProjectIdentifier()).isEqualTo("projectIdentifier");
    assertThat(request.isUseDelegate()).isFalse();
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void shouldGetClusterNamesWhenInheritingCredentials() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();
    String delegateSelector = "test-delegate";
    GcpConnectorDTO gcpConnectorDTO =
        GcpConnectorDTO.builder()
            .credential(GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE)
                            .config(GcpDelegateDetailsDTO.builder()
                                        .delegateSelectors(Collections.singleton(delegateSelector))
                                        .build())
                            .build())
            .build();
    when(gcpHelperService.getConnector(identifierRef)).thenReturn(gcpConnectorDTO);

    when(gcpHelperService.executeSyncTask(
             any(BaseNGAccess.class), any(GcpRequest.class), eq(GcpTaskType.LIST_CLUSTERS), eq("list GCP clusters")))
        .thenReturn(GcpClusterListTaskResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .clusterNames(Arrays.asList("cluster1", "cluster2"))
                        .build());

    GcpResponseDTO responseDTO =
        gcpResourceService.getClusterNames(identifierRef, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getClusterNames()).containsExactly("cluster1", "cluster2");

    ArgumentCaptor<BaseNGAccess> ngAccessCaptor = ArgumentCaptor.forClass(BaseNGAccess.class);
    ArgumentCaptor<GcpListClustersRequest> requestCaptor = ArgumentCaptor.forClass(GcpListClustersRequest.class);
    verify(gcpHelperService, times(1))
        .executeSyncTask(
            ngAccessCaptor.capture(), requestCaptor.capture(), eq(GcpTaskType.LIST_CLUSTERS), eq("list GCP clusters"));
    BaseNGAccess ngAccess = ngAccessCaptor.getValue();
    GcpListClustersRequest request = requestCaptor.getValue();

    assertThat(ngAccess.getAccountIdentifier()).isEqualTo("accountId");
    assertThat(ngAccess.getOrgIdentifier()).isEqualTo("orgIdentifier");
    assertThat(ngAccess.getProjectIdentifier()).isEqualTo("projectIdentifier");
    assertThat(request.isUseDelegate()).isTrue();
  }

  private GcpConnectorDTO getConnector() {
    return GcpConnectorDTO.builder()
        .credential(
            GcpConnectorCredentialDTO.builder()
                .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                .config(GcpManualDetailsDTO.builder()
                            .secretKeyRef(SecretRefData.builder().decryptedValue("secret".toCharArray()).build())
                            .build())
                .build())
        .build();
  }
}
