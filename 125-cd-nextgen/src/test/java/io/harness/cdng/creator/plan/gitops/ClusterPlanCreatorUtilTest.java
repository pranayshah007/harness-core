package io.harness.cdng.creator.plan.gitops;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.gitops.steps.ClusterStepParameters;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.yaml.core.variables.StringNGVariable;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ClusterPlanCreatorUtilTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetGitopsClustersStepPlanNode() {
    EnvironmentPlanCreatorConfig config = EnvironmentPlanCreatorConfig.builder()
                                              .environmentRef(ParameterField.<String>builder().value("env").build())
                                              .identifier("id")
                                              .description("desc")
                                              .orgIdentifier("orgId")
                                              .projectIdentifier("projId")
                                              .type(EnvironmentType.PreProduction)
                                              .variables(asList(new StringNGVariable()))
                                              .gitOpsClusterRefs(asList("c1", "c2"))
                                              .build();
    PlanNode planNode = ClusterPlanCreatorUtils.getGitopsClustersStepPlanNode(config);

    ClusterStepParameters stepParameters = (ClusterStepParameters) planNode.getStepParameters();

    assertThat(stepParameters.isDeployToAllEnvs()).isEqualTo(false);
    assertThat(stepParameters.getEnvClusterRefs()).hasSize(1);
    ClusterStepParameters.EnvClusterRefs envClusterRefs = stepParameters.getEnvClusterRefs().stream().findFirst().get();

    assertThat(envClusterRefs.getEnvRef()).isEqualTo("env");
    assertThat(envClusterRefs.getClusterRefs()).containsExactly("c1", "c2");
  }
}