/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.cloudformation;
import static io.harness.cdng.provision.cloudformation.CloudformationTagsFileTypes.Inline;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.ngmigration.NGMigrationEntityType.INFRA_PROVISIONER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStore.GitStoreBuilder;
import io.harness.cdng.manifest.yaml.S3UrlStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.provision.cloudformation.CloudformationParametersFileSpec;
import io.harness.cdng.provision.cloudformation.CloudformationTags;
import io.harness.cdng.provision.cloudformation.CloudformationTemplateFile;
import io.harness.cdng.provision.cloudformation.CloudformationTemplateFileTypes;
import io.harness.cdng.provision.cloudformation.InlineCloudformationTagsFileSpec;
import io.harness.cdng.provision.cloudformation.InlineCloudformationTemplateFileSpec;
import io.harness.cdng.provision.cloudformation.RemoteCloudformationTemplateFileSpec;
import io.harness.cdng.provision.cloudformation.S3UrlCloudformationTemplateFileSpec;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.beans.GitFileConfig;
import software.wings.beans.GraphNode;
import software.wings.beans.NameValuePair;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.sm.states.provision.CloudFormationCreateStackState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public abstract class BaseCloudformationProvisionerMapper extends StepMapper {
  private static final String SECRET_FORMAT = "<+secrets.getValue(\"%s\")>";

  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.MANUAL_EFFORT;
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

  List<CloudformationParametersFileSpec> getParametersFile(Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities, String provisionerId, CloudFormationCreateStackState state) {
    if (isNotEmpty(state.getParametersFilePaths())) {
      StoreConfigWrapper storeConfigWrapper = new StoreConfigWrapper();

      CloudformationParametersFileSpec cloudformationParametersFileSpec = new CloudformationParametersFileSpec();
      CgEntityNode node =
          entities.getOrDefault(CgEntityId.builder().id(provisionerId).type(INFRA_PROVISIONER).build(), null);
      if (node == null || node.getEntity() == null) {
        log.error("Infra provisioner not found");
        GitStore runtimeGitStore = getRuntimeGitStore();
        runtimeGitStore.setPaths(ParameterField.createValueField(state.getParametersFilePaths()));
        storeConfigWrapper.setType(StoreConfigType.GIT);
        storeConfigWrapper.setSpec(runtimeGitStore);
      } else {
        CloudFormationInfrastructureProvisioner provisioner =
            (CloudFormationInfrastructureProvisioner) node.getEntity();
        if (provisioner.provisionByUrl()) {
          S3UrlStoreConfig s3UrlStoreConfig = S3UrlStoreConfig.builder()
                                                  .connectorRef(MigratorUtility.RUNTIME_INPUT)
                                                  .region(ParameterField.createValueField(state.getRegion()))
                                                  .urls(ParameterField.createValueField(state.getParametersFilePaths()))
                                                  .build();
          storeConfigWrapper.setType(StoreConfigType.S3URL);
          storeConfigWrapper.setSpec(s3UrlStoreConfig);
        } else {
          GitStore gitStore = getGitStore(entities, provisionerId);
          gitStore.setPaths(ParameterField.createValueField(state.getParametersFilePaths()));
          storeConfigWrapper.setType(StoreConfigType.GIT);
          storeConfigWrapper.setSpec(gitStore);
        }
      }
      cloudformationParametersFileSpec.setStore(storeConfigWrapper);
      cloudformationParametersFileSpec.setIdentifier("ParametersIdentifier1");
      return Collections.singletonList(cloudformationParametersFileSpec);
    }
    return null;
  }

  ParameterField<List<String>> getSkipStatuses(List<String> stackStatusesToMarkAsSuccess) {
    if (isNotEmpty(stackStatusesToMarkAsSuccess)) {
      return ParameterField.createValueField(stackStatusesToMarkAsSuccess);
    }
    return null;
  }

  CloudformationTemplateFile getTemplateFile(Map<CgEntityId, CgEntityNode> entities, String provisionerId) {
    CgEntityNode node =
        entities.getOrDefault(CgEntityId.builder().id(provisionerId).type(INFRA_PROVISIONER).build(), null);
    if (node == null || node.getEntity() == null) {
      InlineCloudformationTemplateFileSpec inlineSpec = new InlineCloudformationTemplateFileSpec();
      inlineSpec.setTemplateBody(MigratorUtility.RUNTIME_INPUT);
      return CloudformationTemplateFile.builder().type(CloudformationTemplateFileTypes.Inline).spec(inlineSpec).build();
    }

    CloudFormationInfrastructureProvisioner provisioner = (CloudFormationInfrastructureProvisioner) node.getEntity();

    if (provisioner.provisionByGit()) {
      return getTemplateFileByGit(entities, provisionerId);
    } else if (provisioner.provisionByUrl()) {
      return getTemplateFileByS3(provisioner.getTemplateFilePath());
    } else {
      return getTemplateFileByBody(provisioner.getTemplateBody());
    }
  }

  CloudformationTemplateFile getTemplateFileByGit(Map<CgEntityId, CgEntityNode> entities, String provisionerId) {
    RemoteCloudformationTemplateFileSpec remoteSpec = new RemoteCloudformationTemplateFileSpec();
    GitStore gitStore = getGitStore(entities, provisionerId);
    remoteSpec.setStore(StoreConfigWrapper.builder().type(StoreConfigType.GIT).spec(gitStore).build());
    return CloudformationTemplateFile.builder().type(CloudformationTemplateFileTypes.Remote).spec(remoteSpec).build();
  }

  CloudformationTemplateFile getTemplateFileByS3(String templateFilePath) {
    S3UrlCloudformationTemplateFileSpec s3UrlSpec = new S3UrlCloudformationTemplateFileSpec();
    s3UrlSpec.setTemplateUrl(ParameterField.createValueField(templateFilePath));
    return CloudformationTemplateFile.builder().type(CloudformationTemplateFileTypes.S3Url).spec(s3UrlSpec).build();
  }

  CloudformationTemplateFile getTemplateFileByBody(String templateBody) {
    InlineCloudformationTemplateFileSpec inlineSpec = new InlineCloudformationTemplateFileSpec();
    inlineSpec.setTemplateBody(ParameterField.createValueField(templateBody));
    return CloudformationTemplateFile.builder().type(CloudformationTemplateFileTypes.Inline).spec(inlineSpec).build();
  }

  private GitStore getGitStore(Map<CgEntityId, CgEntityNode> entities, String provisionerId) {
    GitStoreBuilder storeBuilder = GitStore.builder().connectorRef(MigratorUtility.RUNTIME_INPUT);

    CgEntityNode node =
        entities.getOrDefault(CgEntityId.builder().id(provisionerId).type(INFRA_PROVISIONER).build(), null);
    if (node == null || node.getEntity() == null) {
      return getRuntimeGitStore();
    }
    CloudFormationInfrastructureProvisioner provisioner = (CloudFormationInfrastructureProvisioner) node.getEntity();
    GitFileConfig gitFileConfig = provisioner.getGitFileConfig();
    if (null == gitFileConfig) {
      return getRuntimeGitStore();
    }

    if (gitFileConfig.isUseBranch() && StringUtils.isNotBlank(gitFileConfig.getBranch())) {
      storeBuilder.gitFetchType(FetchType.BRANCH);
      storeBuilder.branch(ParameterField.createValueField(gitFileConfig.getBranch()));
    } else {
      storeBuilder.gitFetchType(FetchType.COMMIT);
      storeBuilder.commitId(ParameterField.createValueField(gitFileConfig.getCommitId()));
    }

    storeBuilder.paths(ParameterField.createValueField(Collections.singletonList(gitFileConfig.getFilePath())));

    return storeBuilder.build();
  }

  private static GitStore getRuntimeGitStore() {
    return GitStore.builder()
        .connectorRef(MigratorUtility.RUNTIME_INPUT)
        .branch(MigratorUtility.RUNTIME_INPUT)
        .gitFetchType(FetchType.BRANCH)
        .folderPath(MigratorUtility.RUNTIME_INPUT)
        .build();
  }

  static ParameterField<List<String>> getCapabilities(CloudFormationCreateStackState state) {
    if (state.isSpecifyCapabilities() && isNotEmpty(state.getCapabilities())) {
      return ParameterField.createValueField(state.getCapabilities());
    }
    return null;
  }

  static ParameterField<String> getRoleArn(String cloudFormationRoleArn) {
    if (isNotEmpty(cloudFormationRoleArn)) {
      return ParameterField.createValueField(cloudFormationRoleArn);
    }
    return null;
  }

  static CloudformationTags getTags(String tags) {
    if (isNotEmpty(tags)) {
      InlineCloudformationTagsFileSpec tagSpec = new InlineCloudformationTagsFileSpec();
      tagSpec.setContent(ParameterField.createValueField(tags));
      return CloudformationTags.builder().type(Inline).spec(tagSpec).build();
    }
    return null;
  }

  @NotNull
  static ParameterField<String> getStackName(String customStackname, boolean useCustomStackName) {
    return (useCustomStackName && isNotEmpty(customStackname)) ? ParameterField.createValueField(customStackname)
                                                               : MigratorUtility.RUNTIME_INPUT;
  }
}
