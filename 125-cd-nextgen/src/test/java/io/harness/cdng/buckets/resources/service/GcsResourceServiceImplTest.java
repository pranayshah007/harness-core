/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.buckets.resources.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.gcp.GcpHelperService;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.gcp.GcpTaskType;
import io.harness.delegate.task.gcp.request.GcpListBucketsRequest;
import io.harness.delegate.task.gcp.response.GcpBucketDetails;
import io.harness.delegate.task.gcp.response.GcpListBucketsResponse;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class GcsResourceServiceImplTest extends CategoryTest {
  @Mock private GcpHelperService gcpHelperService;

  @InjectMocks private GcsResourceServiceImpl gcsResourceService;

  private final IdentifierRef connectorRef = IdentifierRef.builder().identifier("test").build();
  private final List<EncryptedDataDetail> encryptionDetails = singletonList(EncryptedDataDetail.builder().build());
  private GcpManualDetailsDTO manualDetailsDTO;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    GcpConnectorDTO gcpConnectorDTO =
        GcpConnectorDTO.builder()
            .credential(GcpConnectorCredentialDTO.builder().gcpCredentialType(INHERIT_FROM_DELEGATE).build())
            .build();
    manualDetailsDTO = GcpManualDetailsDTO.builder().build();

    doReturn(gcpConnectorDTO).when(gcpHelperService).getConnector(connectorRef);
    doReturn(encryptionDetails).when(gcpHelperService).getEncryptionDetails(eq(gcpConnectorDTO), any(NGAccess.class));
    doReturn(manualDetailsDTO).when(gcpHelperService).getManualDetailsDTO(gcpConnectorDTO); // Just for test purpose
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testListBuckets() {
    GcpListBucketsResponse response = GcpListBucketsResponse.builder()
                                          .buckets(asList(GcpBucketDetails.builder().id("test1").name("test1").build(),
                                              GcpBucketDetails.builder().id("test2").name("test2").build()))
                                          .build();

    doReturn(response)
        .when(gcpHelperService)
        .executeSyncTask(any(BaseNGAccess.class), any(GcpListBucketsRequest.class), eq(GcpTaskType.LIST_BUCKETS),
            eq("list GCS buckets"));
    Map<String, String> result = gcsResourceService.listBuckets(connectorRef, "account", "org", "project");
    assertThat(result).containsOnlyKeys("test1", "test2");
    assertThat(result).containsValues("test1", "test2");
    ArgumentCaptor<BaseNGAccess> ngAccessCaptor = ArgumentCaptor.forClass(BaseNGAccess.class);
    ArgumentCaptor<GcpListBucketsRequest> requestCaptor = ArgumentCaptor.forClass(GcpListBucketsRequest.class);
    verify(gcpHelperService, times(1))
        .executeSyncTask(
            ngAccessCaptor.capture(), requestCaptor.capture(), eq(GcpTaskType.LIST_BUCKETS), eq("list GCS buckets"));
    BaseNGAccess ngAccess = ngAccessCaptor.getValue();
    GcpListBucketsRequest request = requestCaptor.getValue();

    assertThat(ngAccess.getAccountIdentifier()).isEqualTo("account");
    assertThat(ngAccess.getOrgIdentifier()).isEqualTo("org");
    assertThat(ngAccess.getProjectIdentifier()).isEqualTo("project");
    assertThat(request.isUseDelegate()).isTrue();
    assertThat(request.getGcpManualDetailsDTO()).isEqualTo(manualDetailsDTO);
  }
}
