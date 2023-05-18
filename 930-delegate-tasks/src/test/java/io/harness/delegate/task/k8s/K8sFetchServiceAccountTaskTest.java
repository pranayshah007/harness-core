/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.k8Connector.K8sServiceAccountInfoResponse;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesConnectionTaskParams;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.common.collect.ImmutableList;
import io.kubernetes.client.openapi.models.V1TokenReviewStatus;
import io.kubernetes.client.openapi.models.V1TokenReviewStatusBuilder;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CE)
@Slf4j
public class K8sFetchServiceAccountTaskTest extends CategoryTest {
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @InjectMocks
  private K8sFetchServiceAccountTask k8sFetchServiceAccountTask = new K8sFetchServiceAccountTask(
      DelegateTaskPackage.builder()
          .delegateId("delid1")
          .data(TaskData.builder().async(false).timeout(DEFAULT_SYNC_CALL_TIMEOUT).build())
          .build(),
      null, notifyResponseData -> {}, () -> true);

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testRunObjectParams() throws Exception {
    assertThatThrownBy(() -> k8sFetchServiceAccountTask.run(new Object[] {}))
        .isExactlyInstanceOf(NotImplementedException.class)
        .hasMessage("not implemented");
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testRunTaskParams() throws Exception {
    final String username = "system:serviceaccount:harness-delegate:default";
    final String group = "system:serviceaccount";

    V1TokenReviewStatus v1TokenReviewStatus = new V1TokenReviewStatusBuilder()
                                                  .withNewUser()
                                                  .withGroups()
                                                  .addToGroups(group)
                                                  .withUsername(username)
                                                  .endUser()
                                                  .build();

    when(k8sTaskHelperBase.fetchTokenReviewStatus(
             isA(KubernetesClusterConfigDTO.class), isA((Class<List<EncryptedDataDetail>>) (Object) List.class)))
        .thenReturn(v1TokenReviewStatus);

    DelegateResponseData delegateResponseData =
        k8sFetchServiceAccountTask.run(KubernetesConnectionTaskParams.builder()
                                           .kubernetesClusterConfig(KubernetesClusterConfigDTO.builder().build())
                                           .encryptionDetails(ImmutableList.of(EncryptedDataDetail.builder().build()))
                                           .build());

    assertThat(delegateResponseData).isNotNull().isExactlyInstanceOf(K8sServiceAccountInfoResponse.class);

    K8sServiceAccountInfoResponse response = (K8sServiceAccountInfoResponse) delegateResponseData;
    assertThat(response.getUsername()).isEqualTo(username);
    assertThat(response.getGroups()).containsExactlyInAnyOrder(group);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testRunTaskParamsThrowsException() throws Exception {
    final String message = "message";
    when(k8sTaskHelperBase.fetchTokenReviewStatus(
             isA(KubernetesClusterConfigDTO.class), isA((Class<List<EncryptedDataDetail>>) (Object) List.class)))
        .thenThrow(new InvalidRequestException(message));

    assertThatThrownBy(()
                           -> k8sFetchServiceAccountTask.run(
                               KubernetesConnectionTaskParams.builder()
                                   .kubernetesClusterConfig(KubernetesClusterConfigDTO.builder().build())
                                   .encryptionDetails(ImmutableList.of(EncryptedDataDetail.builder().build()))
                                   .build()))
        .isExactlyInstanceOf(InvalidRequestException.class)
        .hasMessage(message);
  }
}
