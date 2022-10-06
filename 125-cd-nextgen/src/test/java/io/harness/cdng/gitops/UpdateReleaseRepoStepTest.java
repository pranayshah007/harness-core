package io.harness.cdng.gitops;

import static io.harness.rule.OwnerRule.LUCAS_SALES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.gitops.steps.GitopsClustersOutcome;
import io.harness.cdng.gitops.steps.GitopsClustersStep;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junitparams.JUnitParamsRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.GITOPS)
@RunWith(JUnitParamsRunner.class)
public class UpdateReleaseRepoStepTest extends CategoryTest {
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock protected OutcomeService outcomeService;
  @InjectMocks private UpdateReleaseRepoStep step;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testBuildFilePathsToVariablesMap() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    doAnswer(invocation -> invocation.getArgument(1, String.class))
        .when(engineExpressionService)
        .renderExpression(eq(ambiance), any());
    Map<String, Object> variables = new HashMap<>();
    variables.put("config1", ParameterField.builder().value("VALUE1").build());
    variables.put("config2", ParameterField.builder().expression(true).expressionValue("<+cluster.name>").build());
    variables.put("config3", ParameterField.builder().expression(true).expressionValue("<+env.name>").build());
    variables.put(
        "config4", ParameterField.builder().expression(true).expressionValue("<+env.name>/<+variable.foo>").build());
    GithubStore githubStore =
        GithubStore.builder().paths(ParameterField.<List<String>>builder().value(List.of("FILE_PATH")).build()).build();
    ManifestOutcome manifestOutcome = K8sManifestOutcome.builder().store(githubStore).build();

    List<GitopsClustersOutcome.ClusterData> clusterDataList = List.of(GitopsClustersOutcome.ClusterData.builder()
                                                                          .envId("IDENTIFIER")
                                                                          .envName("ENV_NAME")
                                                                          .clusterName("CLUSTER_NAME")
                                                                          .clusterId("CLUSTER_ID")
                                                                          .scope("SCOPE")
                                                                          .variables(new HashMap<>())
                                                                          .build());

    GitopsClustersOutcome gitopsClustersOutcome = new GitopsClustersOutcome(clusterDataList);
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(gitopsClustersOutcome).found(true).build();
    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(), eq(RefObjectUtils.getOutcomeRefObject(GitopsClustersStep.GITOPS_SWEEPING_OUTPUT)));

    Map<String, Map<String, String>> map = step.buildFilePathsToVariablesMap(manifestOutcome, ambiance, variables);
    Map<String, String> fileVariables = map.get("FILE_PATH");
    assertThat(fileVariables).isNotNull();
    assertThat(fileVariables.get("config1")).isEqualTo("VALUE1");
    assertThat(fileVariables.get("config2")).isEqualTo("CLUSTER_NAME");
    assertThat(fileVariables.get("config3")).isEqualTo("ENV_NAME");
    verify(engineExpressionService).renderExpression(ambiance, "ENV_NAME/<+variable.foo>");
  }
}
