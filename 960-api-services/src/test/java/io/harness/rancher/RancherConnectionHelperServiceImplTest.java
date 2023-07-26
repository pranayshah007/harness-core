/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rancher;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.exception.ngexception.RancherClientRuntimeException;
import io.harness.rancher.RancherListClustersResponse.RancherClusterItem;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
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
    doThrow(RuntimeException.class).when(rancherClusterClient).listClusters(any(), any(), any());
    ConnectorValidationResult result = rancherConnectionHelperService.testRancherConnection("some/url", "some/token");
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testRancherConnectionSuccess() {
    doReturn(null).when(rancherClusterClient).listClusters(any(), any(), any());
    ConnectorValidationResult result = rancherConnectionHelperService.testRancherConnection("some/url", "some/token");
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testListClustersFailure() {
    doThrow(RancherClientRuntimeException.class).when(rancherClusterClient).listClusters(any(), any(), any());
    assertThatThrownBy(() -> rancherConnectionHelperService.listClusters("url", "token", Collections.emptyMap()))
        .isInstanceOf(RancherClientRuntimeException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testListClusters() {
    RancherListClustersResponse response = RancherListClustersResponse.builder()
                                               .resourceType("clusters")
                                               .data(List.of(RancherClusterItem.builder().name("c1").id("id1").build(),
                                                   RancherClusterItem.builder().name("c2").id("id2").build()))
                                               .build();
    doReturn(response).when(rancherClusterClient).listClusters(any(), any(), any());
    assertThat(rancherConnectionHelperService.listClusters("url", "token", Collections.emptyMap()))
        .containsExactlyInAnyOrder("id1", "id2");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testKubeconfigGeneration() {
    RancherGenerateKubeconfigResponse response =
        RancherGenerateKubeconfigResponse.builder().config("KUBECONFIG").build();
    doReturn(response).when(rancherClusterClient).generateKubeconfig(any(), any(), any());
    assertThat(rancherConnectionHelperService.generateKubeconfig("url", "token", "cluster")).isEqualTo("KUBECONFIG");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testKubeconfigGenerationFailure() {
    doThrow(RancherClientRuntimeException.class).when(rancherClusterClient).generateKubeconfig(any(), any(), any());
    assertThatThrownBy(() -> rancherConnectionHelperService.generateKubeconfig("url", "token", "cluster"))
        .isInstanceOf(RancherClientRuntimeException.class);
  }
}
