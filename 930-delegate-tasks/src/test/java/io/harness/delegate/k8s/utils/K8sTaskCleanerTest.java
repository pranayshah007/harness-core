/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.utils;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.K8sTaskCleanupDTO;
import io.harness.delegate.task.k8s.RancherK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.rancher.RancherHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.rancher.RancherConnectionHelperService;
import io.harness.rule.Owner;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class K8sTaskCleanerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks @Spy K8sTaskCleaner k8sTaskCleaner;

  @Mock RancherConnectionHelperService rancherConnectionHelperService;

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testCleanupFailureShouldNotThrowException() {
    String dummy = "dummy";
    try (MockedStatic<RancherHelper> mockedStatic = mockStatic(RancherHelper.class)) {
      mockedStatic.when(() -> RancherHelper.getRancherUrl(any())).thenReturn(dummy);
      mockedStatic.when(() -> RancherHelper.getRancherBearerToken(any())).thenReturn(dummy);
      mockedStatic.when(() -> RancherHelper.getKubeConfigTokenName(any())).thenReturn(dummy);
      K8sTaskCleanupDTO cleanupDTO = mock(K8sTaskCleanupDTO.class);
      RancherK8sInfraDelegateConfig rancherK8sInfraDelegateConfig = mock(RancherK8sInfraDelegateConfig.class);
      doReturn(rancherK8sInfraDelegateConfig).when(cleanupDTO).getInfraDelegateConfig();
      doThrow(InvalidRequestException.class)
          .when(rancherConnectionHelperService)
          .deleteKubeconfigToken(anyString(), anyString(), anyString());

      Assertions.assertThatCode(() -> k8sTaskCleaner.cleanup(cleanupDTO)).doesNotThrowAnyException();
      verify(rancherConnectionHelperService, times(1)).deleteKubeconfigToken(any(), any(), any());
    }
  }
}
