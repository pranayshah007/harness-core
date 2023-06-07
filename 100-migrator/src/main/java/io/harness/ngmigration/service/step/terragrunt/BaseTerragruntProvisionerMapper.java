/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.terragrunt;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.ServiceVariableType.ENCRYPTED_TEXT;
import static software.wings.ngmigration.NGMigrationEntityType.INFRA_PROVISIONER;
import static software.wings.ngmigration.NGMigrationEntityType.SECRET;
import static software.wings.ngmigration.NGMigrationEntityType.SECRET_MANAGER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStore.GitStoreBuilder;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.manifest.yaml.storeConfig.moduleSource.ModuleSource;
import io.harness.cdng.provision.terragrunt.InlineTerragruntBackendConfigSpec;
import io.harness.cdng.provision.terragrunt.InlineTerragruntVarFileSpec;
import io.harness.cdng.provision.terragrunt.RemoteTerragruntVarFileSpec;
import io.harness.cdng.provision.terragrunt.TerragruntApplyStepInfo;
import io.harness.cdng.provision.terragrunt.TerragruntApplyStepNode;
import io.harness.cdng.provision.terragrunt.TerragruntBackendConfig;
import io.harness.cdng.provision.terragrunt.TerragruntConfigFilesWrapper;
import io.harness.cdng.provision.terragrunt.TerragruntExecutionData;
import io.harness.cdng.provision.terragrunt.TerragruntModuleConfig;
import io.harness.cdng.provision.terragrunt.TerragruntPlanCommand;
import io.harness.cdng.provision.terragrunt.TerragruntPlanExecutionData;
import io.harness.cdng.provision.terragrunt.TerragruntPlanStepInfo;
import io.harness.cdng.provision.terragrunt.TerragruntPlanStepNode;
import io.harness.cdng.provision.terragrunt.TerragruntRunType;
import io.harness.cdng.provision.terragrunt.TerragruntStepConfiguration;
import io.harness.cdng.provision.terragrunt.TerragruntStepConfigurationType;
import io.harness.cdng.provision.terragrunt.TerragruntVarFile;
import io.harness.cdng.provision.terragrunt.TerragruntVarFileTypes;
import io.harness.cdng.provision.terragrunt.TerragruntVarFileWrapper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.StepOutput;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.expressions.step.StepExpressionFunctor;
import io.harness.ngmigration.expressions.step.TerragruntStepFunctor;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngmigration.utils.NGMigrationConstants;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.beans.GraphNode;
import software.wings.beans.NameValuePair;
import software.wings.beans.PhaseStep;
import software.wings.beans.TerragruntInfrastructureProvisioner;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.sm.states.provision.TerragruntDestroyState;
import software.wings.sm.states.provision.TerragruntProvisionState;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDP)
public abstract class BaseTerragruntProvisionerMapper extends StepMapper {
  private static final String SECRET_FORMAT = "<+secrets.getValue(\"%s\")>";

  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.MANUAL_EFFORT;
  }

  public List<CgEntityId> getReferencedEntities(
      String accountId, Workflow workflow, GraphNode graphNode, Map<String, String> stepIdToServiceIdMap) {
    TerragruntProvisionState state = (TerragruntProvisionState) getState(graphNode);

    List<CgEntityId> references = new ArrayList<>();

    if (StringUtils.isNotBlank(state.getProvisionerId())) {
      references.add(
          CgEntityId.builder().id(state.getProvisionerId()).type(NGMigrationEntityType.INFRA_PROVISIONER).build());
    }

    if (EmptyPredicate.isNotEmpty(state.getEnvironmentVariables())) {
      references.addAll(state.getEnvironmentVariables()
                            .stream()
                            .filter(item -> ENCRYPTED_TEXT.name().equals(item.getValueType()))
                            .map(item -> CgEntityId.builder().type(SECRET).id(item.getValue()).build())
                            .collect(Collectors.toList()));
    }

    if (EmptyPredicate.isNotEmpty(state.getVariables())) {
      references.addAll(state.getVariables()
                            .stream()
                            .filter(item -> ENCRYPTED_TEXT.name().equals(item.getValueType()))
                            .map(item -> CgEntityId.builder().type(SECRET).id(item.getValue()).build())
                            .collect(Collectors.toList()));
    }

    if (EmptyPredicate.isNotEmpty(state.getBackendConfigs())) {
      references.addAll(state.getBackendConfigs()
                            .stream()
                            .filter(item -> ENCRYPTED_TEXT.name().equals(item.getValueType()))
                            .map(item -> CgEntityId.builder().type(SECRET).id(item.getValue()).build())
                            .collect(Collectors.toList()));
    }

    if (state.getBackendConfigs() != null && EmptyPredicate.isNotEmpty(state.getBackendConfigs())) {
      references.addAll(state.getBackendConfigs()
                            .stream()
                            .filter(item -> ENCRYPTED_TEXT.name().equals(item.getValueType()))
                            .map(item -> CgEntityId.builder().type(SECRET).id(item.getValue()).build())
                            .collect(Collectors.toList()));
    }

    return references;
  }

  protected List<NGVariable> getVariables(Map<CgEntityId, NGYamlFile> migratedEntities, List<NameValuePair> variables) {
    List<NGVariable> ngVariables = new ArrayList<>();
    if (isEmpty(variables)) {
      return ngVariables;
    }

    ngVariables.addAll(variables.stream()
                           .filter(variable -> "ENCRYPTED_TEXT".equals(variable.getValueType()))
                           .map(variable
                               -> StringNGVariable.builder()
                                      .name(variable.getName())
                                      .value(ParameterField.createValueField(String.format(SECRET_FORMAT,
                                          MigratorUtility.getSecretRef(migratedEntities, variable.getValue())
                                              .toSecretRefStringValue())))
                                      .type(NGVariableType.STRING)
                                      .build())
                           .collect(Collectors.toList()));

    ngVariables.addAll(variables.stream()
                           .filter(variable -> !"ENCRYPTED_TEXT".equals(variable.getValueType()))
                           .map(variable
                               -> StringNGVariable.builder()
                                      .name(variable.getName())
                                      .value(ParameterField.createValueField(
                                          StringUtils.isNotBlank(variable.getValue()) ? variable.getValue() : ""))
                                      .type(NGVariableType.STRING)
                                      .build())
                           .collect(Collectors.toList()));

    return ngVariables;
  }

  String convertNameValuePairToContent(Map<CgEntityId, NGYamlFile> migratedEntities, List<NameValuePair> valuePairs) {
    if (isEmpty(valuePairs)) {
      return "";
    }

    StringBuilder content = new StringBuilder();
    for (NameValuePair valuePair : valuePairs) {
      String val = valuePair.getValue();
      if ("ENCRYPTED_TEXT".equals(valuePair.getValueType())) {
        String secretIdentifier =
            MigratorUtility.getIdentifierWithScopeDefaults(migratedEntities, valuePair.getValue(), SECRET);
        val = String.format(SECRET_FORMAT, secretIdentifier);
      }
      content.append(String.format("%s=%s", valuePair.getName(), val)).append('\n');
    }

    return content.toString();
  }

  private TerragruntBackendConfig toInlineBackendConfig(
      Map<CgEntityId, NGYamlFile> migratedEntities, List<NameValuePair> configs) {
    InlineTerragruntBackendConfigSpec spec = new InlineTerragruntBackendConfigSpec();
    spec.setContent(ParameterField.createValueField(convertNameValuePairToContent(migratedEntities, configs)));
    return TerragruntBackendConfig.builder().type(TerragruntVarFileTypes.Inline).spec(spec).build();
  }

  protected TerragruntBackendConfig getBackendConfig(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, TerragruntProvisionState state) {
    List<NameValuePair> beConfigs = state.getBackendConfigs();

    if (EmptyPredicate.isNotEmpty(beConfigs)) {
      return toInlineBackendConfig(migratedEntities, beConfigs);
    }
    List<NameValuePair> backendConfigs = state.getBackendConfigs();

    if (isEmpty(backendConfigs)) {
      return null;
    }

    return toInlineBackendConfig(migratedEntities, backendConfigs);
  }

  protected ParameterField<String> getSecretManagerRef(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities, String provisionerId) {
    CgEntityId provisioner = CgEntityId.builder().id(provisionerId).type(INFRA_PROVISIONER).build();
    if (!entities.containsKey(provisioner)) {
      return MigratorUtility.RUNTIME_INPUT;
    }
    TerragruntInfrastructureProvisioner infraProv =
        (TerragruntInfrastructureProvisioner) entities.get(provisioner).getEntity();
    if (infraProv == null || StringUtils.isBlank(infraProv.getSecretManagerId())) {
      return MigratorUtility.RUNTIME_INPUT;
    }
    return MigratorUtility.getIdentifierWithScopeDefaultsRuntime(
        migratedEntities, infraProv.getSecretManagerId(), SECRET_MANAGER);
  }

  protected List<TerragruntVarFileWrapper> getVarFiles(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, TerragruntProvisionState state) {
    List<TerragruntVarFileWrapper> varFileWrappers = new ArrayList<>();

    TerragruntVarFileWrapper remoteTerragruntVarFile = getRemoteTerragruntVarFile(entities, migratedEntities, state);
    if (remoteTerragruntVarFile != null) {
      varFileWrappers.add(remoteTerragruntVarFile);
    }

    if (EmptyPredicate.isNotEmpty(state.getVariables())) {
      String inlineContent = convertNameValuePairToContent(migratedEntities, state.getVariables());
      TerragruntVarFileWrapper wrapper = new TerragruntVarFileWrapper();
      InlineTerragruntVarFileSpec inlineTerragruntVarFileSpec = new InlineTerragruntVarFileSpec();
      inlineTerragruntVarFileSpec.setContent(ParameterField.createValueField(inlineContent));
      wrapper.setVarFile(TerragruntVarFile.builder()
                             .identifier("inline")
                             .type(TerragruntVarFileTypes.Inline)
                             .spec(inlineTerragruntVarFileSpec)
                             .build());
      varFileWrappers.add(wrapper);
    }

    return varFileWrappers;
  }

  private TerragruntVarFileWrapper getRemoteTerragruntVarFile(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, TerragruntProvisionState state) {
    TerragruntVarFileWrapper wrapper = new TerragruntVarFileWrapper();
    RemoteTerragruntVarFileSpec remoteTerragruntVarFileSpec = new RemoteTerragruntVarFileSpec();
    GitStore gitStore = null;
    if (EmptyPredicate.isNotEmpty(state.getTfVarFiles())) {
      gitStore = getGitStore(entities, migratedEntities, state);
      gitStore.setFolderPath(ParameterField.ofNull());
      gitStore.setPaths(ParameterField.createValueField(state.getTfVarFiles()));
    } else if (state.getTfVarGitFileConfig() != null) {
      GitStoreBuilder storeBuilder = GitStore.builder().connectorRef(
          ParameterField.createValueField(MigratorUtility.getIdentifierWithScopeDefaults(migratedEntities,
              state.getTfVarGitFileConfig().getConnectorId(), NGMigrationEntityType.CONNECTOR,
              NGMigrationConstants.RUNTIME_INPUT)));
      if (StringUtils.isNotBlank(state.getTfVarGitFileConfig().getBranch())) {
        storeBuilder.gitFetchType(FetchType.BRANCH);
        storeBuilder.branch(ParameterField.createValueField(state.getTfVarGitFileConfig().getBranch()));
      } else {
        storeBuilder.gitFetchType(FetchType.COMMIT);
        storeBuilder.commitId(ParameterField.createValueField(state.getTfVarGitFileConfig().getCommitId()));
      }
      storeBuilder.paths(ParameterField.createValueField(
          Arrays.stream(state.getTfVarGitFileConfig().getFilePath().split(",")).collect(Collectors.toList())));
      gitStore = storeBuilder.build();
    }
    if (gitStore != null) {
      remoteTerragruntVarFileSpec.setStore(
          StoreConfigWrapper.builder().type(StoreConfigType.GIT).spec(gitStore).build());
      wrapper.setVarFile(TerragruntVarFile.builder()
                             .identifier("remote")
                             .type(TerragruntVarFileTypes.Remote)
                             .spec(remoteTerragruntVarFileSpec)
                             .build());
      return wrapper;
    }
    return null;
  }

  protected ParameterField<List<TaskSelectorYaml>> getDelegateSelectors(TerragruntProvisionState state) {
    if (StringUtils.isNotBlank(state.getDelegateTag())) {
      return MigratorUtility.getDelegateSelectors(Collections.singletonList(state.getDelegateTag()));
    }
    return MigratorUtility.getDelegateSelectors(null);
  }

  protected ParameterField<List<String>> getDelegateSel(TerragruntProvisionState state) {
    if (StringUtils.isNotBlank(state.getDelegateTag())) {
      return ParameterField.createValueField(Collections.singletonList(state.getDelegateTag()));
    }
    return ParameterField.createValueField(Collections.emptyList());
  }

  private TerragruntConfigFilesWrapper getConfigFilesWrapper(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, TerragruntProvisionState state) {
    GitStore store = getGitStore(entities, migratedEntities, state);
    TerragruntConfigFilesWrapper configFilesWrapper = new TerragruntConfigFilesWrapper();
    configFilesWrapper.setStore(StoreConfigWrapper.builder().spec(store).type(StoreConfigType.GIT).build());
    configFilesWrapper.setModuleSource(
        ModuleSource.builder().useConnectorCredentials(ParameterField.createValueField(true)).build());
    return configFilesWrapper;
  }

  private GitStore getGitStore(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      TerragruntProvisionState state) {
    CgEntityNode node =
        entities.getOrDefault(CgEntityId.builder().id(state.getProvisionerId()).type(INFRA_PROVISIONER).build(), null);
    if (node == null || node.getEntity() == null) {
      return GitStore.builder()
          .connectorRef(MigratorUtility.RUNTIME_INPUT)
          .branch(MigratorUtility.RUNTIME_INPUT)
          .gitFetchType(FetchType.BRANCH)
          .folderPath(MigratorUtility.RUNTIME_INPUT)
          .build();
    }
    GitStoreBuilder storeBuilder = GitStore.builder();
    TerragruntInfrastructureProvisioner provisioner = (TerragruntInfrastructureProvisioner) node.getEntity();
    if (StringUtils.isNotBlank(provisioner.getSourceRepoSettingId())) {
      storeBuilder.connectorRef(ParameterField.createValueField(
          MigratorUtility.getIdentifierWithScopeDefaults(migratedEntities, provisioner.getSourceRepoSettingId(),
              NGMigrationEntityType.CONNECTOR, NGMigrationConstants.RUNTIME_INPUT)));
    } else {
      storeBuilder.connectorRef(MigratorUtility.RUNTIME_INPUT);
    }
    if (StringUtils.isNotBlank(provisioner.getSourceRepoBranch())) {
      storeBuilder.gitFetchType(FetchType.BRANCH);
      storeBuilder.branch(ParameterField.createValueField(provisioner.getSourceRepoBranch()));
    } else {
      storeBuilder.gitFetchType(FetchType.COMMIT);
      storeBuilder.commitId(ParameterField.createValueField(provisioner.getCommitId()));
    }
    storeBuilder.folderPath(ParameterField.createValueField(provisioner.getPath()));
    return storeBuilder.build();
  }

  protected TerragruntExecutionData getExecutionData(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, TerragruntProvisionState state) {
    TerragruntExecutionData executionData = new TerragruntExecutionData();
    executionData.setEnvironmentVariables(getVariables(migratedEntities, state.getEnvironmentVariables()));
    executionData.setTargets(ParameterField.createValueField(state.getTargets()));
    executionData.setTerragruntVarFiles(getVarFiles(entities, migratedEntities, state));
    executionData.setTerragruntConfigFilesWrapper(getConfigFilesWrapper(entities, migratedEntities, state));
    executionData.setWorkspace(ParameterField.createValueField(state.getWorkspace()));
    executionData.setTerragruntBackendConfig(getBackendConfig(entities, migratedEntities, state));
    executionData.setTerragruntModuleConfig(getModuleConfig(state));
    return executionData;
  }

  protected TerragruntPlanExecutionData getPlanExecutionData(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, TerragruntProvisionState state) {
    TerragruntPlanCommand command = TerragruntPlanCommand.APPLY;
    if (state instanceof TerragruntDestroyState) {
      command = TerragruntPlanCommand.DESTROY;
    }

    return TerragruntPlanExecutionData.builder()
        .workspace(ParameterField.createValueField(state.getWorkspace()))
        .targets(ParameterField.createValueField(state.getTargets()))
        .exportTerragruntPlanJson(ParameterField.createValueField(state.isExportPlanToApplyStep()))
        .environmentVariables(getVariables(migratedEntities, state.getEnvironmentVariables()))
        .command(command)
        .terragruntVarFiles(getVarFiles(entities, migratedEntities, state))
        .secretManagerRef(getSecretManagerRef(entities, migratedEntities, state.getProvisionerId()))
        .terragruntConfigFilesWrapper(getConfigFilesWrapper(entities, migratedEntities, state))
        .terragruntBackendConfig(getBackendConfig(entities, migratedEntities, state))
        .terragruntModuleConfig(getModuleConfig(state))
        .build();
  }

  private TerragruntModuleConfig getModuleConfig(TerragruntProvisionState state) {
    TerragruntModuleConfig moduleConfig = new TerragruntModuleConfig();
    if (state.isRunAll()) {
      moduleConfig.setTerragruntRunType(TerragruntRunType.RUN_ALL);
    } else {
      moduleConfig.setTerragruntRunType(TerragruntRunType.RUN_MODULE);
    }
    moduleConfig.setPath(ParameterField.createValueField(state.getPathToModule()));
    return moduleConfig;
  }

  protected AbstractStepNode getStepNode(MigrationContext context, GraphNode graphNode) {
    TerragruntProvisionState state = (TerragruntProvisionState) getState(graphNode);
    if (state.isRunPlanOnly()) {
      TerragruntPlanExecutionData executionData =
          getPlanExecutionData(context.getEntities(), context.getMigratedEntities(), state);
      TerragruntPlanStepInfo stepInfo =
          TerragruntPlanStepInfo.infoBuilder()
              .delegateSelectors(getDelegateSelectors(state))
              .provisionerIdentifier(getProvisionerIdentifier(context, state.getProvisionerId()))
              .terragruntPlanExecutionData(executionData)
              .build();
      TerragruntPlanStepNode planStepNode = new TerragruntPlanStepNode();
      baseSetup(graphNode, planStepNode, context.getInputDTO().getIdentifierCaseFormat());
      planStepNode.setTerragruntPlanStepInfo(stepInfo);
      return planStepNode;
    } else {
      TerragruntStepConfiguration stepConfiguration = new TerragruntStepConfiguration();
      stepConfiguration.setTerragruntExecutionData(
          getExecutionData(context.getEntities(), context.getMigratedEntities(), state));
      stepConfiguration.setTerragruntStepConfigurationType(state.isInheritApprovedPlan()
              ? TerragruntStepConfigurationType.INHERIT_FROM_PLAN
              : TerragruntStepConfigurationType.INLINE);
      TerragruntApplyStepInfo stepInfo =
          TerragruntApplyStepInfo.infoBuilder()
              .delegateSelectors(getDelegateSelectors(state))
              .terragruntStepConfiguration(stepConfiguration)
              .provisionerIdentifier(getProvisionerIdentifier(context, state.getProvisionerId()))
              .build();
      TerragruntApplyStepNode applyStepNode = new TerragruntApplyStepNode();
      baseSetup(graphNode, applyStepNode, context.getInputDTO().getIdentifierCaseFormat());
      applyStepNode.setTerragruntApplyStepInfo(stepInfo);
      return applyStepNode;
    }
  }

  @Override
  public List<StepExpressionFunctor> getExpressionFunctor(
      WorkflowMigrationContext migrationContext, WorkflowPhase phase, PhaseStep phaseStep, GraphNode graphNode) {
    String sweepingOutputName = "terragrunt";
    return Lists.newArrayList(String.format("context.%s", sweepingOutputName), String.format("%s", sweepingOutputName))
        .stream()
        .map(exp
            -> StepOutput.builder()
                   .stageIdentifier(
                       MigratorUtility.generateIdentifier(phase.getName(), migrationContext.getIdentifierCaseFormat()))
                   .stepIdentifier(MigratorUtility.generateIdentifier(
                       graphNode.getName(), migrationContext.getIdentifierCaseFormat()))
                   .stepGroupIdentifier(MigratorUtility.generateIdentifier(
                       phaseStep.getName(), migrationContext.getIdentifierCaseFormat()))
                   .expression(exp)
                   .build())
        .map(TerragruntStepFunctor::new)
        .collect(Collectors.toList());
  }
}
