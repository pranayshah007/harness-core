/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.gitops;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.gitops.steps.ClusterStepParameters;
import io.harness.cdng.gitops.steps.EnvClusterRefs;
import io.harness.cdng.gitops.steps.GitopsClustersStep;
import io.harness.cdng.gitops.steps.Metadata;
import io.harness.cdng.gitops.yaml.ClusterYaml;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

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

  public PlanNode getGitOpsClustersStepPlanNode(EnvironmentYamlV2 envYaml) {
    String uuid = "gitOpsClusters" + UUIDGenerator.generateUuid();
    return PlanNode.builder()
        .uuid(uuid)
        .name(PlanCreatorConstants.GITOPS_INFRA_NODE_NAME)
        .identifier(PlanCreatorConstants.GITOPS_INFRA_NODE_NAME)
        .stepType(GitopsClustersStep.STEP_TYPE)
        .stepParameters(getStepParams(envYaml))
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                .build())
        .build();
  }

  private static ClusterStepParameters getStepParams(EnvironmentYamlV2 envYaml) {
    ParameterField<Boolean> deployToAll = envYaml.getDeployToAll();
    if (deployToAll.isExpression()) {
     throw new InvalidRequestException(String.format("Deploy to all [%s] cannot be resolved", envYaml.getDeployToAll().getExpressionValue());
    }
    if (deployToAll.getValue()) {
      return ClusterStepParameters.builder()
          .envClusterRef(EnvClusterRefs.builder()
                             .envRef((String) envYaml.getEnvironmentRef().fetchFinalValue())
                             .deployToAll(true)
                             .build())
          .build();
    }

    if (ParameterField.isNull(envYaml.getGitOpsClusters())) {
      throw new InvalidRequestException(
          String.format("gitOpsClusters [%s] cannot be resolved", envYaml.getGitOpsClusters().getExpressionValue()));
    }

    checkArgument(isNotEmpty(envYaml.getGitOpsClusters().getValue()),
        "list of gitops clusterRefs must be provided when not deploying to all clusters");

    return ClusterStepParameters.builder()
        .envClusterRefs(Collections.singletonList(EnvClusterRefs.builder()
                                                      .envRef((String) envYaml.getEnvironmentRef().fetchFinalValue())
                                                      /*.envName(envConfig.getName())*/
                                                      .clusterRefs(envYaml.getGitOpsClusters()
                                                                       .getValue()
                                                                       .stream()
                                                                       .map(ClusterYaml::getIdentifier)
                                                                       .map(ParameterField::fetchFinalValue)
                                                                       .map(String.class ::cast)
                                                                       .collect(Collectors.toSet()))
                                                      .build()))
        .build();
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

  private ClusterStepParameters getStepParams(EnvironmentPlanCreatorConfig envConfig) {
    checkNotNull(envConfig, "environment must be present");

    final String envRef = fetchEnvRef(envConfig);
    if (envConfig.isDeployToAll()) {
      return ClusterStepParameters.builder()
          .envClusterRefs(
              asList(EnvClusterRefs.builder().envRef(envRef).envName(envConfig.getName()).deployToAll(true).build()))
          .build();
    }

    checkArgument(isNotEmpty(envConfig.getGitOpsClusterRefs()),
        "list of gitops clusterRefs must be provided when not deploying to all clusters");

    return ClusterStepParameters.builder()
        .envClusterRefs(Collections.singletonList(EnvClusterRefs.builder()
                                                      .envRef(envRef)
                                                      .envName(envConfig.getName())
                                                      .clusterRefs(getClusterRefs(envConfig))
                                                      .build()))
        .build();
  }

  private ClusterStepParameters getStepParams(EnvGroupPlanCreatorConfig config) {
    checkNotNull(config, "environment group must be present");

    if (config.isDeployToAll()) {
      return ClusterStepParameters.WithEnvGroup(new Metadata(config.getIdentifier(), config.getName()));
    }

    checkArgument(isNotEmpty(config.getEnvironmentPlanCreatorConfigs()),
        "list of environments must be provided when not deploying to all clusters");

    final List<EnvClusterRefs> clusterRefs =
        config.getEnvironmentPlanCreatorConfigs()
            .stream()
            .map(c
                -> EnvClusterRefs.builder()
                       .envRef(c.getEnvironmentRef().getValue())
                       .envName(c.getName())
                       .deployToAll(c.isDeployToAll())
                       .clusterRefs(c.isDeployToAll() ? null : ClusterPlanCreatorUtils.getClusterRefs(c))
                       .build())
            .collect(Collectors.toList());

    return ClusterStepParameters.builder().envClusterRefs(clusterRefs).build();
  }

  private static StepParameters getStepParams(
      String envRef, String envName, List<String> gitOpsClusterRefs, boolean deployToAll) {
    if (deployToAll) {
      return ClusterStepParameters.builder()
          .envClusterRefs(
              // Todo: (yogesh) set env name here
              List.of(
                  EnvClusterRefs.builder().envRef(envRef)./*envName(envConfig.getName())*/ deployToAll(true).build()))
          .build();
    }

    checkArgument(isNotEmpty(gitOpsClusterRefs),
        "list of gitops clusterRefs must be provided when not deploying to all clusters");

    return ClusterStepParameters.builder()
        .envClusterRefs(
            Collections.singletonList(EnvClusterRefs.builder()
                                          .envRef(envRef)
                                          // Todo: (yogesh) set env name here
                                          /*                                      .envName(envConfig.getName())*/
                                          .clusterRefs(Set.of(gitOpsClusterRefs))
                                          .build()))
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
