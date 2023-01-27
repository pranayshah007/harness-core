/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.provision.terraform.TerraformStepDataGenerator;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class TerragruntTestStepUtils {
  public Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "test-account")
        .putSetupAbstractions("projectIdentifier", "test-project")
        .putSetupAbstractions("orgIdentifier", "test-org")
        .build();
  }

  public TerragruntConfigFilesWrapper createConfigFilesWrapper() {
    TerragruntConfigFilesWrapper configFilesWrapper = new TerragruntConfigFilesWrapper();
    StoreConfig storeConfigFiles;

    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terragrunt-configFiles"))
            .build();

    storeConfigFiles =
        GithubStore.builder()
            .branch(ParameterField.createValueField(gitStoreConfigFiles.getBranch()))
            .gitFetchType(gitStoreConfigFiles.getFetchType())
            .folderPath(ParameterField.createValueField(gitStoreConfigFiles.getFolderPath().getValue()))
            .connectorRef(ParameterField.createValueField(gitStoreConfigFiles.getConnectoref().getValue()))
            .build();
    configFilesWrapper.setStore(
        StoreConfigWrapper.builder().spec(storeConfigFiles).type(StoreConfigType.GITHUB).build());

    return configFilesWrapper;
  }

  public LinkedHashMap<String, TerragruntVarFile> createVarFilesRemote() {
    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("varFiles/"))
            .connectoref(ParameterField.createValueField("terragrunt-varFiles"))
            .build();

    StoreConfig storeVarFiles;
    RemoteTerragruntVarFileSpec remoteTerragruntVarFileSpec = new RemoteTerragruntVarFileSpec();

    storeVarFiles = GithubStore.builder()
                        .repoName(ParameterField.createValueField("test-repo-name-var-file"))
                        .branch(ParameterField.createValueField(gitStoreVarFiles.getBranch()))
                        .gitFetchType(gitStoreVarFiles.getFetchType())
                        .paths(ParameterField.createValueField(List.of("path/to")))
                        .folderPath(ParameterField.createValueField(gitStoreVarFiles.getFolderPath().getValue()))
                        .connectorRef(ParameterField.createValueField(gitStoreVarFiles.getConnectoref().getValue()))
                        .build();

    remoteTerragruntVarFileSpec.setStore(
        StoreConfigWrapper.builder().spec(storeVarFiles).type(StoreConfigType.GITHUB).build());

    LinkedHashMap<String, TerragruntVarFile> varFilesMap = new LinkedHashMap<>();
    varFilesMap.put("var-file-01",
        TerragruntVarFile.builder().identifier("var-file-01").type("Remote").spec(remoteTerragruntVarFileSpec).build());
    return varFilesMap;
  }

  public TerragruntBackendConfig createRemoteBackendConfig() {
    TerraformStepDataGenerator.GitStoreConfig gitStoreBackend =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("backend/"))
            .connectoref(ParameterField.createValueField("terragrunt-backendFile"))
            .build();

    StoreConfig storeBackend;
    storeBackend = GithubStore.builder()
                       .repoName(ParameterField.createValueField("test-repo-name-be-file"))
                       .branch(ParameterField.createValueField(gitStoreBackend.getBranch()))
                       .gitFetchType(gitStoreBackend.getFetchType())
                       .folderPath(ParameterField.createValueField(gitStoreBackend.getFolderPath().getValue()))
                       .connectorRef(ParameterField.createValueField(gitStoreBackend.getConnectoref().getValue()))
                       .build();

    RemoteTerragruntBackendConfigSpec remoteTerragruntBackendConfigSpec = new RemoteTerragruntBackendConfigSpec();
    remoteTerragruntBackendConfigSpec.setStore(
        StoreConfigWrapper.builder().spec(storeBackend).type(StoreConfigType.GITHUB).build());

    TerragruntBackendConfig terragruntBackendConfig = new TerragruntBackendConfig();
    terragruntBackendConfig.setType("Remote");
    terragruntBackendConfig.setTerragruntBackendConfigSpec(remoteTerragruntBackendConfigSpec);
    return terragruntBackendConfig;
  }

  public GitStoreDelegateConfig createGitStoreDelegateConfig() {
    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .delegateSelectors(Collections.singleton("delegateName"))
                                    .url("https://github.com/wings-software")
                                    .branchName("master")
                                    .build();
    return GitStoreDelegateConfig.builder()
        .branch("master")
        .connectorName("terragrunt")
        .gitConfigDTO(gitConfigDTO)
        .build();
  }

  public LinkedHashMap<String, TerragruntVarFile> createVarFilesInline() {
    InlineTerragruntVarFileSpec inlineTerragruntVarFileSpec = new InlineTerragruntVarFileSpec();
    inlineTerragruntVarFileSpec.setContent(ParameterField.createValueField("test-varFile-Content"));
    LinkedHashMap<String, TerragruntVarFile> varFilesMap = new LinkedHashMap<>();
    varFilesMap.put("var-file-01",
        TerragruntVarFile.builder().identifier("var-file-01").type("Inline").spec(inlineTerragruntVarFileSpec).build());
    return varFilesMap;
  }

  public TerragruntBackendConfig createInlineBackendConfig() {
    InlineTerragruntBackendConfigSpec inlineTerragruntBackendConfigSpec = new InlineTerragruntBackendConfigSpec();
    inlineTerragruntBackendConfigSpec.setContent(ParameterField.createValueField("back-content"));
    return TerragruntBackendConfig.builder()
        .type(TerragruntBackendFileTypes.Inline)
        .spec(inlineTerragruntBackendConfigSpec)
        .build();
  }

  public TerragruntModuleConfig createTerragruntModuleConfig() {
    TerragruntModuleConfig terragruntModuleConfig = new TerragruntModuleConfig();
    terragruntModuleConfig.terragruntRunType = TerragruntRunType.RUN_MODULE;
    terragruntModuleConfig.path = ParameterField.createValueField("test-path");
    return terragruntModuleConfig;
  }
}
