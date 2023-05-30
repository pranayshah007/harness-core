/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rancher;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class RancherConnectionHelperServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Spy @InjectMocks RancherConnectionHelperServiceImpl rancherConnectionHelperService;
  @Mock RancherClusterClient rancherClusterClient;

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testRancherConnectionFailure() {
    doThrow(RuntimeException.class).when(rancherClusterClient).listClusters(any(), any());
    ConnectorValidationResult result = rancherConnectionHelperService.testRancherConnection("some/url", "some/token");
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testRancherConnectionSuccess() {
    doReturn(null).when(rancherClusterClient).listClusters(any(), any());
    ConnectorValidationResult result = rancherConnectionHelperService.testRancherConnection("some/url", "some/token");
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }
}
