/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;
import io.harness.cdng.manifest.yaml.BitbucketStore;
import io.harness.cdng.manifest.yaml.GitLabStore;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.encryption.SecretRefData;
import io.harness.pms.yaml.ParameterField;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.Builder;
import lombok.Data;

public class TerraformStepDataGenerator {
  @Data
  @Builder
  public static class GitStoreConfig {
    private String branch;
    private FetchType fetchType;
    private ParameterField<String> folderPath;
    private ParameterField<String> connectoref;
    private ParameterField<List<String>> varFolderPath;
  }

  @Data
  @Builder
  public static class ArtifactoryStoreConfig {
    private String repositoryName;
    private String connectorRef;
    private List<String> artifacts;
  }

  public static List<String> generateArtifacts() {
    List<String> artifacts = new ArrayList<>();
    artifacts.add("/artifact.tf");
    return artifacts;
  }

  public static TerraformDestroyStepParameters generateDestroyStepPlan(
      StoreConfigType storeType, Object storeConfigFilesParam, Object varStoreConfigFilesParam) {
    StoreConfig storeConfigFiles;
    StoreConfig storeVarFiles;
    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();
    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();
    switch (storeType) {
      case GIT:
      case GITHUB:
      case GITLAB:
      case BITBUCKET:
        TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
            (TerraformStepDataGenerator.GitStoreConfig) storeConfigFilesParam;
        storeConfigFiles =
            GithubStore.builder()
                .branch(ParameterField.createValueField(gitStoreConfigFiles.branch))
                .gitFetchType(gitStoreConfigFiles.fetchType)
                .folderPath(ParameterField.createValueField(gitStoreConfigFiles.folderPath.getValue()))
                .connectorRef(ParameterField.createValueField(gitStoreConfigFiles.connectoref.getValue()))
                .build();
        configFilesWrapper.setStore(StoreConfigWrapper.builder().spec(storeConfigFiles).type(storeType).build());
        TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
            (TerraformStepDataGenerator.GitStoreConfig) varStoreConfigFilesParam;
        storeVarFiles = GithubStore.builder()
                            .branch(ParameterField.createValueField(gitStoreVarFiles.branch))
                            .gitFetchType(gitStoreVarFiles.fetchType)
                            .folderPath(ParameterField.createValueField(gitStoreVarFiles.folderPath.getValue()))
                            .connectorRef(ParameterField.createValueField(gitStoreVarFiles.connectoref.getValue()))
                            .build();
        remoteTerraformVarFileSpec.setStore(StoreConfigWrapper.builder().spec(storeVarFiles).type(storeType).build());
        break;
      case ARTIFACTORY:
        ArtifactoryStoreConfig artifactoryStoreConfigFiles = (ArtifactoryStoreConfig) storeConfigFilesParam;
        storeConfigFiles =
            io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig.builder()
                .repositoryName(ParameterField.createValueField(artifactoryStoreConfigFiles.repositoryName))
                .connectorRef(ParameterField.createValueField(artifactoryStoreConfigFiles.connectorRef))
                .artifactPaths(ParameterField.createValueField(artifactoryStoreConfigFiles.artifacts))
                .build();
        configFilesWrapper.setStore(StoreConfigWrapper.builder().spec(storeConfigFiles).type(storeType).build());
        // Create the store file for the terraform variables
        TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreVarFiles =
            (TerraformStepDataGenerator.ArtifactoryStoreConfig) varStoreConfigFilesParam;
        storeVarFiles = io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig.builder()
                            .repositoryName(ParameterField.createValueField(artifactoryStoreVarFiles.repositoryName))
                            .artifactPaths(ParameterField.createValueField(artifactoryStoreConfigFiles.artifacts))
                            .connectorRef(ParameterField.createValueField(artifactoryStoreVarFiles.connectorRef))
                            .build();
        remoteTerraformVarFileSpec.setStore(StoreConfigWrapper.builder().spec(storeVarFiles).type(storeType).build());
        break;
      default:
        break;
    }
    InlineTerraformVarFileSpec inlineTerraformVarFileSpec = new InlineTerraformVarFileSpec();
    inlineTerraformVarFileSpec.setContent(ParameterField.createValueField("var-content"));
    InlineTerraformBackendConfigSpec inlineTerraformBackendConfigSpec = new InlineTerraformBackendConfigSpec();
    inlineTerraformBackendConfigSpec.setContent(ParameterField.createValueField("back-content"));
    TerraformBackendConfig terraformBackendConfig = new TerraformBackendConfig();
    terraformBackendConfig.setTerraformBackendConfigSpec(inlineTerraformBackendConfigSpec);
    LinkedHashMap<String, TerraformVarFile> varFilesMap = new LinkedHashMap<>();
    varFilesMap.put("var-file-01",
        TerraformVarFile.builder().identifier("var-file-01").type("Inline").spec(inlineTerraformVarFileSpec).build());
    varFilesMap.put("var-file-02",
        TerraformVarFile.builder().identifier("var-file-02").type("Remote").spec(remoteTerraformVarFileSpec).build());
    return TerraformDestroyStepParameters.infoBuilder()
        .provisionerIdentifier(ParameterField.createValueField("provId_$"))
        .configuration(TerraformStepConfigurationParameters.builder()
                           .type(TerraformStepConfigurationType.INLINE)
                           .spec(TerraformExecutionDataParameters.builder()
                                     .configFiles(configFilesWrapper)
                                     .workspace(ParameterField.createValueField("test-workspace"))
                                     .varFiles(varFilesMap)
                                     .isTerraformCloudCli(ParameterField.createValueField(false))
                                     .build())
                           .skipTerraformRefresh(ParameterField.createValueField(false))
                           .build())
        .build();
  }

  public static TerraformApplyStepParameters generateApplyStepPlan(
      StoreConfigType storeType, Object storeConfigFilesParam, Object varStoreConfigFilesParam) {
    StoreConfig storeConfigFiles;
    StoreConfig storeVarFiles;
    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();
    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();
    switch (storeType) {
      case GIT:
      case GITHUB:
      case GITLAB:
      case BITBUCKET:
        // Create the store file for the terraform files
        TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
            (TerraformStepDataGenerator.GitStoreConfig) storeConfigFilesParam;
        storeConfigFiles =
            GithubStore.builder()
                .branch(ParameterField.createValueField(gitStoreConfigFiles.branch))
                .gitFetchType(gitStoreConfigFiles.fetchType)
                .folderPath(ParameterField.createValueField(gitStoreConfigFiles.folderPath.getValue()))
                .connectorRef(ParameterField.createValueField(gitStoreConfigFiles.connectoref.getValue()))
                .build();
        configFilesWrapper.setStore(StoreConfigWrapper.builder().spec(storeConfigFiles).type(storeType).build());
        // Create the store file for the terraform variables
        TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
            (TerraformStepDataGenerator.GitStoreConfig) varStoreConfigFilesParam;
        storeVarFiles = GithubStore.builder()
                            .branch(ParameterField.createValueField(gitStoreVarFiles.branch))
                            .gitFetchType(gitStoreVarFiles.fetchType)
                            .folderPath(ParameterField.createValueField(gitStoreVarFiles.folderPath.getValue()))
                            .connectorRef(ParameterField.createValueField(gitStoreVarFiles.connectoref.getValue()))
                            .build();
        remoteTerraformVarFileSpec.setStore(StoreConfigWrapper.builder().spec(storeVarFiles).type(storeType).build());
        break;
      case ARTIFACTORY:
        // Create the store file for the terraform files
        ArtifactoryStoreConfig artifactoryStoreConfigFiles = (ArtifactoryStoreConfig) storeConfigFilesParam;
        storeConfigFiles =
            io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig.builder()
                .repositoryName(ParameterField.createValueField(artifactoryStoreConfigFiles.repositoryName))
                .connectorRef(ParameterField.createValueField(artifactoryStoreConfigFiles.connectorRef))
                .artifactPaths(ParameterField.createValueField(artifactoryStoreConfigFiles.artifacts))
                .build();
        configFilesWrapper.setStore(StoreConfigWrapper.builder().spec(storeConfigFiles).type(storeType).build());
        // Create the store file for the terraform variables
        TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreVarFiles =
            (TerraformStepDataGenerator.ArtifactoryStoreConfig) varStoreConfigFilesParam;
        storeVarFiles = io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig.builder()
                            .repositoryName(ParameterField.createValueField(artifactoryStoreVarFiles.repositoryName))
                            .artifactPaths(ParameterField.createValueField(artifactoryStoreConfigFiles.artifacts))
                            .connectorRef(ParameterField.createValueField(artifactoryStoreVarFiles.connectorRef))
                            .build();
        remoteTerraformVarFileSpec.setStore(StoreConfigWrapper.builder().spec(storeVarFiles).type(storeType).build());
        break;
      default:
        break;
    }
    InlineTerraformVarFileSpec inlineTerraformVarFileSpec = new InlineTerraformVarFileSpec();
    inlineTerraformVarFileSpec.setContent(ParameterField.createValueField("var-content"));
    InlineTerraformBackendConfigSpec inlineTerraformBackendConfigSpec = new InlineTerraformBackendConfigSpec();
    inlineTerraformBackendConfigSpec.setContent(ParameterField.createValueField("back-content"));
    TerraformBackendConfig terraformBackendConfig = new TerraformBackendConfig();
    terraformBackendConfig.setTerraformBackendConfigSpec(inlineTerraformBackendConfigSpec);
    LinkedHashMap<String, TerraformVarFile> varFilesMap = new LinkedHashMap<>();
    varFilesMap.put("var-file-01",
        TerraformVarFile.builder().identifier("var-file-01").type("Inline").spec(inlineTerraformVarFileSpec).build());
    varFilesMap.put("var-file-02",
        TerraformVarFile.builder().identifier("var-file-02").type("Remote").spec(remoteTerraformVarFileSpec).build());
    return TerraformApplyStepParameters.infoBuilder()
        .provisionerIdentifier(ParameterField.createValueField("provId_$"))
        .configuration(TerraformStepConfigurationParameters.builder()
                           .type(TerraformStepConfigurationType.INLINE)
                           .spec(TerraformExecutionDataParameters.builder()
                                     .configFiles(configFilesWrapper)
                                     .workspace(ParameterField.createValueField("test-workspace"))
                                     .isTerraformCloudCli(ParameterField.createValueField(false))
                                     .varFiles(varFilesMap)
                                     .build())
                           .skipTerraformRefresh(ParameterField.createValueField(false))
                           .build())
        .build();
  }

  public static ArtifactoryStoreDelegateConfig createStoreDelegateConfig() {
    // Create auth with user and password
    ConnectorInfoDTO connectorInfoDTO = getConnectorInfoDTO();

    return ArtifactoryStoreDelegateConfig.builder()
        .repositoryName("repositoryPath")
        .connectorDTO(connectorInfoDTO)
        .succeedIfFileNotFound(false)
        .build();
  }

  public static ConnectorInfoDTO getConnectorInfoDTO() {
    char[] password = {'r', 's', 't', 'u', 'v'};
    ArtifactoryAuthenticationDTO artifactoryAuthenticationDTO =
        ArtifactoryAuthenticationDTO.builder()
            .authType(ArtifactoryAuthType.USER_PASSWORD)
            .credentials(ArtifactoryUsernamePasswordAuthDTO.builder()
                             .username("username")
                             .passwordRef(SecretRefData.builder().decryptedValue(password).build())
                             .build())
            .build();

    // Create DTO connector
    ArtifactoryConnectorDTO artifactoryConnectorDTO = ArtifactoryConnectorDTO.builder()
                                                          .artifactoryServerUrl("http://artifactory.com")
                                                          .auth(artifactoryAuthenticationDTO)
                                                          .delegateSelectors(Collections.singleton("delegateSelector"))
                                                          .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.ARTIFACTORY)
                                            .identifier("connectorRef")
                                            .name("connectorName")
                                            .connectorConfig(artifactoryConnectorDTO)
                                            .build();
    return connectorInfoDTO;
  }

  public static TerraformPlanStepParameters generateStepPlanWithVarFiles(StoreConfigType storeTypeForConfig,
      StoreConfigType storeTypeForVar, Object storeConfigFilesParam, Object varStoreConfigFilesParam,
      boolean generateInlineVarFiles) {
    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();

    generateConfigFileStore(configFilesWrapper, storeTypeForConfig, storeConfigFilesParam);
    RemoteTerraformVarFileSpec remoteVarFilesSpec =
        generateRemoteVarFileSpec(storeTypeForVar, varStoreConfigFilesParam);
    LinkedHashMap<String, TerraformVarFile> varFilesMap =
        generateVarFileSpecs(remoteVarFilesSpec, generateInlineVarFiles);
    InlineTerraformBackendConfigSpec inlineTerraformBackendConfigSpec = new InlineTerraformBackendConfigSpec();
    inlineTerraformBackendConfigSpec.setContent(ParameterField.createValueField("back-content"));
    TerraformBackendConfig terraformBackendConfig = new TerraformBackendConfig();
    terraformBackendConfig.setTerraformBackendConfigSpec(inlineTerraformBackendConfigSpec);
    return TerraformPlanStepParameters.infoBuilder()
        .provisionerIdentifier(ParameterField.createValueField("provId"))
        .configuration(TerraformPlanExecutionDataParameters.builder()
                           .configFiles(configFilesWrapper)
                           .command(TerraformPlanCommand.APPLY)
                           .secretManagerRef(ParameterField.createValueField("secret"))
                           .varFiles(varFilesMap)
                           .environmentVariables(ImmutableMap.of("KEY", ParameterField.createValueField("VAL")))
                           .backendConfig(terraformBackendConfig)
                           .build())
        .build();
  }

  public static TerraformPlanStepParameters generateStepPlanWithRemoteBackendConfig(StoreConfigType storeTypeForConfig,
      StoreConfigType storeType, Object storeConfigFilesParam, Object backendConfigStoreConfigFilesParam) {
    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();

    generateConfigFileStore(configFilesWrapper, storeTypeForConfig, storeConfigFilesParam);
    RemoteTerraformBackendConfigSpec remoteBackendConfigSpec =
        generateRemoteBackendConfigFileSpec(storeType, backendConfigStoreConfigFilesParam);
    TerraformBackendConfig backendConfig = generateBackendConfigFile(remoteBackendConfigSpec, false);
    return TerraformPlanStepParameters.infoBuilder()
        .provisionerIdentifier(ParameterField.createValueField("provId"))
        .configuration(TerraformPlanExecutionDataParameters.builder()
                           .configFiles(configFilesWrapper)
                           .backendConfig(backendConfig)
                           .command(TerraformPlanCommand.APPLY)
                           .secretManagerRef(ParameterField.createValueField("secret"))
                           .environmentVariables(ImmutableMap.of("KEY", ParameterField.createValueField("VAL")))
                           .backendConfig(backendConfig)
                           .build())
        .build();
  }

  public static TerraformPlanStepParameters generateStepPlanFile(
      StoreConfigType storeType, Object storeConfigFilesParam, Object varStoreConfigFilesParam) {
    StoreConfig storeConfigFiles;
    StoreConfig storeVarFiles;
    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();
    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();
    switch (storeType) {
      case GIT:
      case GITHUB:
      case GITLAB:
      case BITBUCKET:
        // Create the store file for the terraform files
        GitStoreConfig gitStoreConfigFiles = (GitStoreConfig) storeConfigFilesParam;
        storeConfigFiles =
            GithubStore.builder()
                .branch(ParameterField.createValueField(gitStoreConfigFiles.branch))
                .gitFetchType(gitStoreConfigFiles.fetchType)
                .folderPath(ParameterField.createValueField(gitStoreConfigFiles.folderPath.getValue()))
                .connectorRef(ParameterField.createValueField(gitStoreConfigFiles.connectoref.getValue()))
                .build();
        configFilesWrapper.setStore(StoreConfigWrapper.builder().spec(storeConfigFiles).type(storeType).build());
        // Create the store file for the terraform variables
        GitStoreConfig gitStoreVarFiles = (GitStoreConfig) varStoreConfigFilesParam;
        storeVarFiles = GithubStore.builder()
                            .branch(ParameterField.createValueField(gitStoreVarFiles.branch))
                            .gitFetchType(gitStoreVarFiles.fetchType)
                            .folderPath(ParameterField.createValueField(gitStoreVarFiles.folderPath.getValue()))
                            .connectorRef(ParameterField.createValueField(gitStoreVarFiles.connectoref.getValue()))
                            .build();
        remoteTerraformVarFileSpec.setStore(StoreConfigWrapper.builder().spec(storeVarFiles).type(storeType).build());
        break;
      case ARTIFACTORY:
        // Create the store file for the terraform files
        ArtifactoryStoreConfig artifactoryStoreConfigFiles = (ArtifactoryStoreConfig) storeConfigFilesParam;
        storeConfigFiles =
            io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig.builder()
                .repositoryName(ParameterField.createValueField(artifactoryStoreConfigFiles.repositoryName))
                .connectorRef(ParameterField.createValueField(artifactoryStoreConfigFiles.connectorRef))
                .build();
        configFilesWrapper.setStore(StoreConfigWrapper.builder().spec(storeConfigFiles).type(storeType).build());
        // Create the store file for the terraform variables
        ArtifactoryStoreConfig artifactoryStoreVarFiles = (ArtifactoryStoreConfig) varStoreConfigFilesParam;
        storeVarFiles = io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig.builder()
                            .repositoryName(ParameterField.createValueField(artifactoryStoreVarFiles.repositoryName))
                            .connectorRef(ParameterField.createValueField(artifactoryStoreVarFiles.connectorRef))
                            .artifactPaths(ParameterField.createValueField(artifactoryStoreVarFiles.artifacts))
                            .build();
        remoteTerraformVarFileSpec.setStore(StoreConfigWrapper.builder().spec(storeVarFiles).type(storeType).build());
        break;
      default:
        break;
    }
    InlineTerraformVarFileSpec inlineTerraformVarFileSpec = new InlineTerraformVarFileSpec();
    inlineTerraformVarFileSpec.setContent(ParameterField.createValueField("var-content"));
    InlineTerraformBackendConfigSpec inlineTerraformBackendConfigSpec = new InlineTerraformBackendConfigSpec();
    inlineTerraformBackendConfigSpec.setContent(ParameterField.createValueField("back-content"));
    TerraformBackendConfig terraformBackendConfig = new TerraformBackendConfig();
    terraformBackendConfig.setType("Inline");
    terraformBackendConfig.setTerraformBackendConfigSpec(inlineTerraformBackendConfigSpec);
    LinkedHashMap<String, TerraformVarFile> varFilesMap = new LinkedHashMap<>();
    varFilesMap.put("var-file-01",
        TerraformVarFile.builder().identifier("var-file-01").type("Inline").spec(inlineTerraformVarFileSpec).build());
    varFilesMap.put("var-file-02",
        TerraformVarFile.builder().identifier("var-file-02").type("Remote").spec(remoteTerraformVarFileSpec).build());
    return TerraformPlanStepParameters.infoBuilder()
        .provisionerIdentifier(ParameterField.createValueField("id"))
        .configuration(TerraformPlanExecutionDataParameters.builder()
                           .workspace(ParameterField.createValueField("test-workspace"))
                           .configFiles(configFilesWrapper)
                           .command(TerraformPlanCommand.APPLY)
                           .secretManagerRef(ParameterField.createValueField("secret"))
                           .varFiles(varFilesMap)
                           .environmentVariables(ImmutableMap.of("KEY", ParameterField.createValueField("VAL")))
                           .backendConfig(terraformBackendConfig)
                           .isTerraformCloudCli(ParameterField.createValueField(false))
                           .skipTerraformRefresh(ParameterField.createValueField(false))
                           .build())
        .build();
  }

  public static LinkedHashMap<String, TerraformVarFile> generateVarFileSpecs(
      RemoteTerraformVarFileSpec remoteTerraformVarFileSpecs, boolean includeInlineFiles) {
    InlineTerraformVarFileSpec inlineTerraformVarFileSpec = new InlineTerraformVarFileSpec();
    inlineTerraformVarFileSpec.setContent(ParameterField.createValueField("var-content"));
    LinkedHashMap<String, TerraformVarFile> varFilesMap = new LinkedHashMap<>();
    if (remoteTerraformVarFileSpecs != null) {
      varFilesMap.put("var-file-1",
          TerraformVarFile.builder().identifier("var-file-1").type("Remote").spec(remoteTerraformVarFileSpecs).build());
      if (includeInlineFiles) {
        varFilesMap.put("var-file-2",
            TerraformVarFile.builder()
                .identifier("var-file-2")
                .type("Inline")
                .spec(inlineTerraformVarFileSpec)
                .build());
      }
    } else {
      varFilesMap.put("var-file-1",
          TerraformVarFile.builder().identifier("var-file-1").type("Inline").spec(inlineTerraformVarFileSpec).build());
    }
    return varFilesMap;
  }

  public static TerraformBackendConfig generateBackendConfigFile(
      RemoteTerraformBackendConfigSpec remoteTerraformBackendConfigSpec, boolean generateInlineSpec) {
    if (generateInlineSpec) {
      InlineTerraformBackendConfigSpec inlineTerraformBackendConfigSpec = new InlineTerraformBackendConfigSpec();
      inlineTerraformBackendConfigSpec.setContent(ParameterField.createValueField("bc-content"));
      return TerraformBackendConfig.builder().type("Inline").spec(inlineTerraformBackendConfigSpec).build();
    }
    return TerraformBackendConfig.builder().type("Remote").spec(remoteTerraformBackendConfigSpec).build();
  }

  public static RemoteTerraformVarFileSpec generateRemoteVarFileSpec(
      StoreConfigType storeType, Object varStoreConfigFilesParam) {
    if (storeType == null) {
      return null;
    }
    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();
    remoteTerraformVarFileSpec.setStore(getStoreConfigWrapper(storeType, varStoreConfigFilesParam));
    return remoteTerraformVarFileSpec;
  }

  public static RemoteTerraformBackendConfigSpec generateRemoteBackendConfigFileSpec(
      StoreConfigType storeType, Object configStoreConfigFilesParam) {
    if (storeType == null) {
      return null;
    }
    RemoteTerraformBackendConfigSpec remoteTerraformBackendConfigFileSpec = new RemoteTerraformBackendConfigSpec();
    remoteTerraformBackendConfigFileSpec.setStore(getStoreConfigWrapper(storeType, configStoreConfigFilesParam));
    return remoteTerraformBackendConfigFileSpec;
  }

  private static StoreConfigWrapper getStoreConfigWrapper(StoreConfigType storeType, Object varStoreConfigFilesParam) {
    StoreConfig storeVarFiles;
    StoreConfigWrapper storeConfigWrapper;
    switch (storeType) {
      case GITLAB:
        GitStoreConfig gitlabStoreVarFiles = (GitStoreConfig) varStoreConfigFilesParam;
        storeVarFiles = GitLabStore.builder()
                            .branch(ParameterField.createValueField(gitlabStoreVarFiles.branch))
                            .gitFetchType(gitlabStoreVarFiles.fetchType)
                            .paths(ParameterField.createValueField(gitlabStoreVarFiles.varFolderPath.getValue()))
                            .folderPath(ParameterField.createValueField(gitlabStoreVarFiles.folderPath.getValue()))
                            .connectorRef(ParameterField.createValueField(gitlabStoreVarFiles.connectoref.getValue()))
                            .build();
        storeConfigWrapper = StoreConfigWrapper.builder().spec(storeVarFiles).type(storeType).build();
        break;
      case GIT:
        GitStoreConfig gitStoreVarFiles = (GitStoreConfig) varStoreConfigFilesParam;
        storeVarFiles = GitStore.builder()
                            .branch(ParameterField.createValueField(gitStoreVarFiles.branch))
                            .gitFetchType(gitStoreVarFiles.fetchType)
                            .paths(ParameterField.createValueField(gitStoreVarFiles.varFolderPath.getValue()))
                            .folderPath(ParameterField.createValueField(gitStoreVarFiles.folderPath.getValue()))
                            .connectorRef(ParameterField.createValueField(gitStoreVarFiles.connectoref.getValue()))
                            .build();
        storeConfigWrapper = StoreConfigWrapper.builder().spec(storeVarFiles).type(storeType).build();
        break;
      case GITHUB:
      case BITBUCKET:
        // Create the store file for the terraform variables
        GitStoreConfig githubStoreVarFiles = (GitStoreConfig) varStoreConfigFilesParam;
        storeVarFiles =
            GithubStore.builder()
                .branch(ParameterField.createValueField(githubStoreVarFiles.branch))
                .gitFetchType(githubStoreVarFiles.fetchType)
                .paths(ParameterField.createValueField(
                    githubStoreVarFiles.varFolderPath == null ? null : githubStoreVarFiles.varFolderPath.getValue()))
                .folderPath(ParameterField.createValueField(githubStoreVarFiles.folderPath.getValue()))
                .connectorRef(ParameterField.createValueField(githubStoreVarFiles.connectoref.getValue()))
                .build();
        storeConfigWrapper = StoreConfigWrapper.builder().spec(storeVarFiles).type(storeType).build();
        break;
      case ARTIFACTORY:
        // Create the store file for the terraform variables
        ArtifactoryStoreConfig artifactoryStoreVarFiles = (ArtifactoryStoreConfig) varStoreConfigFilesParam;
        storeVarFiles = io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig.builder()
                            .repositoryName(ParameterField.createValueField(artifactoryStoreVarFiles.repositoryName))
                            .connectorRef(ParameterField.createValueField(artifactoryStoreVarFiles.connectorRef))
                            .artifactPaths(ParameterField.createValueField(artifactoryStoreVarFiles.artifacts))
                            .build();
        storeConfigWrapper = StoreConfigWrapper.builder().spec(storeVarFiles).type(storeType).build();
        break;
      default:
        return null;
    }
    return storeConfigWrapper;
  }

  public static void generateConfigFileStore(
      TerraformConfigFilesWrapper configFilesWrapper, StoreConfigType storeType, Object storeConfigFilesParam) {
    StoreConfig storeConfigFiles;
    switch (storeType) {
      case GIT:
        GitStoreConfig gitStoreConfigFiles = (GitStoreConfig) storeConfigFilesParam;
        storeConfigFiles =
            GitStore.builder()
                .branch(ParameterField.createValueField(gitStoreConfigFiles.branch))
                .gitFetchType(gitStoreConfigFiles.fetchType)
                .folderPath(ParameterField.createValueField(gitStoreConfigFiles.folderPath.getValue()))
                .connectorRef(ParameterField.createValueField(gitStoreConfigFiles.connectoref.getValue()))
                .build();
        configFilesWrapper.setStore(StoreConfigWrapper.builder().spec(storeConfigFiles).type(storeType).build());
        break;
      case GITHUB:
      case GITLAB:
        // Create the store file for the terraform files
        GitStoreConfig githubStoreConfigFiles = (GitStoreConfig) storeConfigFilesParam;
        storeConfigFiles =
            GithubStore.builder()
                .branch(ParameterField.createValueField(githubStoreConfigFiles.branch))
                .gitFetchType(githubStoreConfigFiles.fetchType)
                .folderPath(ParameterField.createValueField(githubStoreConfigFiles.folderPath.getValue()))
                .connectorRef(ParameterField.createValueField(githubStoreConfigFiles.connectoref.getValue()))
                .build();
        configFilesWrapper.setStore(StoreConfigWrapper.builder().spec(storeConfigFiles).type(storeType).build());
        break;
      case BITBUCKET:
        // Create the store file for the terraform files
        GitStoreConfig bitbucketStoreConfigFiles = (GitStoreConfig) storeConfigFilesParam;
        storeConfigFiles =
            BitbucketStore.builder()
                .branch(ParameterField.createValueField(bitbucketStoreConfigFiles.branch))
                .gitFetchType(bitbucketStoreConfigFiles.fetchType)
                .folderPath(ParameterField.createValueField(bitbucketStoreConfigFiles.folderPath.getValue()))
                .connectorRef(ParameterField.createValueField(bitbucketStoreConfigFiles.connectoref.getValue()))
                .build();
        configFilesWrapper.setStore(StoreConfigWrapper.builder().spec(storeConfigFiles).type(storeType).build());
        break;
      case ARTIFACTORY:
        // Create the store file for the terraform files
        ArtifactoryStoreConfig artifactoryStoreConfigFiles = (ArtifactoryStoreConfig) storeConfigFilesParam;
        storeConfigFiles =
            io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig.builder()
                .repositoryName(ParameterField.createValueField(artifactoryStoreConfigFiles.repositoryName))
                .connectorRef(ParameterField.createValueField(artifactoryStoreConfigFiles.connectorRef))
                .artifactPaths(ParameterField.createValueField(artifactoryStoreConfigFiles.artifacts))
                .build();
        configFilesWrapper.setStore(StoreConfigWrapper.builder().spec(storeConfigFiles).type(storeType).build());
        break;
      default:
        break;
    }
  }
}
