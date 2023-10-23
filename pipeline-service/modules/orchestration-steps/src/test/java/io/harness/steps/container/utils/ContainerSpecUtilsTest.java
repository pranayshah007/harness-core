/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.plugin.ContainerStepInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ContainerSpecUtilsTest extends CategoryTest {
  public static final String CONNECTOR_ORIGIN = "ConnectorDisconnectHandler";

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetDelegateSelectors() {
    ContainerStepInfo containerStepSpec = ContainerStepInfo.infoBuilder().build();
    assertThat(ContainerSpecUtils.getStepDelegateSelectors(containerStepSpec)).isEmpty();

    containerStepSpec.setDelegateSelectors(ParameterField.ofNull());
    assertThat(ContainerSpecUtils.getStepDelegateSelectors(containerStepSpec)).isEmpty();

    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("foo");
    taskSelectorYaml.setOrigin("step");
    containerStepSpec.setDelegateSelectors(ParameterField.createValueField(List.of(taskSelectorYaml)));
    assertThat(ContainerSpecUtils.getStepDelegateSelectors(containerStepSpec))
        .containsExactly(TaskSelector.newBuilder().setSelector("foo").setOrigin("step").build());

    containerStepSpec.setDelegateSelectors(ParameterField.createExpressionField(true, "<+unresolved>", null, false));
    assertThat(ContainerSpecUtils.getStepDelegateSelectors(containerStepSpec)).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testGetMergedDelegateSelectors() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorConfig(KubernetesClusterConfigDTO.builder().delegateSelectors(new HashSet<>()).build())
            .build();
    ContainerStepInfo containerStepSpec = ContainerStepInfo.infoBuilder().build();
    assertThat(ContainerSpecUtils.mergeStepAndConnectorOriginDelegateSelectors(containerStepSpec, connectorDetails))
        .isEmpty();
    connectorDetails.setConnectorConfig(KubernetesClusterConfigDTO.builder()
                                            .delegateSelectors(new HashSet<>(Arrays.asList("selector1", "selector2")))
                                            .build());

    TaskSelectorYaml taskSelectorYaml1 = new TaskSelectorYaml("selector1");
    taskSelectorYaml1.setOrigin(CONNECTOR_ORIGIN);
    TaskSelectorYaml taskSelectorYaml2 = new TaskSelectorYaml("selector2");
    taskSelectorYaml2.setOrigin(CONNECTOR_ORIGIN);

    assertThat(ContainerSpecUtils.mergeStepAndConnectorOriginDelegateSelectors(containerStepSpec, connectorDetails))
        .isEqualTo(Arrays.asList(
            TaskSelectorYaml.toTaskSelector(taskSelectorYaml1), TaskSelectorYaml.toTaskSelector(taskSelectorYaml2)));
  }
}
