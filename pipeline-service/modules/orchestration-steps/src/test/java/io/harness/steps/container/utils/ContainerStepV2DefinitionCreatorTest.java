/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.PluginCreationResponseList;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.StepInfo;
import io.harness.steps.plugin.infrastructure.ContainerInfraYamlSpec;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ContainerStepV2DefinitionCreatorTest extends CategoryTest {
  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetContainerDefinitionInfo() {
    InitContainerV2StepInfo initContainerV2StepInfo =
        InitContainerV2StepInfo.builder()
            .infrastructure(ContainerK8sInfra.builder()
                                .spec(ContainerInfraYamlSpec.builder()
                                          .os(ParameterField.<OSType>builder().value(OSType.Linux).build())
                                          .build())
                                .build())
            .pluginsData(Collections.singletonMap(StepInfo.builder().build(),
                PluginCreationResponseList.newBuilder()
                    .addResponse(PluginCreationResponseWrapper.newBuilder().setShouldSkip(true).build())
                    .build()))
            .build();

    List<ContainerDefinitionInfo> containerDefinitionInfoList =
        ContainerStepV2DefinitionCreator.getContainerDefinitionInfo(initContainerV2StepInfo, "stepGroupIdentifier");

    assertThat(containerDefinitionInfoList).isEmpty();
  }
}
