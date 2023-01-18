/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.terraform;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;
import static software.wings.ngmigration.NGMigrationEntityType.INFRA_PROVISIONER;
import static software.wings.ngmigration.NGMigrationEntityType.SECRET;
import static software.wings.ngmigration.NGMigrationEntityType.SECRET_MANAGER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.provision.terraform.InlineTerraformVarFileSpec;
import io.harness.cdng.provision.terraform.RemoteTerraformBackendConfigSpec;
import io.harness.cdng.provision.terraform.RemoteTerraformVarFileSpec;
import io.harness.cdng.provision.terraform.TerraformBackendConfig;
import io.harness.cdng.provision.terraform.TerraformConfigFilesWrapper;
import io.harness.cdng.provision.terraform.TerraformExecutionData;
import io.harness.cdng.provision.terraform.TerraformPlanCommand;
import io.harness.cdng.provision.terraform.TerraformPlanExecutionData;
import io.harness.cdng.provision.terraform.TerraformVarFile;
import io.harness.cdng.provision.terraform.TerraformVarFileTypes;
import io.harness.cdng.provision.terraform.TerraformVarFileWrapper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.beans.GitFileConfig;
import software.wings.beans.GraphNode;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.NameValuePair;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.sm.states.provision.DestroyTerraformProvisionState;
import software.wings.sm.states.provision.TerraformProvisionState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public abstract class BaseTerraformProvisionerMapper implements StepMapper {
  public List<CgEntityId> getReferencedEntities(GraphNode graphNode) {
    TerraformProvisionState state = (TerraformProvisionState) getState(graphNode);

    List<CgEntityId> references = new ArrayList<>();

    if (StringUtils.isNotBlank(state.getProvisionerId())) {
      references.add(
          CgEntityId.builder().id(state.getProvisionerId()).type(NGMigrationEntityType.INFRA_PROVISIONER).build());
    }

    if (EmptyPredicate.isNotEmpty(state.getEnvironmentVariables())) {
      references.addAll(state.getEnvironmentVariables()
                            .stream()
                            .filter(item -> "ENCRYPTED_TEXT".equals(item.getValueType()))
                            .map(item -> CgEntityId.builder().type(SECRET).id(item.getValue()).build())
                            .collect(Collectors.toList()));
    }

    if (EmptyPredicate.isNotEmpty(state.getVariables())) {
      references.addAll(state.getVariables()
                            .stream()
                            .filter(item -> "ENCRYPTED_TEXT".equals(item.getValueType()))
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
                               -> SecretNGVariable.builder()
                                      .name(variable.getName())
                                      .value(ParameterField.createValueField(
                                          MigratorUtility.getSecretRef(migratedEntities, variable.getValue())))
                                      .type(NGVariableType.SECRET)
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

  protected TerraformBackendConfig getBackendConfig(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, TerraformProvisionState state) {
    CgEntityId provisionerId = CgEntityId.builder().id(state.getProvisionerId()).type(INFRA_PROVISIONER).build();
    ParameterField<String> connectorRef = MigratorUtility.RUNTIME_INPUT;
    String branch = null;
    String commitId = null;
    String folderPath = null;
    if (entities.containsKey(provisionerId)) {
      TerraformInfrastructureProvisioner provisioner =
          (TerraformInfrastructureProvisioner) entities.get(provisionerId).getEntity();
      connectorRef = MigratorUtility.getIdentifierWithScopeDefaultsRuntime(
          migratedEntities, provisioner.getSourceRepoSettingId(), CONNECTOR);
      branch = provisioner.getSourceRepoBranch();
      commitId = provisioner.getCommitId();
      folderPath = provisioner.getPath();
    }
    GitStore store = GitStore.builder().connectorRef(connectorRef).build();

    // Branch/Commit Logic
    if (StringUtils.isAllBlank(branch, commitId)) {
      store.setBranch(MigratorUtility.RUNTIME_INPUT);
    }
    if (StringUtils.isNotBlank(branch)) {
      store.setBranch(ParameterField.createValueField(branch));
    } else {
      store.setCommitId(ParameterField.createValueField(commitId));
    }

    // Path
    store.setFolderPath(StringUtils.isNotBlank(folderPath) ? ParameterField.createValueField(folderPath)
                                                           : MigratorUtility.RUNTIME_INPUT);

    RemoteTerraformBackendConfigSpec spec = new RemoteTerraformBackendConfigSpec();
    spec.setStore(StoreConfigWrapper.builder().type(StoreConfigType.GIT).spec(store).build());

    return TerraformBackendConfig.builder().type(TerraformVarFileTypes.Remote).spec(spec).build();
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

  protected List<TerraformVarFileWrapper> getVarFiles(TerraformProvisionState state) {
    // TODO
    if (EmptyPredicate.isEmpty(state.getTfVarFiles())) {
      return Collections.emptyList();
    }

    return state.getTfVarFiles()
        .stream()
        .map(file -> {
          TerraformVarFileWrapper wrapper = new TerraformVarFileWrapper();
          InlineTerraformVarFileSpec inlineTerraformVarFileSpec = null;
          RemoteTerraformVarFileSpec remoteTerraformVarFileSpec = null;

          wrapper.setVarFile(TerraformVarFile.builder()
                                 .identifier("")
                                 .type(TerraformVarFileTypes.Inline)
                                 .spec(inlineTerraformVarFileSpec)
                                 .build());

          return wrapper;
        })
        .collect(Collectors.toList());
  }

  protected String getProvisionerIdentifier(Map<CgEntityId, CgEntityNode> entities, TerraformProvisionState state) {
    String provisionerIdentifier = "__PLEASE_FIX_ME__";
    CgEntityNode cgEntityNode =
        entities.getOrDefault(CgEntityId.builder().type(INFRA_PROVISIONER).id(state.getProvisionerId()).build(), null);
    if (cgEntityNode != null) {
      InfrastructureProvisioner infrastructureProvisioner = (InfrastructureProvisioner) cgEntityNode.getEntity();
      provisionerIdentifier = MigratorUtility.generateIdentifier(infrastructureProvisioner.getName());
    }
    return provisionerIdentifier;
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
    ParameterField<String> connectorRef = MigratorUtility.RUNTIME_INPUT;
    GitStore store = GitStore.builder().connectorRef(connectorRef).build();
    String branch = null;
    String commitId = null;
    List<String> files = new ArrayList<>();
    if (state.getTfVarGitFileConfig() != null) {
      GitFileConfig gitFileConfig = state.getTfVarGitFileConfig();
      branch = gitFileConfig.getBranch();
      commitId = gitFileConfig.getCommitId();
      if (StringUtils.isNotBlank(gitFileConfig.getFilePath())) {
        files = Arrays.stream(gitFileConfig.getFilePath().split(",")).map(String::trim).collect(Collectors.toList());
      }
    }
    // Branch/Commit Logic
    if (StringUtils.isAllBlank(branch, commitId)) {
      store.setBranch(MigratorUtility.RUNTIME_INPUT);
    }
    if (StringUtils.isNotBlank(branch)) {
      store.setBranch(ParameterField.createValueField(branch));
    } else {
      store.setCommitId(ParameterField.createValueField(commitId));
    }

    // Path
    store.setPaths(ParameterField.createValueField(files));

    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();
    configFilesWrapper.setStore(StoreConfigWrapper.builder().spec(store).type(StoreConfigType.GIT).build());
    configFilesWrapper.setModuleSource(null);
    return configFilesWrapper;
  }

  protected TerraformExecutionData getExecutionData(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, TerraformProvisionState state) {
    TerraformExecutionData executionData = new TerraformExecutionData();
    executionData.setEnvironmentVariables(getVariables(migratedEntities, state.getEnvironmentVariables()));
    executionData.setTargets(ParameterField.createValueField(state.getTargets()));
    executionData.setTerraformVarFiles(getVarFiles(state));
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
        .terraformVarFiles(getVarFiles(state))
        .secretManagerRef(getSecretManagerRef(entities, migratedEntities, state.getProvisionerId()))
        .terraformConfigFilesWrapper(getConfigFilesWrapper(entities, migratedEntities, state))
        .terraformBackendConfig(getBackendConfig(entities, migratedEntities, state))
        .build();
  }
}
