/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.k8Connector.K8sValidationParams;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesConnectionTaskParams;
import io.harness.delegate.beans.connector.k8Connector.KubernetesConnectionTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KubernetesTestConnectionDelegateTaskTest extends CategoryTest {
  @Mock KubernetesValidationHandler kubernetesValidationHandler;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock Consumer<DelegateTaskResponse> consumer;
  @Mock BooleanSupplier preExecute;

  final String accountId = "accountId";
  final DelegateTaskPackage delegateTaskPackage =
      DelegateTaskPackage.builder().data(TaskData.builder().build()).accountId(accountId).build();

  @InjectMocks
  KubernetesTestConnectionDelegateTask testConnectionDelegateTask =
      new KubernetesTestConnectionDelegateTask(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRun() {
    final List<EncryptedDataDetail> encryptionDetails = Collections.emptyList();
    final KubernetesClusterConfigDTO kubernetesClusterConfig = KubernetesClusterConfigDTO.builder().build();
    final KubernetesConnectionTaskParams taskParams = KubernetesConnectionTaskParams.builder()
                                                          .encryptionDetails(encryptionDetails)
                                                          .kubernetesClusterConfig(kubernetesClusterConfig)
                                                          .build();
    final ConnectorValidationResult expectedResult = ConnectorValidationResult.builder().build();

    doReturn(expectedResult).when(kubernetesValidationHandler).validate(any(K8sValidationParams.class), eq(accountId));

    final KubernetesConnectionTaskResponse result = testConnectionDelegateTask.run(taskParams);
    ArgumentCaptor<K8sValidationParams> params = ArgumentCaptor.forClass(K8sValidationParams.class);

    verify(kubernetesValidationHandler).validate(params.capture(), eq(accountId));

    K8sValidationParams validationParams = params.getValue();
    assertThat(result.getConnectorValidationResult()).isSameAs(expectedResult);
    assertThat(validationParams.getEncryptedDataDetails()).isEqualTo(encryptionDetails);
    assertThat(validationParams.getKubernetesClusterConfigDTO()).isEqualTo(kubernetesClusterConfig);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunUnimplemented() {
    assertThatThrownBy(() -> testConnectionDelegateTask.run(new Object[] {}))
        .isInstanceOf(NotImplementedException.class);
  }
}
