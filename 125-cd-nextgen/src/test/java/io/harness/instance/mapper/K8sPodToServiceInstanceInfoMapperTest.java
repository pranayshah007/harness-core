/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.instance.mapper;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.K8sServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.K8sPodToServiceInstanceInfoMapper;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sContainer;
import io.harness.k8s.model.K8sPod;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class K8sPodToServiceInstanceInfoMapperTest extends CategoryTest {
  private static final String POD_ID = "podId";
  private static final String POD_NAME = "podName";
  private static final String NAMESPACE = "namespace";
  private static final String RELEASE_NAME = "releaseName";
  private static final String CONTAINER_ID = "containerId";
  private static final String IMAGE = "image";
  private static final String CONTAINER_NAME = "containerName";
  private static final String BLUE_GREEN_COLOR = "blueGreenColor";

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testToServerInstanceInfoList() {
    List<ServerInstanceInfo> serverInstanceInfos =
        K8sPodToServiceInstanceInfoMapper.toServerInstanceInfoList(getServiceInstanceInfos(), null);

    assertThat(serverInstanceInfos.size()).isEqualTo(1);
    assertThat(serverInstanceInfos.get(0)).isInstanceOf(K8sServerInstanceInfo.class);
    K8sServerInstanceInfo serverInstanceInfo = (K8sServerInstanceInfo) serverInstanceInfos.get(0);
    assertThat(serverInstanceInfo.getPodIP()).isEqualTo(POD_ID);
    assertThat(serverInstanceInfo.getName()).isEqualTo(POD_NAME);
    assertThat(serverInstanceInfo.getNamespace()).isEqualTo(NAMESPACE);
    assertThat(serverInstanceInfo.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(serverInstanceInfo.getBlueGreenColor()).isEqualTo(BLUE_GREEN_COLOR);

    List<K8sContainer> containerList = serverInstanceInfo.getContainerList();
    assertThat(containerList.size()).isEqualTo(1);
    K8sContainer k8sContainer = containerList.get(0);
    assertThat(k8sContainer.getContainerId()).isEqualTo(CONTAINER_ID);
    assertThat(k8sContainer.getImage()).isEqualTo(IMAGE);
    assertThat(k8sContainer.getName()).isEqualTo(CONTAINER_NAME);
  }

  private List<K8sPod> getServiceInstanceInfos() {
    return Collections.singletonList(K8sPod.builder()
                                         .podIP(POD_ID)
                                         .name(POD_NAME)
                                         .namespace(NAMESPACE)
                                         .releaseName(RELEASE_NAME)
                                         .labels(getLabels())
                                         .containerList(getContainerList())
                                         .build());
  }

  private HashMap<String, String> getLabels() {
    HashMap<String, String> labels = new HashMap<>();
    labels.put(HarnessLabels.color, BLUE_GREEN_COLOR);
    return labels;
  }

  private List<K8sContainer> getContainerList() {
    return Collections.singletonList(
        K8sContainer.builder().containerId(CONTAINER_ID).image(IMAGE).name(CONTAINER_NAME).build());
  }
}
