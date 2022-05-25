/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.environment;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.environment.helper.EnvironmentPlanCreatorConfigMapper;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.infra.steps.EnvironmentStep;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.YamlPipelineUtils;

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
public class EnvironmentPlanCreatorHelper {
  public PlanNode getPlanNode(
      String envNodeUuid, StepParameters infraSectionStepParameters, ByteString advisorParameters) {
    return PlanNode.builder()
        .uuid(envNodeUuid)
        .stepType(EnvironmentStep.STEP_TYPE)
        .name(PlanCreatorConstants.ENVIRONMENT_NODE_NAME)
        .identifier(YamlTypes.ENVIRONMENT_YAML)
        .stepParameters(infraSectionStepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                .build())
        .adviserObtainment(
            AdviserObtainment.newBuilder()
                .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                .setParameters(advisorParameters)
                .build())
        .skipExpressionChain(false)
        .build();
  }

  // TODO: currently this function do not handle runtime inputs value in Environment and Infrastructure Entities. Need
  // to handle this in future
  public EnvironmentPlanCreatorConfig getResolvedEnvRefs(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, EnvironmentYamlV2 environmentV2, boolean gitOpsEnabled,
      EnvironmentService environmentService, InfrastructureEntityService infrastructure) {
    // TODO: check the case when its a runtime value if its possible for it to have here
    Optional<Environment> environment = environmentService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentV2.getEnvironmentRef().getValue(), false);

    String envIdentifier = environmentV2.getEnvironmentRef().getValue();
    if (!environment.isPresent()) {
      throw new InvalidRequestException(
          String.format("No environment found with %s identifier in %s project in %s org and %s account", envIdentifier,
              projectIdentifier, orgIdentifier, accountIdentifier));
    }

    if (!gitOpsEnabled) {
      List<InfrastructureEntity> infrastructureEntityList = getInfraStructureEntityList(
          accountIdentifier, orgIdentifier, projectIdentifier, environmentV2, infrastructure);
      return EnvironmentPlanCreatorConfigMapper.toEnvironmentPlanCreatorConfig(
          environment.get(), infrastructureEntityList);
    }
    // TODO: need to handle gitOps cluster
    throw new InvalidRequestException(
        String.format("Environment with id %s does not exists or has been deleted", envIdentifier));
  }

  private List<InfrastructureEntity> getInfraStructureEntityList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, EnvironmentYamlV2 environmentV2, InfrastructureEntityService infrastructure) {
    List<InfrastructureEntity> infrastructureEntityList = new ArrayList<>();
    String envIdentifier = environmentV2.getEnvironmentRef().getValue();
    if (!environmentV2.getDeployToAll()) {
      List<String> infraIdentifierList =
          environmentV2.getInfrastructureDefinitions()
              .stream()
              .map(infraStructureDefinitionYaml -> infraStructureDefinitionYaml.getRef().getValue())
              .collect(Collectors.toList());
      infrastructureEntityList = infrastructure.getAllInfrastructureFromIdentifierList(
          accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifierList);
    } else {
      if (isNotEmpty(environmentV2.getInfrastructureDefinitions())) {
        throw new InvalidRequestException(String.format("DeployToAll is enabled along with specific Infrastructures %s",
            environmentV2.getInfrastructureDefinitions()));
      }
      infrastructureEntityList = infrastructure.getAllInfrastructureFromEnvIdentifier(
          accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier);
    }
    return infrastructureEntityList;
  }

  public YamlField fetchEnvironmentPlanCreatorConfigYaml(
      EnvironmentPlanCreatorConfig environmentPlanCreatorConfig, YamlField originalEnvironmentField) {
    try {
      String yamlString = YamlPipelineUtils.getYamlString(environmentPlanCreatorConfig);
      YamlField yamlField = YamlUtils.injectUuidInYamlField(yamlString);
      return new YamlField(YamlTypes.ENVIRONMENT_YAML,
          new YamlNode(YamlTypes.ENVIRONMENT_YAML, yamlField.getNode().getCurrJsonNode(),
              originalEnvironmentField.getNode().getParentNode()));
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid environment yaml", e);
    }
  }
}
