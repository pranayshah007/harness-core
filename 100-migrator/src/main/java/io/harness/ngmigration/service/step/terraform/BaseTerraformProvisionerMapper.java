/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.terraform;

import static io.harness.provision.TerraformConstants.LOCAL_STORE_TYPE;
import static io.harness.provision.TerraformConstants.REMOTE_STORE_TYPE;

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
import io.harness.cdng.provision.terraform.InlineTerraformBackendConfigSpec;
import io.harness.cdng.provision.terraform.InlineTerraformVarFileSpec;
import io.harness.cdng.provision.terraform.RemoteTerraformBackendConfigSpec;
import io.harness.cdng.provision.terraform.RemoteTerraformVarFileSpec;
import io.harness.cdng.provision.terraform.TerraformApplyStepInfo;
import io.harness.cdng.provision.terraform.TerraformApplyStepNode;
import io.harness.cdng.provision.terraform.TerraformBackendConfig;
import io.harness.cdng.provision.terraform.TerraformConfigFilesWrapper;
import io.harness.cdng.provision.terraform.TerraformExecutionData;
import io.harness.cdng.provision.terraform.TerraformPlanCommand;
import io.harness.cdng.provision.terraform.TerraformPlanExecutionData;
import io.harness.cdng.provision.terraform.TerraformPlanStepInfo;
import io.harness.cdng.provision.terraform.TerraformPlanStepNode;
import io.harness.cdng.provision.terraform.TerraformStepConfiguration;
import io.harness.cdng.provision.terraform.TerraformStepConfigurationType;
import io.harness.cdng.provision.terraform.TerraformVarFile;
import io.harness.cdng.provision.terraform.TerraformVarFileTypes;
import io.harness.cdng.provision.terraform.TerraformVarFileWrapper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.exception.InvalidRequestException;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.StepOutput;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.expressions.step.StepExpressionFunctor;
import io.harness.ngmigration.expressions.step.TerraformStepFunctor;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngmigration.utils.NGMigrationConstants;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.beans.GitFileConfig;
import software.wings.beans.GraphNode;
import software.wings.beans.NameValuePair;
import software.wings.beans.PhaseStep;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.sm.states.provision.DestroyTerraformProvisionState;
import software.wings.sm.states.provision.TerraformProvisionState;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public abstract class BaseTerraformProvisionerMapper extends StepMapper {
  private static final String SECRET_FORMAT = "<+secrets.getValue(\"%s\")>";

  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.MANUAL_EFFORT;
  }

  public List<CgEntityId> getReferencedEntities(
      String accountId, Workflow workflow, GraphNode graphNode, Map<String, String> stepIdToServiceIdMap) {
    TerraformProvisionState state = (TerraformProvisionState) getState(graphNode);

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

    if (state.getBackendConfig() != null
        && EmptyPredicate.isNotEmpty(state.getBackendConfig().getInlineBackendConfig())) {
      references.addAll(state.getBackendConfig()
                            .getInlineBackendConfig()
                            .stream()
                            .filter(item -> ENCRYPTED_TEXT.name().equals(item.getValueType()))
                            .map(item -> CgEntityId.builder().type(SECRET).id(item.getValue()).build())
                            .collect(Collectors.toList()));
    }

    return references;
  }

  protected List<NGVariable> getVariables(Map<CgEntityId, NGYamlFile> migratedEntities, List<NameValuePair> variables) {
    List<NGVariable> ngVariables = new ArrayList<>();
    if (EmptyPredicate.isEmpty(variables)) {
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
                                      .value(ParameterField.createValueField(variable.getValue()))
                                      .type(NGVariableType.STRING)
                                      .build())
                           .collect(Collectors.toList()));

    return ngVariables;
  }

  String convertNameValuePairToContent(Map<CgEntityId, NGYamlFile> migratedEntities, List<NameValuePair> valuePairs) {
    if (EmptyPredicate.isEmpty(valuePairs)) {
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

  private TerraformBackendConfig toInlineBackendConfig(
      Map<CgEntityId, NGYamlFile> migratedEntities, List<NameValuePair> configs) {
    InlineTerraformBackendConfigSpec spec = new InlineTerraformBackendConfigSpec();
    spec.setContent(ParameterField.createValueField(convertNameValuePairToContent(migratedEntities, configs)));
    return TerraformBackendConfig.builder().type(TerraformVarFileTypes.Inline).spec(spec).build();
  }

  protected TerraformBackendConfig getBackendConfig(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, TerraformProvisionState state) {
    List<NameValuePair> beConfigs = state.getBackendConfigs();

    if (EmptyPredicate.isNotEmpty(beConfigs)) {
      return toInlineBackendConfig(migratedEntities, beConfigs);
    }
    software.wings.beans.TerraformBackendConfig beConfig = state.getBackendConfig();

    if (beConfig == null) {
      return null;
    }

    if (LOCAL_STORE_TYPE.equals(beConfig.getStoreType())) {
      return toInlineBackendConfig(migratedEntities, beConfig.getInlineBackendConfig());
    }

    if (REMOTE_STORE_TYPE.equals(beConfig.getStoreType())) {
      GitFileConfig gitFileConfig = beConfig.getRemoteBackendConfig();
      GitStore store = GitStore.builder()
                           .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScopeDefaults(
                               migratedEntities, gitFileConfig.getConnectorId(), NGMigrationEntityType.CONNECTOR,
                               NGMigrationConstants.RUNTIME_INPUT)))
                           .build();
      if (StringUtils.isNotBlank(gitFileConfig.getBranch())) {
        store.setGitFetchType(FetchType.BRANCH);
        store.setBranch(ParameterField.createValueField(gitFileConfig.getBranch()));
      } else {
        store.setGitFetchType(FetchType.COMMIT);
        store.setCommitId(ParameterField.createValueField(gitFileConfig.getCommitId()));
      }
      store.setFolderPath(ParameterField.createValueField(gitFileConfig.getFilePath()));
      RemoteTerraformBackendConfigSpec spec = new RemoteTerraformBackendConfigSpec();
      spec.setStore(StoreConfigWrapper.builder().type(StoreConfigType.GIT).spec(store).build());

      return TerraformBackendConfig.builder().type(TerraformVarFileTypes.Remote).spec(spec).build();
    }

    throw new InvalidRequestException("S3 Terraform store types are currently not supported");
  }

  protected ParameterField<String> getSecretManagerRef(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities, String provisionerId) {
    CgEntityId provisioner = CgEntityId.builder().id(provisionerId).type(INFRA_PROVISIONER).build();
    if (!entities.containsKey(provisioner)) {
      return MigratorUtility.RUNTIME_INPUT;
    }
    TerraformInfrastructureProvisioner infraProv =
        (TerraformInfrastructureProvisioner) entities.get(provisioner).getEntity();
    if (infraProv == null || StringUtils.isBlank(infraProv.getKmsId())) {
      return MigratorUtility.RUNTIME_INPUT;
    }
    return MigratorUtility.getIdentifierWithScopeDefaultsRuntime(
        migratedEntities, infraProv.getKmsId(), SECRET_MANAGER);
  }

  protected List<TerraformVarFileWrapper> getVarFiles(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, TerraformProvisionState state) {
    List<TerraformVarFileWrapper> varFileWrappers = new ArrayList<>();

    TerraformVarFileWrapper remoteVarFile = getRemoteTFVar(entities, migratedEntities, state);
    if (remoteVarFile != null) {
      varFileWrappers.add(remoteVarFile);
    }

    if (EmptyPredicate.isNotEmpty(state.getVariables())) {
      String inlineContent = convertNameValuePairToContent(migratedEntities, state.getVariables());
      TerraformVarFileWrapper wrapper = new TerraformVarFileWrapper();
      InlineTerraformVarFileSpec inlineTerraformVarFileSpec = new InlineTerraformVarFileSpec();
      inlineTerraformVarFileSpec.setContent(ParameterField.createValueField(inlineContent));
      wrapper.setVarFile(TerraformVarFile.builder()
                             .identifier("inline")
                             .type(TerraformVarFileTypes.Inline)
                             .spec(inlineTerraformVarFileSpec)
                             .build());
      varFileWrappers.add(wrapper);
    }

    return varFileWrappers;
  }

  private TerraformVarFileWrapper getRemoteTFVar(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, TerraformProvisionState state) {
    TerraformVarFileWrapper wrapper = new TerraformVarFileWrapper();
    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();
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
      remoteTerraformVarFileSpec.setStore(
          StoreConfigWrapper.builder().type(StoreConfigType.GIT).spec(gitStore).build());
      wrapper.setVarFile(TerraformVarFile.builder()
                             .identifier("remote")
                             .type(TerraformVarFileTypes.Remote)
                             .spec(remoteTerraformVarFileSpec)
                             .build());
      return wrapper;
    }
    return null;
  }

  protected ParameterField<List<TaskSelectorYaml>> getDelegateSelectors(TerraformProvisionState state) {
    if (StringUtils.isNotBlank(state.getDelegateTag())) {
      return MigratorUtility.getDelegateSelectors(Collections.singletonList(state.getDelegateTag()));
    }
    return MigratorUtility.getDelegateSelectors(null);
  }

  protected ParameterField<List<String>> getDelegateSel(TerraformProvisionState state) {
    if (StringUtils.isNotBlank(state.getDelegateTag())) {
      return ParameterField.createValueField(Collections.singletonList(state.getDelegateTag()));
    }
    return ParameterField.createValueField(Collections.emptyList());
  }

  private TerraformConfigFilesWrapper getConfigFilesWrapper(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, TerraformProvisionState state) {
    GitStore store = getGitStore(entities, migratedEntities, state);
    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();
    configFilesWrapper.setStore(StoreConfigWrapper.builder().spec(store).type(StoreConfigType.GIT).build());
    configFilesWrapper.setModuleSource(
        ModuleSource.builder().useConnectorCredentials(ParameterField.createValueField(true)).build());
    return configFilesWrapper;
  }

  private GitStore getGitStore(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      TerraformProvisionState state) {
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
    TerraformInfrastructureProvisioner provisioner = (TerraformInfrastructureProvisioner) node.getEntity();
    GitStoreBuilder storeBuilder = GitStore.builder();
    if (StringUtils.isNotBlank(provisioner.getSourceRepoSettingId())) {
      storeBuilder.connectorRef(ParameterField.createValueField(
          MigratorUtility.getIdentifierWithScopeDefaults(migratedEntities, provisioner.getSourceRepoSettingId(),
              NGMigrationEntityType.CONNECTOR, NGMigrationConstants.RUNTIME_INPUT)));
    } else {
      storeBuilder.connectorRef(MigratorUtility.RUNTIME_INPUT);
    }

    String path = StringUtils.isNotBlank(provisioner.getPath()) ? provisioner.getPath() : "/";
    if (StringUtils.isNotBlank(provisioner.getSourceRepoBranch())) {
      storeBuilder.gitFetchType(FetchType.BRANCH);
      storeBuilder.branch(ParameterField.createValueField(provisioner.getSourceRepoBranch()));
    } else {
      storeBuilder.gitFetchType(FetchType.COMMIT);
      storeBuilder.commitId(ParameterField.createValueField(provisioner.getCommitId()));
    }
    storeBuilder.folderPath(ParameterField.createValueField(path));
    return storeBuilder.build();
  }

  protected TerraformExecutionData getExecutionData(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, TerraformProvisionState state) {
    TerraformExecutionData executionData = new TerraformExecutionData();
    executionData.setEnvironmentVariables(getVariables(migratedEntities, state.getEnvironmentVariables()));
    executionData.setTargets(ParameterField.createValueField(state.getTargets()));
    executionData.setTerraformVarFiles(getVarFiles(entities, migratedEntities, state));
    executionData.setTerraformConfigFilesWrapper(getConfigFilesWrapper(entities, migratedEntities, state));
    executionData.setWorkspace(ParameterField.createValueField(state.getWorkspace()));
    executionData.setTerraformBackendConfig(getBackendConfig(entities, migratedEntities, state));
    return executionData;
  }

  protected TerraformPlanExecutionData getPlanExecutionData(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, TerraformProvisionState state) {
    TerraformPlanCommand command = TerraformPlanCommand.APPLY;
    if (state instanceof DestroyTerraformProvisionState) {
      command = TerraformPlanCommand.DESTROY;
    }

    return TerraformPlanExecutionData.builder()
        .workspace(ParameterField.createValueField(state.getWorkspace()))
        .targets(ParameterField.createValueField(state.getTargets()))
        .exportTerraformPlanJson(ParameterField.createValueField(state.isExportPlanToApplyStep()))
        .exportTerraformHumanReadablePlan(ParameterField.createValueField(state.isExportPlanToHumanReadableOutput()))
        .environmentVariables(getVariables(migratedEntities, state.getEnvironmentVariables()))
        .command(command)
        .terraformVarFiles(getVarFiles(entities, migratedEntities, state))
        .secretManagerRef(getSecretManagerRef(entities, migratedEntities, state.getProvisionerId()))
        .terraformConfigFilesWrapper(getConfigFilesWrapper(entities, migratedEntities, state))
        .terraformBackendConfig(getBackendConfig(entities, migratedEntities, state))
        .build();
  }

  protected AbstractStepNode getStepNode(MigrationContext migrationContext, GraphNode graphNode) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    CaseFormat identifierCaseFormat = migrationContext.getInputDTO().getIdentifierCaseFormat();
    TerraformProvisionState state = (TerraformProvisionState) getState(graphNode);
    if (state.isRunPlanOnly()) {
      TerraformPlanExecutionData executionData = getPlanExecutionData(entities, migratedEntities, state);
      TerraformPlanStepInfo stepInfo =
          TerraformPlanStepInfo.infoBuilder()
              .delegateSelectors(getDelegateSelectors(state))
              .provisionerIdentifier(getProvisionerIdentifier(migrationContext, state.getProvisionerId()))
              .terraformPlanExecutionData(executionData)
              .build();
      TerraformPlanStepNode planStepNode = new TerraformPlanStepNode();
      baseSetup(graphNode, planStepNode, identifierCaseFormat);
      planStepNode.setTerraformPlanStepInfo(stepInfo);
      return planStepNode;
    } else {
      TerraformStepConfiguration stepConfiguration = new TerraformStepConfiguration();
      stepConfiguration.setTerraformExecutionData(getExecutionData(entities, migratedEntities, state));
      stepConfiguration.setTerraformStepConfigurationType(TerraformStepConfigurationType.INLINE);
      TerraformApplyStepInfo stepInfo =
          TerraformApplyStepInfo.infoBuilder()
              .delegateSelectors(getDelegateSelectors(state))
              .terraformStepConfiguration(stepConfiguration)
              .provisionerIdentifier(getProvisionerIdentifier(migrationContext, state.getProvisionerId()))
              .build();
      TerraformApplyStepNode applyStepNode = new TerraformApplyStepNode();
      baseSetup(graphNode, applyStepNode, identifierCaseFormat);
      applyStepNode.setTerraformApplyStepInfo(stepInfo);
      MigratorExpressionUtils.render(migrationContext, applyStepNode, new HashMap<>());
      return applyStepNode;
    }
  }

  @Override
  public List<StepExpressionFunctor> getExpressionFunctor(
      WorkflowMigrationContext migrationContext, WorkflowPhase phase, PhaseStep phaseStep, GraphNode graphNode) {
    String sweepingOutputName = "terraform";
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
        .map(TerraformStepFunctor::new)
        .collect(Collectors.toList());
  }
}
