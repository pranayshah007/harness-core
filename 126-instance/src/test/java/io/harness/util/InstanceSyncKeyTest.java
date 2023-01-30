/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.util;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.k8s.model.K8sContainer;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class InstanceSyncKeyTest extends CategoryTest {
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testK8sDeploymentInfoDTOPrepareInstanceSyncHandlerKey() {
    K8sDeploymentInfoDTO k8sDeploymentInfoDTO = K8sDeploymentInfoDTO.builder()
                                                    .blueGreenStageColor("blueGreenStageColor")
                                                    .releaseName("releaseName")
                                                    .namespaces(getNamespaces())
                                                    .build();

    String instanceSyncHandlerKey = k8sDeploymentInfoDTO.prepareInstanceSyncHandlerKey();

    assertThat(instanceSyncHandlerKey).isNotBlank();
    assertThat(instanceSyncHandlerKey).isEqualTo("releaseName_blueGreenStageColor");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testK8sInstanceInfoDTOPrepareInstanceSyncHandlerKey() {
    K8sInstanceInfoDTO k8sInstanceInfoDTO = K8sInstanceInfoDTO.builder()
                                                .namespace("namespace")
                                                .podName("podName")
                                                .releaseName("releaseName")
                                                .containerList(getContainerList())
                                                .build();

    String instanceSyncHandlerKey = k8sInstanceInfoDTO.prepareInstanceSyncHandlerKey();
    String instanceKey = k8sInstanceInfoDTO.prepareInstanceKey();

    assertThat(instanceSyncHandlerKey).isNotBlank();
    assertThat(instanceSyncHandlerKey).isEqualTo("releaseName");

    assertThat(instanceKey).isNotBlank();
    assertThat(instanceKey).isEqualTo("K8sInstanceInfoDTO_podName_namespace_image1image2");
  }

  private List<K8sContainer> getContainerList() {
    List<K8sContainer> k8sContainers = new LinkedList<>();
    k8sContainers.add(K8sContainer.builder().image("image1").build());
    k8sContainers.add(K8sContainer.builder().image("image2").build());

    return k8sContainers;
  }

  private LinkedHashSet<String> getNamespaces() {
    return new LinkedHashSet<>(Arrays.asList("namespace1", "namespace2", "namespace3"));
  }
}
