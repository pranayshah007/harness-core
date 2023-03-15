/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.arm;

import static io.harness.azure.model.AzureDeploymentMode.INCREMENTAL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.MigratorUtility.containsExpressions;
import static io.harness.ngmigration.utils.MigratorUtility.generateFileIdentifier;
import static io.harness.ngmigration.utils.MigratorUtility.getGitConnector;
import static io.harness.ngmigration.utils.MigratorUtility.getYamlConfigFile;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;
import static software.wings.ngmigration.NGMigrationEntityType.INFRA_PROVISIONER;

import io.harness.azure.model.ARMResourceType;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStore.GitStoreBuilder;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.provision.azure.AzureARMResourceDeploymentMode;
import io.harness.cdng.provision.azure.AzureBPScopes;
import io.harness.cdng.provision.azure.AzureCreateARMResourceParameterFile;
import io.harness.cdng.provision.azure.AzureCreateARMResourceStepConfiguration;
import io.harness.cdng.provision.azure.AzureCreateARMResourceStepInfo;
import io.harness.cdng.provision.azure.AzureCreateARMResourceStepNode;
import io.harness.cdng.provision.azure.AzureCreateARMResourceStepScope;
import io.harness.cdng.provision.azure.AzureCreateBPStepConfiguration;
import io.harness.cdng.provision.azure.AzureCreateBPStepInfo;
import io.harness.cdng.provision.azure.AzureCreateBPStepNode;
import io.harness.cdng.provision.azure.AzureManagementSpec;
import io.harness.cdng.provision.azure.AzureResourceGroupSpec;
import io.harness.cdng.provision.azure.AzureScopeTypesNames;
import io.harness.cdng.provision.azure.AzureSubscriptionSpec;
import io.harness.cdng.provision.azure.AzureTemplateFile;
import io.harness.cdng.provision.azure.AzureTenantSpec;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.ARMInfrastructureProvisioner;
import software.wings.beans.ARMSourceType;
import software.wings.beans.GitFileConfig;
import software.wings.beans.GraphNode;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.sm.State;
import software.wings.sm.states.provision.ARMProvisionState;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class AzureCreateARMResourceStepMapperImpl extends BaseAzureARMProvisionerMapper {
  public List<CgEntityId> getReferencedEntities(
      String accountId, GraphNode graphNode, Map<String, String> stepIdToServiceIdMap) {
    ARMProvisionState state = (ARMProvisionState) getState(graphNode);

    List<CgEntityId> references = new ArrayList<>();

    if (StringUtils.isNotBlank(state.getProvisionerId())) {
      references.add(
          CgEntityId.builder().id(state.getProvisionerId()).type(NGMigrationEntityType.INFRA_PROVISIONER).build());
    }

    if (StringUtils.isNotBlank(state.getCloudProviderId()) && !containsExpressions(state.getCloudProviderId())) {
      references.add(CgEntityId.builder().id(state.getCloudProviderId()).type(NGMigrationEntityType.CONNECTOR).build());
    }

    if (null != state.getParametersGitFileConfig()) {
      references.add(CgEntityId.builder()
                         .id(state.getParametersGitFileConfig().getConnectorId())
                         .type(NGMigrationEntityType.CONNECTOR)
                         .build());
    }

    return references;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    String stepType = StepSpecTypeConstants.AZURE_CREATE_ARM_RESOURCE;
    ARMProvisionState state = (ARMProvisionState) getState(stepYaml);
    if (isNotEmpty(state.getAssignmentNameExpression())) {
      stepType = StepSpecTypeConstants.AZURE_CREATE_BP_RESOURCE;
    }
    return stepType;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = super.getProperties(stepYaml);
    ARMProvisionState state = new ARMProvisionState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    ARMProvisionState state = (ARMProvisionState) getState(graphNode);
    String provisionerId = state.getProvisionerId();

    if (isBlueprint(context.getEntities(), provisionerId, state)) {
      return getAzureBP(context, graphNode);
    } else {
      return getAzureARM(context, graphNode);
    }
  }

  @Override
  public ParameterField<Timeout> getTimeout(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);

    String timeoutString = "20m";
    if (null != properties.get("timeoutExpression") && properties.get("timeoutExpression") instanceof Integer) {
      timeoutString = properties.get("timeoutExpression") + "m";
    }
    return ParameterField.createValueField(Timeout.builder().timeoutString(timeoutString).build());
  }

  private AbstractStepNode getAzureBP(WorkflowMigrationContext context, GraphNode graphNode) {
    AzureCreateBPStepNode node = new AzureCreateBPStepNode();
    baseSetup(graphNode, node, context.getIdentifierCaseFormat());
    ARMProvisionState state = (ARMProvisionState) getState(graphNode);
    AzureCreateBPStepConfiguration configuration =
        AzureCreateBPStepConfiguration.builder()
            .connectorRef(getConnectorRef(context.getMigratedEntities(), state))
            .scope(getBPResourceScope(context.getEntities(), state))
            .template(getTemplate(context, state, true))
            .assignmentName(ParameterField.createValueField(state.getAssignmentNameExpression()))
            .build();
    AzureCreateBPStepInfo stepInfo = new AzureCreateBPStepInfo();
    stepInfo.setCreateStepBPConfiguration(configuration);
    node.setCreateStepBPNodeStepInfo(stepInfo);
    return node;
  }

  private AbstractStepNode getAzureARM(WorkflowMigrationContext context, GraphNode graphNode) {
    AzureCreateARMResourceStepNode node = new AzureCreateARMResourceStepNode();
    baseSetup(graphNode, node, context.getIdentifierCaseFormat());
    ARMProvisionState state = (ARMProvisionState) getState(graphNode);

    AzureCreateARMResourceStepConfiguration config =
        AzureCreateARMResourceStepConfiguration.builder()
            .connectorRef(getConnectorRef(context.getMigratedEntities(), state))
            .scope(getARMResourceScope(context.getEntities(), state))
            .template(getTemplate(context, state, false))
            .parameters(getParameters(context, state))
            .build();

    AzureCreateARMResourceStepInfo stepInfo = AzureCreateARMResourceStepInfo.infoBuilder()
                                                  .provisionerIdentifier(getProvisionerIdentifier())
                                                  .createStepConfiguration(config)
                                                  .build();
    node.setAzureCreateARMResourceStepInfo(stepInfo);
    return node;
  }

  private AzureTemplateFile getTemplate(WorkflowMigrationContext context, ARMProvisionState state, boolean isFolder) {
    AzureTemplateFile templateFile = new AzureTemplateFile();
    StoreConfigWrapper storeConfigWrapper;
    Optional<ARMInfrastructureProvisioner> provisionerOptional =
        getProvisioner(context.getEntities(), state.getProvisionerId());
    if (provisionerOptional.isPresent()) {
      ARMInfrastructureProvisioner provisioner = provisionerOptional.get();
      if (provisioner.getSourceType() == ARMSourceType.TEMPLATE_BODY) {
        storeConfigWrapper = StoreConfigWrapper.builder()
                                 .type(StoreConfigType.HARNESS)
                                 .spec(HarnessStore.builder()
                                           .files(ParameterField.createValueField(List.of("/"
                                               + generateFileIdentifier("infraProvisioners/" + provisioner.getName(),
                                                   context.getIdentifierCaseFormat()))))
                                           .build())
                                 .build();
      } else {
        GitStore gitStore = getGitStore(context, provisioner.getGitFileConfig(), isFolder);
        storeConfigWrapper = StoreConfigWrapper.builder().type(StoreConfigType.GIT).spec(gitStore).build();
      }

      templateFile.setStore(storeConfigWrapper);
    }

    return templateFile;
  }

  private AzureCreateARMResourceParameterFile getParameters(WorkflowMigrationContext context, ARMProvisionState state) {
    AzureCreateARMResourceParameterFile parameterFile = new AzureCreateARMResourceParameterFile();
    if (state.getParametersGitFileConfig() != null) {
      GitStore gitStore = getGitStore(context, state.getParametersGitFileConfig(), false);
      parameterFile.setStore(StoreConfigWrapper.builder().type(StoreConfigType.GIT).spec(gitStore).build());
    } else if (isNotEmpty(state.getInlineParametersExpression())) {
      List<String> fileNames = new ArrayList<>();
      fileNames.add("/"
          + getParameterFileName(context.getWorkflow().getName(), state.getName(), context.getIdentifierCaseFormat()));
      parameterFile.setStore(StoreConfigWrapper.builder()
                                 .type(StoreConfigType.HARNESS)
                                 .spec(HarnessStore.builder().files(ParameterField.createValueField(fileNames)).build())
                                 .build());
    }
    return parameterFile.getStore() == null ? null : parameterFile;
  }

  private static GitStore getGitStore(WorkflowMigrationContext context, GitFileConfig gitFileConfig, boolean isFolder) {
    NgEntityDetail connector = getGitConnector(context.getMigratedEntities(), gitFileConfig);
    if (connector == null) {
      return null;
    }
    GitStoreBuilder gitStoreBuilder =
        GitStore.builder()
            .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
            .gitFetchType(gitFileConfig.isUseBranch() ? FetchType.BRANCH : FetchType.COMMIT);

    if (isFolder) {
      gitStoreBuilder.folderPath(ParameterField.createValueField(gitFileConfig.getFilePath()));
    } else {
      gitStoreBuilder.paths(MigratorUtility.splitWithComma(gitFileConfig.getFilePath()));
    }

    GitStore gitStore = gitStoreBuilder.build();

    if (StringUtils.isNotBlank(gitFileConfig.getCommitId())) {
      gitStore.setCommitId(ParameterField.createValueField(gitFileConfig.getCommitId()));
    }

    if (StringUtils.isNotBlank(gitFileConfig.getBranch())) {
      gitStore.setBranch(ParameterField.createValueField(gitFileConfig.getBranch()));
    }
    return gitStore;
  }

  private AzureBPScopes getBPResourceScope(Map<CgEntityId, CgEntityNode> entities, ARMProvisionState state) {
    Optional<ARMInfrastructureProvisioner> provisionerOptional = getProvisioner(entities, state.getProvisionerId());
    if (provisionerOptional.isPresent() && provisionerOptional.get().getScopeType() != null) {
      ARMInfrastructureProvisioner provisioner = provisionerOptional.get();
      switch (provisioner.getScopeType()) {
        case SUBSCRIPTION:
          return AzureBPScopes.SUBSCRIPTION;
        case MANAGEMENT_GROUP:
          return AzureBPScopes.MANAGEMENT_GROUP;
        default:
          break;
      }
    }
    return null;
  }

  private AzureCreateARMResourceStepScope getARMResourceScope(
      Map<CgEntityId, CgEntityNode> entities, ARMProvisionState state) {
    AzureCreateARMResourceStepScope scope = AzureCreateARMResourceStepScope.builder().build();

    Optional<ARMInfrastructureProvisioner> provisionerOptional = getProvisioner(entities, state.getProvisionerId());
    if (provisionerOptional.isPresent() && provisionerOptional.get().getScopeType() != null) {
      ARMInfrastructureProvisioner provisioner = provisionerOptional.get();
      switch (provisioner.getScopeType()) {
        case TENANT:
          scope.setType(AzureScopeTypesNames.Tenant);
          scope.setSpec(AzureTenantSpec.builder()
                            .location(ParameterField.createValueField(state.getLocationExpression()))
                            .build());
          break;
        case SUBSCRIPTION:
          scope.setType(AzureScopeTypesNames.Subscription);
          scope.setSpec(AzureSubscriptionSpec.builder()
                            .location(ParameterField.createValueField(state.getLocationExpression()))
                            .subscription(ParameterField.createValueField(state.getSubscriptionExpression()))
                            .build());
          break;
        case RESOURCE_GROUP:
          scope.setType(AzureScopeTypesNames.ResourceGroup);
          scope.setSpec(AzureResourceGroupSpec.builder()
                            .subscription(ParameterField.createValueField(state.getSubscriptionExpression()))
                            .resourceGroup(ParameterField.createValueField(state.getResourceGroupExpression()))
                            .mode(getMode(state.getMode(), provisioner.getScopeType()))
                            .build());
          break;
        case MANAGEMENT_GROUP:
          scope.setType(AzureScopeTypesNames.ManagementGroup);
          scope.setSpec(AzureManagementSpec.builder()
                            .location(ParameterField.createValueField(state.getLocationExpression()))
                            .managementGroupId(ParameterField.createValueField(state.getManagementGroupExpression()))
                            .build());
          break;
        default:
          break;
      }
    }

    return scope;
  }

  private AzureARMResourceDeploymentMode getMode(String cgMode, ARMScopeType scopeType) {
    AzureDeploymentMode deploymentMode = deploymentMode(cgMode, scopeType);
    return deploymentMode == INCREMENTAL ? AzureARMResourceDeploymentMode.INCREMENTAL
                                         : AzureARMResourceDeploymentMode.COMPLETE;
  }

  private AzureDeploymentMode deploymentMode(String mode, ARMScopeType scopeType) {
    if (ARMScopeType.RESOURCE_GROUP == scopeType) {
      return mode != null ? AzureDeploymentMode.valueOf(mode.toUpperCase()) : INCREMENTAL;
    }
    return INCREMENTAL;
  }

  @NotNull
  private static ParameterField<String> getConnectorRef(
      Map<CgEntityId, NGYamlFile> migratedEntities, ARMProvisionState state) {
    ParameterField<String> connectorRef = ParameterField.createValueField(state.getCloudProviderId());
    if (!containsExpressions(state.getCloudProviderId())) {
      NgEntityDetail connector =
          migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(state.getCloudProviderId()).build())
              .getNgEntityDetail();
      connectorRef = ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector));
    }
    return connectorRef;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return false;
  }

  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public List<NGYamlFile> getChildNGYamlFiles(MigrationInputDTO inputDTO, GraphNode graphNode, String name) {
    List<NGYamlFile> result = new ArrayList<>();
    ARMProvisionState state = (ARMProvisionState) getState(graphNode);
    if (state.getParametersGitFileConfig() == null && isNotEmpty(state.getInlineParametersExpression())) {
      byte[] fileContent = state.getInlineParametersExpression().getBytes(StandardCharsets.UTF_8);
      NGYamlFile yamlConfigFile = getYamlConfigFile(
          inputDTO, fileContent, getParameterFileName(name, state.getName(), inputDTO.getIdentifierCaseFormat()));
      if (null != yamlConfigFile) {
        result.add(yamlConfigFile);
      }
    }

    return result;
  }

  @NotNull
  private static String getParameterFileName(String workflowName, String stateName, CaseFormat identifierCaseFormat) {
    String fileName = workflowName + "/" + stateName + "/armParameters.json";
    return generateFileIdentifier(fileName, identifierCaseFormat);
  }

  private Boolean isBlueprint(Map<CgEntityId, CgEntityNode> entities, String provisionerId, ARMProvisionState state) {
    Optional<ARMInfrastructureProvisioner> provisionerOptional = getProvisioner(entities, provisionerId);

    if (provisionerOptional.isPresent()) {
      ARMInfrastructureProvisioner provisioner = provisionerOptional.get();
      if (null != provisioner.getResourceType()) {
        return provisioner.getResourceType() == ARMResourceType.BLUEPRINT;
      }
    }

    return isNotEmpty(state.getAssignmentNameExpression());
  }

  private Optional<ARMInfrastructureProvisioner> getProvisioner(
      Map<CgEntityId, CgEntityNode> entities, String provisionerId) {
    CgEntityId provisioner = CgEntityId.builder().id(provisionerId).type(INFRA_PROVISIONER).build();
    if (!entities.containsKey(provisioner)) {
      log.error("Provisioner not found with id {}", provisionerId);
      return Optional.empty();
    }
    ARMInfrastructureProvisioner infraProv = (ARMInfrastructureProvisioner) entities.get(provisioner).getEntity();
    return Optional.of(infraProv);
  }
}
