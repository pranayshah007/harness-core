/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.filters;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceYaml;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.filters.GenericStageFilterJsonCreatorV2;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.cdng.sample.cd.creator.filters.CdFilter;
import io.harness.pms.cdng.sample.cd.creator.filters.CdFilter.CdFilterBuilder;
import io.harness.pms.exception.runtime.InvalidYamlRuntimeException;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.CDC)
public class DeploymentStageFilterJsonCreatorV2 extends GenericStageFilterJsonCreatorV2<DeploymentStageNode> {
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private EnvironmentService environmentService;
  @Inject private InfrastructureEntityService infraService;

  @Override
  public Set<String> getSupportedStageTypes() {
    return Collections.singleton("Deployment");
  }

  @Override
  public Class<DeploymentStageNode> getFieldClass() {
    return DeploymentStageNode.class;
  }

  @Override
  public PipelineFilter getFilter(FilterCreationContext filterCreationContext, DeploymentStageNode yamlField) {
    CdFilterBuilder filterBuilder = CdFilter.builder();

    final DeploymentStageConfig deploymentStageConfig = yamlField.getDeploymentStageConfig();

    addServiceFilters(filterCreationContext, filterBuilder, deploymentStageConfig);
    addInfraFilters(filterCreationContext, filterBuilder, deploymentStageConfig);

    return filterBuilder.build();
  }

  private void addServiceFilters(FilterCreationContext filterCreationContext, CdFilterBuilder filterBuilder,
      DeploymentStageConfig deploymentStageConfig) {
    if (deploymentStageConfig.getServiceConfig() != null) {
      addFiltersFromServiceConfig(filterCreationContext, filterBuilder, deploymentStageConfig.getServiceConfig());
    } else if (deploymentStageConfig.getService() != null) {
      addFiltersFromServiceV2(filterCreationContext, filterBuilder, deploymentStageConfig.getService(),
          deploymentStageConfig.getDeploymentType());
    } else {
      throw new InvalidYamlRuntimeException(
          format("serviceConfig or Service should be present in stage [%s]. Please add it and try again",
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }
  }

  private void addInfraFilters(FilterCreationContext filterCreationContext, CdFilterBuilder filterBuilder,
      DeploymentStageConfig deploymentStageConfig) {
    if (deploymentStageConfig.getInfrastructure() != null) {
      addFiltersFromPipelineInfra(filterCreationContext, filterBuilder, deploymentStageConfig.getInfrastructure());
    } else if (deploymentStageConfig.getEnvironment() != null) {
      addFiltersFromEnvironment(filterCreationContext, filterBuilder, deploymentStageConfig.getEnvironment());
    } else if (deploymentStageConfig.getEnvironmentGroup() != null) {
      addFiltersFromEnvironmentGroup(filterCreationContext, filterBuilder, deploymentStageConfig.getEnvironmentGroup());
    } else {
      throw new InvalidYamlRuntimeException(format(
          "Infrastructure or Environment or EnvironmentGroup should be present in stage [%s]. Please add it and try again",
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }
  }

  private void addFiltersFromEnvironment(
      FilterCreationContext filterCreationContext, CdFilterBuilder filterBuilder, EnvironmentYamlV2 env) {
    final ParameterField<String> environmentRef = env.getEnvironmentRef();
    if (environmentRef == null || environmentRef.fetchFinalValue() == null) {
      throw new InvalidYamlRuntimeException(
          format("environmentRef should be present in stage [%s]. Please add it and try again",
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }

    if (!environmentRef.isExpression()) {
      Optional<Environment> environmentEntityOptional = environmentService.get(
          filterCreationContext.getSetupMetadata().getAccountId(), filterCreationContext.getSetupMetadata().getOrgId(),
          filterCreationContext.getSetupMetadata().getProjectId(), environmentRef.getValue(), false);
      if (environmentEntityOptional.isPresent()) {
        final Environment entity = environmentEntityOptional.get();
        filterBuilder.environmentName(entity.getName());

        List<InfraStructureDefinitionYaml> infraList = env.getInfrastructureDefinitions();
        if (isNotEmpty(infraList)) {
          if (infraList.size() > 1) {
            throw new InvalidYamlRuntimeException(format(
                "multiple infra deployments are not supported yet in stage [%s]. Please select 1 infrastructure and try again",
                YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
          }
          Optional<InfrastructureEntity> infrastructureEntity =
              infraService.get(filterCreationContext.getSetupMetadata().getAccountId(),
                  filterCreationContext.getSetupMetadata().getOrgId(),
                  filterCreationContext.getSetupMetadata().getProjectId(), entity.getIdentifier(),
                  infraList.get(0).getRef().getValue());
          infrastructureEntity.ifPresent(
              ie -> filterBuilder.infrastructureType(infrastructureEntity.get().getType().getDisplayName()));
        }
      }
    }
  }

  private void addFiltersFromEnvironmentGroup(
      FilterCreationContext filterCreationContext, CdFilterBuilder filterBuilder, EnvironmentGroupYaml envGroupYaml) {
    final ParameterField<String> envGroupRef = envGroupYaml.getEnvGroupRef();
    if (envGroupRef == null || envGroupRef.fetchFinalValue() == null) {
      throw new InvalidYamlRuntimeException(
          format("envGroupRef should be present in stage [%s]. Please add it and try again",
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }
  }

  private void addFiltersFromServiceV2(FilterCreationContext filterCreationContext, CdFilterBuilder filterBuilder,
      ServiceYamlV2 service, ServiceDefinitionType deploymentType) {
    final ParameterField<String> serviceEntityRef = service.getServiceRef();
    if (serviceEntityRef == null || serviceEntityRef.fetchFinalValue() == null) {
      throw new InvalidYamlRuntimeException(format(
          "serviceConfigRef should be present in stage [%s] when referring to a service entity. Please add it and try again",
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }

    if (!serviceEntityRef.isExpression()) {
      Optional<ServiceEntity> serviceEntityOptional = serviceEntityService.get(
          filterCreationContext.getSetupMetadata().getAccountId(), filterCreationContext.getSetupMetadata().getOrgId(),
          filterCreationContext.getSetupMetadata().getProjectId(), serviceEntityRef.getValue(), false);
      serviceEntityOptional.ifPresent(se -> {
        NGServiceV2InfoConfig config = NGServiceEntityMapper.toNGServiceConfig(se).getNgServiceV2InfoConfig();
        filterBuilder.serviceName(se.getName());
        if (config.getServiceDefinition() == null) {
          throw new InvalidYamlRuntimeException(
              format("ServiceDefinition should be present in service [%s]. Please add it and try again", se.getName()));
        }
        if (config.getServiceDefinition().getType() != deploymentType) {
          throw new InvalidYamlRuntimeException(format(
              "deploymentType should be the same as in service [%s]. Please correct it and try again", se.getName()));
        }
        filterBuilder.deploymentType(config.getServiceDefinition().getType().getYamlName());
      });
    }
  }

  private void addFiltersFromServiceConfig(
      FilterCreationContext filterCreationContext, CdFilterBuilder cdFilter, ServiceConfig serviceConfig) {
    ServiceYaml service = serviceConfig.getService();
    if (service == null
        && (serviceConfig.getServiceRef() == null || serviceConfig.getServiceRef().fetchFinalValue() == null)
        && serviceConfig.getUseFromStage() == null) {
      throw new InvalidYamlRuntimeException(format(
          "One of service, serviceRef and useFromStage should be present in stage [%s]. Please add it and try again",
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }
    if (service != null && isNotEmpty(service.getName())) {
      cdFilter.serviceName(service.getName());
    }

    ParameterField<String> serviceRef = serviceConfig.getServiceRef();
    if (serviceRef != null && !serviceRef.isExpression()) {
      Optional<ServiceEntity> serviceEntityOptional = serviceEntityService.get(
          filterCreationContext.getSetupMetadata().getAccountId(), filterCreationContext.getSetupMetadata().getOrgId(),
          filterCreationContext.getSetupMetadata().getProjectId(), serviceRef.getValue(), false);
      serviceEntityOptional.ifPresent(serviceEntity -> cdFilter.serviceName(serviceEntity.getName()));
    }

    ServiceDefinition serviceDefinition = serviceConfig.getServiceDefinition();
    if (serviceDefinition != null && serviceDefinition.getType() != null) {
      cdFilter.deploymentType(serviceDefinition.getType().getYamlName());
    }
  }

  private void addFiltersFromPipelineInfra(
      FilterCreationContext filterCreationContext, CdFilterBuilder cdFilter, PipelineInfrastructure infrastructure) {
    if (infrastructure == null) {
      throw new InvalidYamlRuntimeException(
          format("Infrastructure cannot be null in stage [%s]. Please add it and try again",
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }
    if (infrastructure.getEnvironment() == null
        && (infrastructure.getEnvironmentRef() == null || infrastructure.getEnvironmentRef().fetchFinalValue() == null)
        && infrastructure.getUseFromStage() == null) {
      throw new InvalidYamlRuntimeException(format(
          "One of environment, environment and useFromStage should be present in stage [%s]. Please add it and try again",
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }

    if (infrastructure.getEnvironment() != null && isNotEmpty(infrastructure.getEnvironment().getName())) {
      cdFilter.environmentName(infrastructure.getEnvironment().getName());
    }

    ParameterField<String> environmentRef = infrastructure.getEnvironmentRef();
    if (environmentRef != null && !environmentRef.isExpression()) {
      Optional<Environment> environmentEntityOptional = environmentService.get(
          filterCreationContext.getSetupMetadata().getAccountId(), filterCreationContext.getSetupMetadata().getOrgId(),
          filterCreationContext.getSetupMetadata().getProjectId(), environmentRef.getValue(), false);
      environmentEntityOptional.ifPresent(environment -> cdFilter.environmentName(environment.getName()));
    }

    if (infrastructure.getInfrastructureDefinition() != null
        && isNotEmpty(infrastructure.getInfrastructureDefinition().getType().getDisplayName())) {
      cdFilter.infrastructureType(infrastructure.getInfrastructureDefinition().getType().getDisplayName());
    }
  }

  @Override
  @NotNull
  protected Map<String, YamlField> getDependencies(YamlField stageField) {
    // Add dependency for rollback steps
    Map<String, YamlField> dependencies = new HashMap<>(super.getDependencies(stageField));
    YamlField pipelineInfraField = stageField.getNode()
                                       .getField(YAMLFieldNameConstants.SPEC)
                                       .getNode()
                                       .getField(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE);
    if (pipelineInfraField != null) {
      YamlField provisionerField = pipelineInfraField.getNode()
                                       .getField("infrastructureDefinition")
                                       .getNode()
                                       .getField(YAMLFieldNameConstants.PROVISIONER);

      if (provisionerField != null) {
        YamlField stepsField = provisionerField.getNode().getField("steps");
        if (stepsField != null && stepsField.getNode().asArray().size() != 0) {
          addRollbackDependencies(dependencies, stepsField);
        }
      }
    }
    YamlField executionField =
        stageField.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YAMLFieldNameConstants.EXECUTION);
    YamlField rollbackStepsField = executionField.getNode().getField(YAMLFieldNameConstants.ROLLBACK_STEPS);
    if (rollbackStepsField != null && rollbackStepsField.getNode().asArray().size() != 0) {
      addRollbackDependencies(dependencies, rollbackStepsField);
    }
    return dependencies;
  }

  private void addRollbackDependencies(Map<String, YamlField> dependencies, YamlField rollbackStepsField) {
    List<YamlField> stepYamlFields = PlanCreatorUtils.getStepYamlFields(rollbackStepsField.getNode().asArray());
    for (YamlField stepYamlField : stepYamlFields) {
      dependencies.put(stepYamlField.getNode().getUuid(), stepYamlField);
    }
  }
}
