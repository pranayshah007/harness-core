/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.envGroup;

import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.gitops.yaml.ClusterYaml;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EnvGroupPlanCreatorHelperTest extends CategoryTest {
  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCreateEnvConfigs() {
    EnvGroupPlanCreatorHelper envGroupPlanCreatorHelper = new EnvGroupPlanCreatorHelper();
    List<EnvironmentPlanCreatorConfig> envConfigs = new ArrayList<>();
    EnvironmentYamlV2 environmentYamlV2 = getEnvironmentYamlV2();
    Environment environment = getEnvironment();
    envGroupPlanCreatorHelper.createEnvConfigs(envConfigs, environmentYamlV2, environment);
    assertThat(envConfigs.size()).isNotZero();
    assertThat(envConfigs.get(0)).isNotNull();
    assertThat(envConfigs.get(0).getGitOpsClusterRefs().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCreateEnvConfigsForFiltersFlow() {
    EnvGroupPlanCreatorHelper envGroupPlanCreatorHelper = new EnvGroupPlanCreatorHelper();
    List<EnvironmentPlanCreatorConfig> envConfigs = new ArrayList<>();
    Environment environment = getEnvironment();
    List<String> clusterRefs = Arrays.asList("c1", "c2", "c3");
    envGroupPlanCreatorHelper.createEnvConfigsForFiltersFlow(envConfigs, environment, clusterRefs);
    assertThat(envConfigs.size()).isNotZero();
    assertThat(envConfigs.get(0)).isNotNull();
    assertThat(envConfigs.get(0).getGitOpsClusterRefs().size()).isEqualTo(clusterRefs.size());
  }

  private Environment getEnvironment() {
    List<NGTag> ngTags =
        Arrays.asList(NGTag.builder().key("k1").value("v1").build(), NGTag.builder().key("k2").value("v2").build());
    return Environment.builder()
        .accountId("accountId")
        .orgIdentifier("orgId")
        .projectIdentifier("projectId")
        .identifier("envId")
        .name("developmentEnv")
        .type(EnvironmentType.Production)
        .tags(ngTags)
        .build();
  }

  private EnvironmentYamlV2 getEnvironmentYamlV2() {
    return EnvironmentYamlV2.builder()
        .environmentRef(ParameterField.<String>builder().value("envId").build())
        .deployToAll(ParameterField.createValueField(false))
        .environmentInputs(ParameterField.createValueField(Map.of("k1", "v1")))
        .gitOpsClusters(
            ParameterField.<List<ClusterYaml>>builder()
                .value(asList(ClusterYaml.builder().identifier(ParameterField.createValueField("c1")).build(),
                    ClusterYaml.builder().identifier(ParameterField.createValueField("c2")).build()))
                .build())
        .build();
  }
}