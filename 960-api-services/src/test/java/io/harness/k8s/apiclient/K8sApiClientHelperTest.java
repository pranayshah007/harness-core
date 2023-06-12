/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.apiclient;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.utils.system.SystemWrapper;

import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class K8sApiClientHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetDefaultTimeoutWhenEmptyEnvVar() {
    try (MockedStatic<SystemWrapper> mockClient = mockStatic(SystemWrapper.class)) {
      when(SystemWrapper.getenv(any())).thenReturn("");
      Optional<Long> actualTimeout = K8sApiClientHelper.getTimeout("ENV_VAR");

      assertThat(actualTimeout).isEmpty();
    }
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetDefaultTimeoutWhenInvalidEnvVarValue() {
    try (MockedStatic<SystemWrapper> mockClient = mockStatic(SystemWrapper.class)) {
      when(SystemWrapper.getenv(any())).thenReturn("abc");
      Optional<Long> actualTimeout = K8sApiClientHelper.getTimeout("ENV_VAR");

      assertThat(actualTimeout).isEmpty();
    }
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetTimeout() {
    try (MockedStatic<SystemWrapper> mockClient = mockStatic(SystemWrapper.class)) {
      long timeout = 45;
      when(SystemWrapper.getenv(any())).thenReturn(String.valueOf(timeout));
      Optional<Long> actualTimeout = K8sApiClientHelper.getTimeout("ENV_VAR");

      assertThat(actualTimeout).isPresent();
      assertThat(actualTimeout).contains(timeout);
    }
  }
}
