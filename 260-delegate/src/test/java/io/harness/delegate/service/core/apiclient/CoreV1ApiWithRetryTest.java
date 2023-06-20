/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.apiclient;

import static io.harness.rule.OwnerRule.MARKO;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.api.mockito.PowerMockito;

//@PrepareForTest({CoreV1Api.class})
@RunWith(MockitoJUnitRunner.class)
public class CoreV1ApiWithRetryTest {
  private CoreV1ApiWithRetry underTest;
  @Mock private ApiClient apiClient;

  @Before
  public void setUp() {
    underTest = new CoreV1ApiWithRetry(apiClient);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedPod() throws ApiException {
    doReturn(new V1Pod()).when(apiClient).execute(any(), any());
    final var actual = underTest.createNamespacedPod(null, null, null, null, null, null);
    assertNotNull(actual);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedSecret() throws ApiException {
    PowerMockito.when(underTest.createNamespacedSecret(null, null, null, null, null, null))
        .thenThrow(ApiException.class);
  }
//
//  @Test
//  public void testCreateNamespacedPodWithRetry() {}
//
//  @Test
//  public void whenApiExceptionThenCreateNamespacedPodRetry() {}
//
//  @Test
//  public void whenNonApiExceptionThenCreateNamespacedPodFails() {}
}
