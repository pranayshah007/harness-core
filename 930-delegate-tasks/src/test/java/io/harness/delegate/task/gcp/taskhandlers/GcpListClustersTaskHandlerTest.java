/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gcp.taskhandlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.gcp.helpers.GkeClusterHelper;
import io.harness.delegate.task.gcp.request.GcpListClustersRequest;
import io.harness.delegate.task.gcp.request.GcpValidationRequest;
import io.harness.delegate.task.gcp.response.GcpClusterListTaskResponse;
import io.harness.encryption.SecretRefData;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class GcpListClustersTaskHandlerTest extends CategoryTest {
  @Mock private GkeClusterHelper gkeClusterHelper;
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private NGErrorHelper ngErrorHelper;
  @InjectMocks private GcpListClustersTaskHandler taskHandler;
  private String accountIdentifier = "accountIdentifier";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void executeGcpTaskUseDelegateSuccess() {
    GcpListClustersRequest request =
        GcpListClustersRequest.builder().delegateSelectors(ImmutableSet.of("delegate1")).build();
    doReturn(Arrays.asList("cluster1", "cluster2")).when(gkeClusterHelper).listClusters(eq(null), eq(true));

    final GcpClusterListTaskResponse response = (GcpClusterListTaskResponse) taskHandler.executeRequest(request);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getClusterNames()).containsExactly("cluster1", "cluster2");

    verify(gkeClusterHelper, times(1)).listClusters(eq(null), eq(true));
    verify(secretDecryptionService, times(0)).decrypt(any(), anyList());
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void executeGcpTaskManualCredentialsSuccess() {
    char[] secret = "secret".toCharArray();
    GcpListClustersRequest request =
        GcpListClustersRequest.builder()
            .encryptionDetails(Collections.emptyList())
            .gcpManualDetailsDTO(GcpManualDetailsDTO.builder()
                                     .secretKeyRef(SecretRefData.builder().decryptedValue(secret).build())
                                     .build())
            .build();
    doReturn(Arrays.asList("cluster1", "cluster2")).when(gkeClusterHelper).listClusters(eq(secret), eq(false));

    final GcpClusterListTaskResponse response = (GcpClusterListTaskResponse) taskHandler.executeRequest(request);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getClusterNames()).containsExactly("cluster1", "cluster2");

    verify(gkeClusterHelper, times(1)).listClusters(eq(secret), eq(false));
    verify(secretDecryptionService, times(1)).decrypt(any(), anyList());
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void executeGcpTaskManualCredentialsFailure() {
    char[] secret = "secret".toCharArray();
    GcpListClustersRequest request =
        GcpListClustersRequest.builder()
            .encryptionDetails(Collections.emptyList())
            .gcpManualDetailsDTO(GcpManualDetailsDTO.builder()
                                     .secretKeyRef(SecretRefData.builder().decryptedValue(secret).build())
                                     .build())
            .build();
    doThrow(new RuntimeException("No credentials found")).when(gkeClusterHelper).listClusters(eq(secret), eq(false));
    doReturn("No credentials found").when(ngErrorHelper).getErrorSummary(anyString());

    final GcpClusterListTaskResponse response = (GcpClusterListTaskResponse) taskHandler.executeRequest(request);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getClusterNames()).isNull();
    assertThat(response.getErrorMessage()).isEqualTo("No credentials found");

    verify(gkeClusterHelper, times(1)).listClusters(eq(secret), eq(false));
    verify(secretDecryptionService, times(1)).decrypt(any(), anyList());
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void shouldFailWhenWrongRequestType() {
    String errorMessage =
        "Invalid GCP request type, expecting: class io.harness.delegate.task.gcp.request.GcpListClustersRequest";
    GcpValidationRequest request = GcpValidationRequest.builder().build();
    doReturn(errorMessage).when(ngErrorHelper).getErrorSummary(anyString());

    final GcpClusterListTaskResponse response = (GcpClusterListTaskResponse) taskHandler.executeRequest(request);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getClusterNames()).isNull();
    assertThat(response.getErrorMessage()).isEqualTo(errorMessage);

    verify(gkeClusterHelper, times(0)).listClusters(any(), anyBoolean());
    verify(secretDecryptionService, times(0)).decrypt(any(), anyList());
  }
}
