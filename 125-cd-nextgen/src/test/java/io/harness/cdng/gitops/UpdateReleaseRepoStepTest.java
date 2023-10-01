/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops;

import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_SWEEPING_OUTPUT;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.MANKRIT;
import static io.harness.rule.OwnerRule.MEENA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.gitops.steps.GitopsClustersOutcome;
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
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junitparams.JUnitParamsRunner;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.GITOPS)
@RunWith(JUnitParamsRunner.class)
public class UpdateReleaseRepoStepTest extends CDNGTestBase {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock protected OutcomeService outcomeService;
  @InjectMocks private UpdateReleaseRepoStep step;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    Reflect.on(cdExpressionResolver).set("engineExpressionService", engineExpressionService);
    Reflect.on(cdExpressionResolver).set("ngFeatureFlagHelperService", ngFeatureFlagHelperService);
    Reflect.on(step).set("cdExpressionResolver", cdExpressionResolver);
    doReturn(false).when(ngFeatureFlagHelperService).isEnabled(any(), any());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testBuildFilePathsToVariablesMap() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    doAnswer(invocation -> invocation.getArgument(1))
        .when(engineExpressionService)
        .renderExpression(eq(ambiance), any(), any());
    Map<String, Object> variables = new HashMap<>();
    variables.put("config1", ParameterField.builder().value("VALUE1").build());
    variables.put("config2", ParameterField.builder().expression(true).expressionValue("<+cluster.name>").build());
    variables.put("config3", ParameterField.builder().expression(true).expressionValue("<+env.name>").build());
    variables.put(
        "config4", ParameterField.builder().expression(true).expressionValue("<+env.name>/<+variable.foo>").build());
    GithubStore githubStore =
        GithubStore.builder()
            .paths(
                ParameterField.<List<String>>builder().value(List.of("FILE_PATH/<+env.name>/<+cluster.name>")).build())
            .build();
    ManifestOutcome manifestOutcome = K8sManifestOutcome.builder().store(githubStore).build();

    GitopsClustersOutcome.ClusterData cluster1 = GitopsClustersOutcome.ClusterData.builder()
                                                     .envId("IDENTIFIER")
                                                     .envName("ENV_NAME")
                                                     .clusterName("CLUSTER_NAME")
                                                     .clusterId("CLUSTER_ID")
                                                     .scope("SCOPE")
                                                     .variables(new HashMap<>())
                                                     .build();

    GitopsClustersOutcome.ClusterData cluster2 = GitopsClustersOutcome.ClusterData.builder()
                                                     .envId("IDENTIFIER2")
                                                     .envName("ENV_NAME2")
                                                     .clusterName("CLUSTER_NAME2")
                                                     .clusterId("CLUSTER_ID2")
                                                     .scope("SCOPE2")
                                                     .variables(new HashMap<>())
                                                     .build();

    List<GitopsClustersOutcome.ClusterData> clusterDataList = List.of(cluster1, cluster2);

    GitopsClustersOutcome gitopsClustersOutcome = new GitopsClustersOutcome(clusterDataList);
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(gitopsClustersOutcome).found(true).build();
    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(), eq(RefObjectUtils.getOutcomeRefObject(GITOPS_SWEEPING_OUTPUT)));

    Map<String, Map<String, String>> map = step.buildFilePathsToVariablesMap(manifestOutcome, ambiance, variables);
    Map<String, String> fileVariables = map.get("FILE_PATH/ENV_NAME/CLUSTER_NAME");
    assertThat(fileVariables).isNotNull();
    assertThat(fileVariables.get("config1")).isEqualTo("VALUE1");
    assertThat(fileVariables.get("config2")).isEqualTo("CLUSTER_NAME");
    assertThat(fileVariables.get("config3")).isEqualTo("ENV_NAME");
    Map<String, String> fileVariables2 = map.get("FILE_PATH/ENV_NAME2/CLUSTER_NAME2");
    assertThat(fileVariables2).isNotNull();
    assertThat(fileVariables2.get("config1")).isEqualTo("VALUE1");
    assertThat(fileVariables2.get("config2")).isEqualTo("CLUSTER_NAME2");
    assertThat(fileVariables2.get("config3")).isEqualTo("ENV_NAME2");
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testBuildFilePathsToVariablesMapForEnvGroup() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    doAnswer(invocation -> invocation.getArgument(1))
        .when(engineExpressionService)
        .renderExpression(eq(ambiance), any(), any());
    Map<String, Object> variables = new HashMap<>();
    variables.put("config1", ParameterField.builder().value("VALUE1").build());
    variables.put("configNum", ParameterField.builder().value("3.0").build());
    variables.put("configFloat", ParameterField.builder().value("3.14").build());
    variables.put("configNumFail", ParameterField.builder().value("3a.0").build());
    variables.put("config2", ParameterField.builder().expression(true).expressionValue("<+cluster.name>").build());
    variables.put("config3", ParameterField.builder().expression(true).expressionValue("<+env.name>").build());
    variables.put(
        "config4", ParameterField.builder().expression(true).expressionValue("<+env.name>/<+variable.foo>").build());
    variables.put("config5", ParameterField.builder().expression(true).expressionValue("<+envgroup.name>").build());
    GithubStore githubStore = GithubStore.builder()
                                  .paths(ParameterField.<List<String>>builder()
                                             .value(List.of("FILE_PATH/<+envgroup.name>/<+env.name>/<+cluster.name>"))
                                             .build())
                                  .build();
    ManifestOutcome manifestOutcome = K8sManifestOutcome.builder().store(githubStore).build();

    GitopsClustersOutcome.ClusterData cluster1 = GitopsClustersOutcome.ClusterData.builder()
                                                     .envId("IDENTIFIER")
                                                     .envName("ENV_NAME")
                                                     .envGroupId("ENVGROUP_ID")
                                                     .envGroupName("ENV_GROUP")
                                                     .clusterName("CLUSTER_NAME")
                                                     .clusterId("CLUSTER_ID")
                                                     .scope("SCOPE")
                                                     .variables(new HashMap<>())
                                                     .build();

    GitopsClustersOutcome.ClusterData cluster2 = GitopsClustersOutcome.ClusterData.builder()
                                                     .envId("IDENTIFIER2")
                                                     .envName("ENV_NAME2")
                                                     .envGroupId("ENVGROUP_ID")
                                                     .envGroupName("ENV_GROUP")
                                                     .clusterName("CLUSTER_NAME2")
                                                     .clusterId("CLUSTER_ID2")
                                                     .scope("SCOPE2")
                                                     .variables(new HashMap<>())
                                                     .build();

    List<GitopsClustersOutcome.ClusterData> clusterDataList = List.of(cluster1, cluster2);

    GitopsClustersOutcome gitopsClustersOutcome = new GitopsClustersOutcome(clusterDataList);
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(gitopsClustersOutcome).found(true).build();
    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(), eq(RefObjectUtils.getOutcomeRefObject(GITOPS_SWEEPING_OUTPUT)));

    Map<String, Map<String, String>> map = step.buildFilePathsToVariablesMap(manifestOutcome, ambiance, variables);
    Map<String, String> fileVariables = map.get("FILE_PATH/ENV_GROUP/ENV_NAME/CLUSTER_NAME");
    assertThat(fileVariables).isNotNull();
    assertThat(fileVariables.get("config1")).isEqualTo("VALUE1");
    assertThat(fileVariables.get("configNum")).isEqualTo("3");
    assertThat(fileVariables.get("configFloat")).isEqualTo("3.14");
    assertThat(fileVariables.get("configNumFail")).isEqualTo("3a.0");
    assertThat(fileVariables.get("config2")).isEqualTo("CLUSTER_NAME");
    assertThat(fileVariables.get("config3")).isEqualTo("ENV_NAME");
    assertThat(fileVariables.get("config5")).isEqualTo("ENV_GROUP");
    Map<String, String> fileVariables2 = map.get("FILE_PATH/ENV_GROUP/ENV_NAME2/CLUSTER_NAME2");
    assertThat(fileVariables2).isNotNull();
    assertThat(fileVariables2.get("config1")).isEqualTo("VALUE1");
    assertThat(fileVariables2.get("config2")).isEqualTo("CLUSTER_NAME2");
    assertThat(fileVariables2.get("config3")).isEqualTo("ENV_NAME2");
    assertThat(fileVariables2.get("config5")).isEqualTo("ENV_GROUP");
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testBuildFilePathsToVariablesMapForClusterVar() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    doAnswer(invocation -> invocation.getArgument(1))
        .when(engineExpressionService)
        .renderExpression(eq(ambiance), any(), any());
    Map<String, Object> variables = new HashMap<>();
    variables.put("config1", ParameterField.builder().value("VALUE1").build());
    variables.put("config2", "VALUE2");
    variables.put("secret", "${ngSecretManager.obtain(\\\"serviceSecret\\\", 166585618)}");
    GithubStore githubStore = GithubStore.builder()
                                  .paths(ParameterField.<List<String>>builder()
                                             .value(List.of("FILE_PATH/<+envgroup.name>/<+env.name>/<+cluster.name>"))
                                             .build())
                                  .build();
    ManifestOutcome manifestOutcome = K8sManifestOutcome.builder().store(githubStore).build();

    GitopsClustersOutcome.ClusterData cluster1 = GitopsClustersOutcome.ClusterData.builder()
                                                     .envId("IDENTIFIER")
                                                     .envName("ENV_NAME")
                                                     .envGroupId("ENVGROUP_ID")
                                                     .envGroupName("ENV_GROUP")
                                                     .clusterName("CLUSTER_NAME")
                                                     .clusterId("CLUSTER_ID")
                                                     .scope("SCOPE")
                                                     .variables(variables)
                                                     .build();

    List<GitopsClustersOutcome.ClusterData> clusterDataList = List.of(cluster1);

    GitopsClustersOutcome gitopsClustersOutcome = new GitopsClustersOutcome(clusterDataList);
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(gitopsClustersOutcome).found(true).build();
    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(), eq(RefObjectUtils.getOutcomeRefObject(GITOPS_SWEEPING_OUTPUT)));

    Map<String, Map<String, String>> map =
        step.buildFilePathsToVariablesMap(manifestOutcome, ambiance, new HashMap<>());
    Map<String, String> fileVariables = map.get("FILE_PATH/ENV_GROUP/ENV_NAME/CLUSTER_NAME");
    assertThat(fileVariables).isNotNull();
    assertThat(fileVariables.get("config1")).isEqualTo("VALUE1");
    assertThat(fileVariables.get("config2")).isEqualTo("VALUE2");
    assertThat(fileVariables.containsKey("secret")).isEqualTo(false);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testBuildFilePathsToVariablesMapForEmptyVar() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    doAnswer(invocation -> invocation.getArgument(1))
        .when(engineExpressionService)
        .renderExpression(eq(ambiance), any(), any());
    Map<String, Object> variables = new HashMap<>();
    variables.put("config1", ParameterField.builder().value("null").build());
    variables.put("config2", ParameterField.builder().value("").build());
    GithubStore githubStore = GithubStore.builder()
                                  .paths(ParameterField.<List<String>>builder()
                                             .value(List.of("FILE_PATH/<+envgroup.name>/<+env.name>/<+cluster.name>"))
                                             .build())
                                  .build();
    ManifestOutcome manifestOutcome = K8sManifestOutcome.builder().store(githubStore).build();

    GitopsClustersOutcome.ClusterData cluster1 = GitopsClustersOutcome.ClusterData.builder()
                                                     .envId("IDENTIFIER")
                                                     .envName("ENV_NAME")
                                                     .envGroupId("ENVGROUP_ID")
                                                     .envGroupName("ENV_GROUP")
                                                     .clusterName("CLUSTER_NAME")
                                                     .clusterId("CLUSTER_ID")
                                                     .scope("SCOPE")
                                                     .variables(variables)
                                                     .build();

    List<GitopsClustersOutcome.ClusterData> clusterDataList = List.of(cluster1);

    GitopsClustersOutcome gitopsClustersOutcome = new GitopsClustersOutcome(clusterDataList);
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(gitopsClustersOutcome).found(true).build();
    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(), eq(RefObjectUtils.getOutcomeRefObject(GITOPS_SWEEPING_OUTPUT)));

    Map<String, Object> variables2 = new HashMap<>();
    variables2.put("config3", ParameterField.builder().value("null").build());
    variables2.put("config4", ParameterField.builder().value("").build());

    Map<String, Map<String, String>> map = step.buildFilePathsToVariablesMap(manifestOutcome, ambiance, variables2);
    Map<String, String> fileVariables = map.get("FILE_PATH/ENV_GROUP/ENV_NAME/CLUSTER_NAME");
    assertThat(fileVariables).isNotNull();

    // validating cluster variables
    assertThat(fileVariables.get("config1")).isEqualTo(null);
    assertThat(fileVariables.get("config2")).isEqualTo(null);

    // validating variables from spec parameters
    assertThat(fileVariables.get("config3")).isEqualTo(null);
    assertThat(fileVariables.get("config4")).isEqualTo(null);
  }
}
