/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.gitops;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.gitops.steps.ClusterStepParameters;
import io.harness.cdng.gitops.steps.EnvClusterRefs;
import io.harness.cdng.gitops.steps.GitopsClustersStep;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class ClusterPlanCreatorUtilsTest {
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getData")
  public void testGetGitopsClustersStepPlanNodeBuilder(
      EnvironmentPlanCreatorConfig input, ClusterStepParameters output) {
    final String nodeUuid = "foobar";

    PlanNode expected = ClusterPlanCreatorUtils.getGitopsClustersStepPlanNodeBuilder(nodeUuid, input).build();

    assertThat(expected.getFacilitatorObtainments()).isNotNull();
    assertThat(expected.getIdentifier()).isEqualTo("GitopsClusters");
    assertThat(expected.getUuid()).isEqualTo(nodeUuid);
    assertThat(expected.getName()).isEqualTo("GitopsClusters");
    assertThat(expected.getStepType()).isEqualTo(GitopsClustersStep.STEP_TYPE);

    ClusterStepParameters stepParameters = (ClusterStepParameters) expected.getStepParameters();
    compareStepParams(output, stepParameters);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getDataForEnvGroup")
  public void testGetGitopsClustersStepPlanNodeBuilderEnvGroup(
      EnvGroupPlanCreatorConfig input, ClusterStepParameters output) {
    final String nodeUuid = "foobar";

    PlanNode expected = ClusterPlanCreatorUtils.getGitopsClustersStepPlanNodeBuilder(nodeUuid, input).build();

    assertThat(expected.getFacilitatorObtainments()).isNotNull();
    assertThat(expected.getIdentifier()).isEqualTo("GitopsClusters");
    assertThat(expected.getUuid()).isEqualTo(nodeUuid);
    assertThat(expected.getName()).isEqualTo("GitopsClusters");
    assertThat(expected.getStepType()).isEqualTo(GitopsClustersStep.STEP_TYPE);

    ClusterStepParameters stepParameters = (ClusterStepParameters) expected.getStepParameters();
    compareStepParams(output, stepParameters);
  }

  // Method to provide parameters to test
  private Object[][] getData() {
    EnvironmentPlanCreatorConfig i1 = EnvironmentPlanCreatorConfig.builder()
                                          .environmentRef(ParameterField.<String>builder().value("myenv").build())
                                          .deployToAll(true)
                                          .build();
    StepParameters o1 = ClusterStepParameters.builder()
                            .envClusterRefs(Collections.singletonList(
                                EnvClusterRefs.builder().envRef("myenv").deployToAll(true).build()))
                            .build();

    EnvironmentPlanCreatorConfig i2 = EnvironmentPlanCreatorConfig.builder()
                                          .environmentRef(ParameterField.<String>builder().value("myenv").build())
                                          .deployToAll(false)
                                          .gitOpsClusterRefs(asList("c1", "c2", "c3"))
                                          .build();
    StepParameters o2 = ClusterStepParameters.builder()
                            .envClusterRefs(asList(EnvClusterRefs.builder()
                                                       .envRef("myenv")
                                                       .deployToAll(false)
                                                       .clusterRefs(Set.of("c3", "c1", "c2"))
                                                       .build()))
                            .build();
    return new Object[][] {{i1, o1}, {i2, o2}};
  }

  private Object[][] getDataForEnvGroup() {
    EnvGroupPlanCreatorConfig i1 =
        EnvGroupPlanCreatorConfig.builder()
            .environmentGroupRef(ParameterField.<String>builder().value("myenvgroup").build())
            .deployToAll(true)
            .build();
    StepParameters o1 = ClusterStepParameters.builder().envGroupRef("myenvgroup").deployToAllEnvs(true).build();

    EnvGroupPlanCreatorConfig i2 =
        EnvGroupPlanCreatorConfig.builder()
            .environmentGroupRef(ParameterField.<String>builder().value("myenvgroup").build())
            .deployToAll(false)
            .environmentPlanCreatorConfigs(
                asList(EnvironmentPlanCreatorConfig.builder()
                           .environmentRef(ParameterField.<String>builder().value("env1").build())
                           .deployToAll(false)
                           .gitOpsClusterRefs(asList("c1", "c2"))
                           .build(),
                    EnvironmentPlanCreatorConfig.builder()
                        .environmentRef(ParameterField.<String>builder().value("env2").build())
                        .deployToAll(true)
                        .build(),
                    EnvironmentPlanCreatorConfig.builder()
                        .environmentRef(ParameterField.<String>builder().value("env3").build())
                        .deployToAll(false)
                        .gitOpsClusterRefs(asList("c3", "c4", "c5"))
                        .build()))
            .build();
    StepParameters o2 =
        ClusterStepParameters.builder()
            .envGroupRef("envgroup")
            .deployToAllEnvs(false)
            .envClusterRefs(asList(
                EnvClusterRefs.builder().envRef("env1").deployToAll(false).clusterRefs(Set.of("c1", "c2")).build(),
                EnvClusterRefs.builder().envRef("env2").deployToAll(true).build(),
                EnvClusterRefs.builder().envRef("env3").clusterRefs(Set.of("c3", "c4", "c5")).build()))
            .build();
    return new Object[][] {{i1, o1}, {i2, o2}};
  }

  private void compareStepParams(ClusterStepParameters s1, ClusterStepParameters s2) {
    Map<String, EnvClusterRefs> expectedEnvClusterRefs =
        s2.getEnvClusterRefs().stream().collect(Collectors.toMap(EnvClusterRefs::getEnvRef, Function.identity()));
    Map<String, EnvClusterRefs> actualEnvClusterRefs =
        s1.getEnvClusterRefs().stream().collect(Collectors.toMap(EnvClusterRefs::getEnvRef, Function.identity()));
    assertThat(expectedEnvClusterRefs).isEqualTo(actualEnvClusterRefs);
    assertThat(s2.getEnvGroupRef()).isEqualTo(s2.getEnvGroupRef());
    assertThat(s2.isDeployToAllEnvs()).isEqualTo(s2.isDeployToAllEnvs());
  }
}
