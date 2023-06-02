/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.gitops;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig;
import io.harness.cdng.environment.bean.IndividualEnvData;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentsPlanCreatorConfig;
import io.harness.cdng.gitops.steps.ClusterStepParameters;
import io.harness.cdng.gitops.steps.ClusterStepParameters.ClusterStepParametersBuilder;
import io.harness.cdng.gitops.steps.EnvClusterRefs;
import io.harness.cdng.gitops.steps.GitopsClustersStep;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.yaml.ParameterField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.GITOPS)
@UtilityClass
public class ClusterPlanCreatorUtils {
  public PlanNodeBuilder getGitopsClustersStepPlanNodeBuilder(String nodeUuid, EnvironmentPlanCreatorConfig envConfig) {
    return PlanNode.builder()
        .uuid(nodeUuid)
        .name(PlanCreatorConstants.GITOPS_INFRA_NODE_NAME)
        .identifier(PlanCreatorConstants.GITOPS_INFRA_NODE_NAME)
        .stepType(GitopsClustersStep.STEP_TYPE)
        .stepParameters(getStepParams(envConfig))
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                .build());
  }

  public PlanNodeBuilder getGitopsClustersStepPlanNodeBuilder(
      String nodeUuid, EnvGroupPlanCreatorConfig envGroupConfig) {
    return PlanNode.builder()
        .uuid(nodeUuid)
        .name(PlanCreatorConstants.GITOPS_INFRA_NODE_NAME)
        .identifier(PlanCreatorConstants.GITOPS_INFRA_NODE_NAME)
        .stepType(GitopsClustersStep.STEP_TYPE)
        .stepParameters(getStepParams(envGroupConfig))
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                .build());
  }

  public PlanNodeBuilder getGitopsClustersStepPlanNodeBuilder(
      String nodeUuid, EnvironmentsPlanCreatorConfig environmentsPlanCreatorConfig) {
    return PlanNode.builder()
        .uuid(nodeUuid)
        .name(PlanCreatorConstants.GITOPS_INFRA_NODE_NAME)
        .identifier(PlanCreatorConstants.GITOPS_INFRA_NODE_NAME)
        .stepType(GitopsClustersStep.STEP_TYPE)
        .stepParameters(getStepParams(environmentsPlanCreatorConfig))
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                .build());
  }

  private ClusterStepParameters getStepParams(EnvironmentPlanCreatorConfig envConfig) {
    checkNotNull(envConfig, "environment must be present");

    final String envRef = fetchEnvRef(envConfig);
    if (envConfig.isDeployToAll()) {
      return ClusterStepParameters.builder()
          .envClusterRefs(List.of(EnvClusterRefs.builder()
                                      .envRef(envRef)
                                      .envName(envConfig.getName())
                                      .envType(envConfig.getType() != null ? envConfig.getType().toString() : null)
                                      .deployToAll(true)
                                      .build()))
          .build();
    }

    checkArgument(isNotEmpty(envConfig.getGitOpsClusterRefs()),
        "list of gitops clusterRefs must be provided when not deploying to all clusters");

    return ClusterStepParameters.builder()
        .envClusterRefs(
            Collections.singletonList(EnvClusterRefs.builder()
                                          .envRef(envRef)
                                          .envName(envConfig.getName())
                                          .envType(envConfig.getType() != null ? envConfig.getType().toString() : null)
                                          .clusterRefs(getClusterRefs(envConfig))
                                          .build()))
        .build();
  }

  private ClusterStepParameters getStepParams(EnvGroupPlanCreatorConfig config) {
    checkNotNull(config, "environment group must be present");

    ClusterStepParametersBuilder clusterStepParametersBuilder =
        ClusterStepParameters.builder()
            .envGroupRef(config.getEnvironmentGroupRef().getValue())
            .envGroupName(config.getName())
            .deployToAllEnvs(false)
            .environmentGroupYaml(config.getEnvironmentGroupYaml());

    if (config.isDeployToAll()) {
      clusterStepParametersBuilder.deployToAllEnvs(true);
    }

    checkArgument(isNotEmpty(config.getEnvironmentPlanCreatorConfigs()) || config.getEnvironmentGroupYaml() != null,
        "list of environments must be provided when not deploying to all clusters");

    // Deploy to filtered list
    final List<EnvClusterRefs> clusterRefs =
        config.getEnvironmentPlanCreatorConfigs()
            .stream()
            .map(c
                -> EnvClusterRefs.builder()
                       .envRef(c.getEnvironmentRef().getValue())
                       .envName(c.getName())
                       .envType(c.getType() != null ? c.getType().toString() : null)
                       .deployToAll(c.isDeployToAll())
                       .clusterRefs(c.isDeployToAll() ? null : ClusterPlanCreatorUtils.getClusterRefs(c))
                       .build())
            .collect(Collectors.toList());

    return clusterStepParametersBuilder.envClusterRefs(clusterRefs).build();
  }

  private ClusterStepParameters getStepParams(EnvironmentsPlanCreatorConfig envConfig) {
    checkNotNull(envConfig, "environments must be present");

    ClusterStepParametersBuilder clusterStepParametersBuilder = ClusterStepParameters.builder();
    List<EnvClusterRefs> envClusterRefList = new ArrayList<>();

    for (IndividualEnvData envData : envConfig.getIndividualEnvDataList()) {
      EnvClusterRefs envClusterRefs = EnvClusterRefs.builder()
                                          .envName(envData.getEnvName())
                                          .envRef(envData.getEnvRef())
                                          .envType(envData.getType())
                                          .clusterRefs(envData.getGitOpsClusterRefs())
                                          .deployToAll(envData.isDeployToAll())
                                          .build();

      envClusterRefList.add(envClusterRefs);
    }

    return clusterStepParametersBuilder.envClusterRefs(envClusterRefList)
        .environmentsYaml(envConfig.getEnvironmentsYaml())
        .build();
  }

  private Set<String> getClusterRefs(EnvironmentPlanCreatorConfig config) {
    return new HashSet<>(config.getGitOpsClusterRefs());
  }

  private String fetchEnvRef(EnvironmentPlanCreatorConfig config) {
    final ParameterField<String> environmentRef = config.getEnvironmentRef();
    checkNotNull(environmentRef, "environment ref must be present");
    checkArgument(!environmentRef.isExpression(), "environment ref not resolved yet");
    return (String) environmentRef.fetchFinalValue();
  }
}
