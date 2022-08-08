/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ContainerTaskMapper {
  public software.wings.beans.dto.KubernetesContainerTask toKubernetesContainerTaskDTO(
      KubernetesContainerTask kubernetesContainerTask) {
    if (kubernetesContainerTask == null) {
      return null;
    }

    return software.wings.beans.dto.KubernetesContainerTask.builder()
        .deploymentType(kubernetesContainerTask.getDeploymentType())
        .serviceId(kubernetesContainerTask.getServiceId())
        .advancedConfig(kubernetesContainerTask.getAdvancedConfig())
        .containerDefinitions(kubernetesContainerTask.getContainerDefinitions())
        .build();
  }

  public software.wings.beans.dto.EcsContainerTask toEcsContainerTaskDTO(EcsContainerTask ecsContainerTask) {
    if (ecsContainerTask == null) {
      return null;
    }

    return software.wings.beans.dto.EcsContainerTask.builder()
        .deploymentType(ecsContainerTask.getDeploymentType())
        .serviceId(ecsContainerTask.getServiceId())
        .advancedConfig(ecsContainerTask.getAdvancedConfig())
        .containerDefinitions(ecsContainerTask.getContainerDefinitions())
        .build();
  }
}
