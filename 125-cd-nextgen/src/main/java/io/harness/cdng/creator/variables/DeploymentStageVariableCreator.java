/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STRATEGY;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactSource;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifactWrapper;
import io.harness.cdng.artifact.mappers.ArtifactResponseToOutcomeMapper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.manifest.mappers.ManifestOutcomeMapper;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.gitx.GitXTransientBranchGuard;
import io.harness.ng.core.security.NgManagerSourcePrincipalGuard;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.mapper.NGServiceOverrideEntityConfigMapper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.AbstractStageVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class DeploymentStageVariableCreator extends AbstractStageVariableCreator<DeploymentStageNode> {
  private static final String SIDECARS_PREFIX = "artifacts.sidecars";
  private static final String PRIMARY = "primary";
  private static final String OVERRIDE_PROJECT_SETTING_IDENTIFIER = "service_override_v2";
  @Inject private ServiceEntityService serviceEntityService;

  @Inject private ServiceOverrideService serviceOverrideService;

  @Inject private ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;
  @Inject private ServiceOverridesServiceV2 serviceOverridesServiceV2;
  @Inject private StageVariableCreatorHelper stageVariableCreatorHelper;

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config) {
    LinkedHashMap<String, VariableCreationResponse> responseMap = new LinkedHashMap<>();
    YamlField serviceField =
        config.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YamlTypes.SERVICE_CONFIG);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(serviceField)) {
      VariableCreationResponse serviceVariableResponse = ServiceVariableCreator.createVariableResponse(serviceField);
      responseMap.put(serviceField.getNode().getUuid(), serviceVariableResponse);
    }

    YamlField infraField =
        config.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YamlTypes.PIPELINE_INFRASTRUCTURE);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(infraField)) {
      VariableCreationResponse infraVariableResponse = InfraVariableCreator.createVariableResponse(infraField);
      responseMap.put(infraField.getNode().getUuid(), infraVariableResponse);
    }

    YamlField executionField =
        config.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YAMLFieldNameConstants.EXECUTION);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(executionField)) {
      Map<String, YamlField> executionDependencyMap = new HashMap<>();
      executionDependencyMap.put(executionField.getNode().getUuid(), executionField);
      responseMap.put(executionField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(executionDependencyMap))
              .build());
    }

    YamlField strategyField = config.getNode().getField(STRATEGY);

    if (strategyField != null) {
      Map<String, YamlField> strategyDependencyMap = new HashMap<>();
      strategyDependencyMap.put(strategyField.getNode().getUuid(), strategyField);
      responseMap.put(strategyField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(strategyDependencyMap))
              .build());
    }

    return responseMap;
  }

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesV2(
      VariableCreationContext ctx, DeploymentStageNode config) {
    YamlField currentField = ctx.getCurrentField();

    // set source principal to get git entities
    SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.NG_MANAGER.getServiceId()));
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new ServicePrincipal(AuthorizationServiceHeader.NG_MANAGER.getServiceId()));

    LinkedHashMap<String, VariableCreationResponse> responseMap =
        createVariablesForChildrenNodesPipelineV2Yaml(ctx, config);
    // v1
    // add dependencies for provision node
    YamlField infraField = currentField.getNode()
                               .getField(YAMLFieldNameConstants.SPEC)
                               .getNode()
                               .getField(YamlTypes.PIPELINE_INFRASTRUCTURE);

    if (VariableCreatorHelper.isNotYamlFieldEmpty(infraField)) {
      Map<String, YamlField> infraDependencyMap = new LinkedHashMap<>();
      YamlField infraDefNode = infraField.getNode().getField(YamlTypes.INFRASTRUCTURE_DEF);
      if (VariableCreatorHelper.isNotYamlFieldEmpty(infraDefNode)
          && VariableCreatorHelper.isNotYamlFieldEmpty(infraDefNode.getNode().getField(YamlTypes.SPEC))) {
        YamlField provisionerField = infraDefNode.getNode().getField(YAMLFieldNameConstants.PROVISIONER);
        if (provisionerField != null) {
          infraDependencyMap.putAll(InfraVariableCreator.addDependencyForProvisionerSteps(provisionerField));
        }
      }
      responseMap.put(infraField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(infraDependencyMap))
              .build());
    }

    // add dependencies for execution node
    YamlField executionField = currentField.getNode()
                                   .getField(YAMLFieldNameConstants.SPEC)
                                   .getNode()
                                   .getField(YAMLFieldNameConstants.EXECUTION);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(executionField)) {
      Map<String, YamlField> executionDependencyMap = new HashMap<>();
      executionDependencyMap.put(executionField.getNode().getUuid(), executionField);
      responseMap.put(executionField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(executionDependencyMap))
              .build());
    }
    YamlField strategyField = currentField.getNode().getField(STRATEGY);

    if (strategyField != null) {
      Map<String, YamlField> strategyDependencyMap = new HashMap<>();
      strategyDependencyMap.put(strategyField.getNode().getUuid(), strategyField);
      responseMap.put(strategyField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(strategyDependencyMap))
              .build());
    }
    return responseMap;
  }

  private LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesPipelineV2Yaml(
      VariableCreationContext ctx, DeploymentStageNode config) {
    LinkedHashMap<String, VariableCreationResponse> responseMap = new LinkedHashMap<>();
    try (NgManagerSourcePrincipalGuard sourcePrincipalGuard = new NgManagerSourcePrincipalGuard()) {
      final ParameterField<String> serviceRef = getServiceRef(config);
      final ServicesYaml services = config.getDeploymentStageConfig().getServices();
      final EnvironmentsYaml environmentsYaml = config.getDeploymentStageConfig().getEnvironments();
      final EnvironmentYamlV2 environment = config.getDeploymentStageConfig().getEnvironment();
      final ParameterField<String> environmentRef = getEnvironmentRef(config);
      final String infraIdentifier = getInfraIdentifier(config);
      // for collecting service variables from service/env/service overrides
      Set<String> serviceVariables = new HashSet<>();

      if (environmentsYaml != null) {
        createVariablesForEnvironments(ctx, responseMap, environmentsYaml, serviceVariables);
      }
      if (environment != null) {
        stageVariableCreatorHelper.createVariablesForEnvironment(ctx, responseMap, serviceVariables, environment);
      }
      if (serviceRef != null) {
        try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(getServiceGitBranch(config))) {
          createVariablesForService(ctx, environmentRef, serviceRef, serviceVariables, responseMap, infraIdentifier);
        }
      }
      if (services != null) {
        createVariablesForServices(ctx, responseMap, services, environmentRef, serviceVariables, infraIdentifier);
      }
    } catch (Exception ex) {
      log.error("Exception during Deployment Stage Node variable creation", ex);
    }
    return responseMap;
  }

  private void createVariablesForServices(VariableCreationContext ctx,
      LinkedHashMap<String, VariableCreationResponse> responseMap, ServicesYaml services,
      ParameterField<String> environmentRef, Set<String> serviceVariables, String infraIdentifier) {
    if (!services.getValues().isExpression()) {
      for (ServiceYamlV2 serviceRefValue : services.getValues().getValue()) {
        try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(serviceRefValue.getGitBranch())) {
          createVariablesForService(
              ctx, environmentRef, serviceRefValue.getServiceRef(), serviceVariables, responseMap, infraIdentifier);
        }
      }
    }
  }

  private void createVariablesForEnvironments(VariableCreationContext ctx,
      LinkedHashMap<String, VariableCreationResponse> responseMap, EnvironmentsYaml environmentsYaml,
      Set<String> serviceVariables) {
    if (!environmentsYaml.getValues().isExpression()) {
      for (EnvironmentYamlV2 environmentYamlV2 : environmentsYaml.getValues().getValue()) {
        ParameterField<String> envRef = environmentYamlV2.getEnvironmentRef();
        if (envRef != null) {
          stageVariableCreatorHelper.createVariablesForEnvironment(
              ctx, responseMap, serviceVariables, environmentYamlV2);
        }
      }
    }
  }

  private void createVariablesForService(VariableCreationContext ctx, ParameterField<String> environmentRef,
      ParameterField<String> serviceRef, Set<String> serviceVariables,
      LinkedHashMap<String, VariableCreationResponse> responseMap, String infraIdentifier) {
    final String accountIdentifier = ctx.get(NGCommonEntityConstants.ACCOUNT_KEY);
    final String orgIdentifier = ctx.get(NGCommonEntityConstants.ORG_KEY);
    final String projectIdentifier = ctx.get(NGCommonEntityConstants.PROJECT_KEY);

    YamlField currentField = ctx.getCurrentField();
    List<YamlProperties> outputProperties = new LinkedList<>();
    Map<String, YamlExtraProperties> yamlPropertiesMap = new LinkedHashMap<>();

    YamlField specField = currentField.getNode().getField(YAMLFieldNameConstants.SPEC);
    // service node in v2 yaml
    YamlField serviceField = specField.getNode().getField(YamlTypes.SERVICE_ENTITY);
    if (serviceField == null) {
      serviceField = specField.getNode().getField(YamlTypes.SERVICE_ENTITIES);
    }

    if (isNotEmpty(serviceRef.getValue()) && !serviceRef.isExpression()) {
      outputProperties.addAll(handleServiceStepOutcome(serviceField));

      // scoped service ref used here
      Optional<ServiceEntity> optionalService = serviceEntityService.get(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceRef.getValue(), false, true, false);

      NGServiceConfig ngServiceConfig = null;
      if (optionalService.isPresent()) {
        ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(optionalService.get());
      }
      outputProperties.addAll(handleManifestProperties(specField, ngServiceConfig));
      outputProperties.addAll(handleArtifactProperties(specField, ngServiceConfig));
      if (optionalService.isPresent()) {
        handleServiceOverridesV2(ctx, environmentRef, serviceRef, serviceVariables, infraIdentifier, accountIdentifier,
            orgIdentifier, projectIdentifier, optionalService.get());
      }
      outputProperties.addAll(handleServiceVariables(specField, serviceVariables, ngServiceConfig));
    } else {
      outputProperties.addAll(handleServiceStepOutcome(serviceField));
      // handle serviceVariables from env
      outputProperties.addAll(handleServiceVariables(specField, serviceVariables, null));
    }
    yamlPropertiesMap.put(serviceField.getNode().getUuid(),
        YamlExtraProperties.newBuilder().addAllOutputProperties(outputProperties).build());
    responseMap.put(serviceField.getNode().getUuid(),
        VariableCreationResponse.builder().yamlExtraProperties(yamlPropertiesMap).build());
  }

  private void handleServiceOverridesV2(VariableCreationContext ctx, ParameterField<String> environmentRef,
      ParameterField<String> serviceRef, Set<String> serviceVariables, String infraIdentifier, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, ServiceEntity serviceEntity) {
    if (environmentRef != null && !environmentRef.isExpression()) {
      if (overrideV2ValidationHelper.isOverridesV2Enabled(accountIdentifier, orgIdentifier, projectIdentifier)) {
        Map<Scope, NGServiceOverridesEntity> envServiceOverride =
            serviceOverridesServiceV2.getEnvServiceOverride(accountIdentifier, orgIdentifier, projectIdentifier,
                environmentRef.getValue(), serviceRef.getValue(), null);
        addOverrideVariablesToSet(serviceVariables, envServiceOverride);
        if (isNotEmpty(infraIdentifier)) {
          Map<Scope, NGServiceOverridesEntity> infraServiceOverride =
              serviceOverridesServiceV2.getInfraServiceOverride(accountIdentifier, orgIdentifier, projectIdentifier,
                  environmentRef.getValue(), serviceRef.getValue(), infraIdentifier, null);
          addOverrideVariablesToSet(serviceVariables, infraServiceOverride);
        }
      } else {
        serviceVariables.addAll(getServiceOverridesVariables(ctx, environmentRef, serviceEntity));
      }
    }
  }

  private void addOverrideVariablesToSet(
      Set<String> serviceVariables, Map<Scope, NGServiceOverridesEntity> serviceOverride) {
    if (isNotEmpty(serviceOverride)) {
      List<NGServiceOverridesEntity> serviceOverridesEntities = new ArrayList<>(serviceOverride.values());
      serviceOverridesEntities.forEach(
          entity -> serviceVariables.addAll(NGVariablesUtils.getSetOfVars(entity.getSpec().getVariables())));
    }
  }

  private Set<String> getServiceOverridesVariables(
      VariableCreationContext ctx, ParameterField<String> environmentRef, ServiceEntity serviceEntity) {
    Set<String> variables = new HashSet<>();
    final String accountIdentifier = ctx.get(NGCommonEntityConstants.ACCOUNT_KEY);
    final String orgIdentifier = ctx.get(NGCommonEntityConstants.ORG_KEY);
    final String projectIdentifier = ctx.get(NGCommonEntityConstants.PROJECT_KEY);

    String scopedServiceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(serviceEntity.getAccountId(),
        serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier(), serviceEntity.getIdentifier());
    Optional<NGServiceOverridesEntity> serviceOverridesEntity = serviceOverrideService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentRef.getValue(), scopedServiceRef);
    if (serviceOverridesEntity.isPresent()) {
      NGServiceOverrideConfig serviceOverrideConfig =
          NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig(serviceOverridesEntity.get());
      List<NGVariable> variableOverrides = serviceOverrideConfig.getServiceOverrideInfoConfig().getVariables();
      if (EmptyPredicate.isNotEmpty(variableOverrides)) {
        variables.addAll(variableOverrides.stream()
                             .map(NGVariable::getName)
                             .filter(EmptyPredicate::isNotEmpty)
                             .collect(Collectors.toSet()));
      }
    }
    return variables;
  }

  private ParameterField<String> getServiceRef(DeploymentStageNode stageNode) {
    ServiceYamlV2 serviceYamlV2 = stageNode.getDeploymentStageConfig().getService();
    if (serviceYamlV2 != null) {
      return serviceYamlV2.getServiceRef();
    }
    return null;
  }

  private String getServiceGitBranch(DeploymentStageNode stageNode) {
    ServiceYamlV2 serviceYamlV2 = stageNode.getDeploymentStageConfig().getService();
    if (serviceYamlV2 != null) {
      return serviceYamlV2.getGitBranch();
    }
    return null;
  }

  private ParameterField<String> getEnvironmentRef(DeploymentStageNode stageNode) {
    EnvironmentYamlV2 environmentYamlV2 = stageNode.getDeploymentStageConfig().getEnvironment();
    if (environmentYamlV2 != null) {
      return environmentYamlV2.getEnvironmentRef();
    }
    return null;
  }

  private String getInfraIdentifier(DeploymentStageNode stageNode) {
    EnvironmentYamlV2 environmentYamlV2 = stageNode.getDeploymentStageConfig().getEnvironment();
    if (environmentYamlV2 != null) {
      ParameterField<InfraStructureDefinitionYaml> infraStructureDefinitionYaml =
          environmentYamlV2.getInfrastructureDefinition();
      if (infraStructureDefinitionYaml != null && infraStructureDefinitionYaml.getValue() != null) {
        ParameterField<String> infraId = infraStructureDefinitionYaml.getValue().getIdentifier();
        if (infraId != null && infraId.getValue() != null) {
          return infraId.getValue();
        }
      }

      ParameterField<List<InfraStructureDefinitionYaml>> infraStructureDefinitionYamlList =
          environmentYamlV2.getInfrastructureDefinitions();
      if (infraStructureDefinitionYamlList != null && infraStructureDefinitionYamlList.getValue() != null) {
        List<InfraStructureDefinitionYaml> infraStructureDefinitionYamls = infraStructureDefinitionYamlList.getValue();
        InfraStructureDefinitionYaml yaml = infraStructureDefinitionYamls.get(0);
        ParameterField<String> infraId = yaml.getIdentifier();
        if (infraId != null && infraId.getValue() != null) {
          return infraId.getValue();
        }
      }
    }
    return null;
  }

  private List<YamlProperties> handleServiceStepOutcome(YamlField serviceField) {
    List<YamlProperties> outputProperties = new ArrayList<>();
    String fqn = YamlUtils.getFullyQualifiedName(serviceField.getNode());
    ServiceStepOutcome serviceStepOutcome = ServiceStepOutcome.builder().build();
    // constance for service
    List<String> serviceStepOutputExpressions = VariableCreatorHelper.getExpressionsInObject(serviceStepOutcome, "");

    for (String outputExpression : serviceStepOutputExpressions) {
      outputProperties.add(YamlProperties.newBuilder()
                               .setFqn(fqn + "." + outputExpression)
                               .setLocalName("service"
                                   + "." + outputExpression)
                               .setVisible(true)
                               .build());
    }
    return outputProperties;
  }

  private List<YamlProperties> handleArtifactProperties(YamlField specField, NGServiceConfig ngServiceConfig) {
    final String stageFqn = YamlUtils.getFullyQualifiedName(specField.getNode());
    List<YamlProperties> outputProperties = new ArrayList<>();
    if (ngServiceConfig != null) {
      ArtifactListConfig artifactListConfig =
          ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec().getArtifacts();
      if (artifactListConfig != null) {
        List<SidecarArtifactWrapper> sidecarArtifactWrapperList = artifactListConfig.getSidecars();
        if (isNotEmpty(sidecarArtifactWrapperList)) {
          for (SidecarArtifactWrapper sideCarArtifact : sidecarArtifactWrapperList) {
            SidecarArtifact sidecar = sideCarArtifact.getSidecar();
            String identifier = sidecar.getIdentifier();
            if (sidecar.getSpec() != null) {
              ArtifactOutcome sideCarArtifactOutcome =
                  ArtifactResponseToOutcomeMapper.toArtifactOutcome(sidecar.getSpec(), null, false);
              List<String> sideCarOutputExpressions =
                  VariableCreatorHelper.getExpressionsInObject(sideCarArtifactOutcome, identifier);

              for (String outputExpression : sideCarOutputExpressions) {
                String localFqn = SIDECARS_PREFIX + "." + outputExpression;
                String fqn = stageFqn + "." + localFqn;
                outputProperties.add(
                    YamlProperties.newBuilder().setLocalName(localFqn).setFqn(fqn).setVisible(true).build());
              }
            }
          }
        }
        PrimaryArtifact primaryArtifact = artifactListConfig.getPrimary();
        if (primaryArtifact != null) {
          Set<String> expressions = new HashSet<>();
          if (primaryArtifact.getSpec() != null) {
            populateExpressionsForArtifact(specField, outputProperties, primaryArtifact.getSpec(), expressions);
          }
          if (ParameterField.isNotNull(primaryArtifact.getPrimaryArtifactRef())
              && isNotEmpty(primaryArtifact.getSources())) {
            // If primary artifact ref is fixed, use only that particular artifact source
            if (!primaryArtifact.getPrimaryArtifactRef().isExpression()) {
              Optional<ArtifactSource> source =
                  primaryArtifact.getSources()
                      .stream()
                      .filter(s -> primaryArtifact.getPrimaryArtifactRef().getValue().equals(s.getIdentifier()))
                      .findFirst();
              source.ifPresent(
                  s -> populateExpressionsForArtifact(specField, outputProperties, s.getSpec(), expressions));
            } else {
              primaryArtifact.getSources().forEach(
                  s -> populateExpressionsForArtifact(specField, outputProperties, s.getSpec(), expressions));
            }
          }
        }
      }
    }
    return outputProperties;
  }

  private void populateExpressionsForArtifact(
      YamlField specField, List<YamlProperties> outputProperties, ArtifactConfig spec, Set<String> expressions) {
    // in case of template source, spec will be null
    if (spec != null) {
      final String stageFqn = YamlUtils.getFullyQualifiedName(specField.getNode());
      ArtifactOutcome primaryArtifactOutcome = ArtifactResponseToOutcomeMapper.toArtifactOutcome(spec, null, false);
      List<String> primaryArtifactExpressions =
          VariableCreatorHelper.getExpressionsInObject(primaryArtifactOutcome, PRIMARY);

      for (String outputExpression : primaryArtifactExpressions) {
        if (expressions.add(outputExpression)) {
          String localName = OutcomeExpressionConstants.ARTIFACTS + "." + outputExpression;
          String fqn = stageFqn + "." + localName;
          outputProperties.add(
              YamlProperties.newBuilder().setLocalName(localName).setFqn(fqn).setVisible(true).build());
        }
      }
    }
  }

  private List<YamlProperties> handleManifestProperties(YamlField specField, NGServiceConfig ngServiceConfig) {
    final String stageFqn = YamlUtils.getFullyQualifiedName(specField.getNode());
    List<YamlProperties> outputProperties = new ArrayList<>();
    if (ngServiceConfig != null && ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition() != null) {
      List<ManifestConfigWrapper> manifestConfigWrappers =
          ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec().getManifests();

      if (isNotEmpty(manifestConfigWrappers)) {
        for (ManifestConfigWrapper manifestWrapper : manifestConfigWrappers) {
          ManifestConfig manifestConfig = manifestWrapper.getManifest();
          String identifier = manifestConfig.getIdentifier();

          ManifestOutcome outcome = ManifestOutcomeMapper.toManifestOutcome(manifestConfig.getSpec(), 0);

          List<String> manifestOutputExpressions = VariableCreatorHelper.getExpressionsInObject(outcome, identifier);

          for (String outputExpression : manifestOutputExpressions) {
            String localFqn = OutcomeExpressionConstants.MANIFESTS + "." + outputExpression;
            String fqn = stageFqn + "." + localFqn;
            outputProperties.add(
                YamlProperties.newBuilder().setLocalName(localFqn).setFqn(fqn).setVisible(true).build());
          }
        }
      }
    }
    return outputProperties;
  }

  private List<YamlProperties> handleServiceVariables(
      YamlField specField, Set<String> existingServiceVariables, NGServiceConfig ngServiceConfig) {
    final String stageFqn = YamlUtils.getFullyQualifiedName(specField.getNode());
    List<YamlProperties> outputProperties = new ArrayList<>();
    if (ngServiceConfig != null) {
      List<NGVariable> ngVariableList =
          ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec().getVariables();
      if (isNotEmpty(ngVariableList)) {
        existingServiceVariables.addAll(ngVariableList.stream().map(NGVariable::getName).collect(Collectors.toSet()));
      }
    }
    if (isNotEmpty(existingServiceVariables)) {
      List<String> outputExpressions = existingServiceVariables.stream()
                                           .map(entry -> YAMLFieldNameConstants.SERVICE_VARIABLES + '.' + entry)
                                           .collect(Collectors.toList());

      for (String outputExpression : outputExpressions) {
        String fqn = stageFqn + "." + outputExpression;
        outputProperties.add(
            YamlProperties.newBuilder().setLocalName(outputExpression).setFqn(fqn).setVisible(true).build());
      }
    }
    return outputProperties;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(StepSpecTypeConstants.DEPLOYMENT_STAGE));
  }

  @Override
  public Class<DeploymentStageNode> getFieldClass() {
    return DeploymentStageNode.class;
  }
}
