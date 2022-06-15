/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.ci.integrationstage.K8InitializeServiceUtilsHelper.PORT_STARTING_RANGE;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.environment.ServiceDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.executionplan.CIExecutionTestBase;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.rule.Owner;
import io.harness.util.PortFinder;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class K8InitializeServiceUtilsTest extends CIExecutionTestBase {
  @Inject private K8InitializeServiceUtils k8InitializeServiceUtils;

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void createServiceContainerDefinitions() {
    StageElementConfig stageElementConfig = K8InitializeServiceUtilsHelper.getStageElementConfig();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();

    List<ContainerDefinitionInfo> expected = Arrays.asList(K8InitializeServiceUtilsHelper.getServiceContainer());
    List<ContainerDefinitionInfo> serviceContainers =
        k8InitializeServiceUtils.createServiceContainerDefinitions(stageElementConfig, portFinder, OSType.Linux);

    assertThat(serviceContainers).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getServiceInfos() {
    IntegrationStageConfig integrationStageConfig = K8InitializeServiceUtilsHelper.getIntegrationStageConfig();

    List<ServiceDefinitionInfo> expected = Arrays.asList(K8InitializeServiceUtilsHelper.getServiceDefintion());
    List<ServiceDefinitionInfo> serviceDefinitionInfos =
        k8InitializeServiceUtils.getServiceInfos(integrationStageConfig);

    assertThat(serviceDefinitionInfos).isEqualTo(expected);
  }
}
