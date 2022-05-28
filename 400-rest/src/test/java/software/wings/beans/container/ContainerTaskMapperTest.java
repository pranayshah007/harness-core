/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import static io.harness.rule.OwnerRule.JOHANNES;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ContainerTaskMapperTest extends CategoryTest {
  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void toKubernetesContainerTaskDTOSmokeTest() {
    KubernetesContainerTask containerTask = new KubernetesContainerTask();
    containerTask.setServiceId("SomeServiceId");
    containerTask.setAdvancedConfig("SomeAdvancedConfig: true");
    containerTask.setContainerDefinitions(
        Arrays.asList(new ContainerDefinition(null, "Def1", null, null, null, null, null)));

    software.wings.beans.dto.KubernetesContainerTask containerTaskDTO =
        ContainerTaskMapper.toKubernetesContainerTaskDTO(containerTask);

    assertThat(containerTaskDTO).isNotNull();
    assertThat(containerTaskDTO.getDeploymentType()).isEqualTo(containerTask.getDeploymentType());
    assertThat(containerTaskDTO.getServiceId()).isEqualTo(containerTask.getServiceId());
    assertThat(containerTaskDTO.getAdvancedConfig()).isEqualTo(containerTask.getAdvancedConfig());
    assertThat(containerTaskDTO.getContainerDefinitions()).isEqualTo(containerTask.getContainerDefinitions());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void toKubernetesContainerTaskDTOForNull() {
    assertThat(ContainerTaskMapper.toKubernetesContainerTaskDTO(null)).isNull();
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void toEcsContainerTaskDTOSmokeTest() {
    EcsContainerTask containerTask = new EcsContainerTask();
    containerTask.setServiceId("SomeServiceId");
    containerTask.setAdvancedConfig("SomeAdvancedConfig: true");
    containerTask.setContainerDefinitions(
        Arrays.asList(new ContainerDefinition(null, "Def1", null, null, null, null, null)));

    software.wings.beans.dto.EcsContainerTask containerTaskDTO =
        ContainerTaskMapper.toEcsContainerTaskDTO(containerTask);

    assertThat(containerTaskDTO).isNotNull();
    assertThat(containerTaskDTO.getDeploymentType()).isEqualTo(containerTask.getDeploymentType());
    assertThat(containerTaskDTO.getServiceId()).isEqualTo(containerTask.getServiceId());
    assertThat(containerTaskDTO.getAdvancedConfig()).isEqualTo(containerTask.getAdvancedConfig());
    assertThat(containerTaskDTO.getContainerDefinitions()).isEqualTo(containerTask.getContainerDefinitions());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void toEcsContainerTaskDTOForNull() {
    assertThat(ContainerTaskMapper.toEcsContainerTaskDTO(null)).isNull();
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void ensureAdvancedConfigTrimmingMatchesOriginal() {
    String advancedConfig = "SomeAdvancedConfig: true \na  \nb\nc";

    KubernetesContainerTask containerTask = new KubernetesContainerTask();
    containerTask.setAdvancedConfig(advancedConfig);
    software.wings.beans.dto.KubernetesContainerTask containerTaskDTO =
        software.wings.beans.dto.KubernetesContainerTask.builder().advancedConfig(advancedConfig).build();

    assertThat(containerTaskDTO.getAdvancedConfig()).isNotEqualTo(advancedConfig);
    assertThat(containerTaskDTO.getAdvancedConfig()).isEqualTo(containerTask.getAdvancedConfig());
  }
}
