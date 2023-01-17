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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.k8Connector.K8sValidationParams;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.task.k8s.helpers.k8sbase.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.k8stestconnectiontask.KubernetesValidationHandler;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KubernetesValidationHandlerTest extends CategoryTest {
  @Mock
  K8sTaskHelperBase taskHelperBase;

  @InjectMocks
  KubernetesValidationHandler kubernetesValidationHandler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidate() {
    final List<EncryptedDataDetail> encryptionDetails = Collections.emptyList();
    final KubernetesClusterConfigDTO kubernetesClusterConfig = KubernetesClusterConfigDTO.builder().build();
    final K8sValidationParams k8sValidationParams = K8sValidationParams.builder()
                                                        .kubernetesClusterConfigDTO(kubernetesClusterConfig)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .build();
    final ConnectorValidationResult expectedResult = ConnectorValidationResult.builder().build();

    doReturn(expectedResult).when(taskHelperBase).validate(kubernetesClusterConfig, encryptionDetails);

    ConnectorValidationResult result = kubernetesValidationHandler.validate(k8sValidationParams, "account");

    verify(taskHelperBase).validate(kubernetesClusterConfig, encryptionDetails);
    assertThat(result).isSameAs(expectedResult);
  }
}
