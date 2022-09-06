/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.provision.azure.AzureCommonHelper.BLUEPRINT_IDENTIFIER;
import static io.harness.cdng.provision.azure.AzureCommonHelper.TEMPLATE_FILE_IDENTIFIER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.encryption.SecretRefData;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.FetchFilesResult.FetchFilesResultBuilder;
import io.harness.git.model.GitFile;
import io.harness.pms.contracts.ambiance.Ambiance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@OwnedBy(CDP)
public class AzureTestHelper {
  public Map<String, FetchFilesResult> createFetchFilesResultMap(
      boolean withBP, boolean withParameter, boolean withTemplate) {
    Map<String, FetchFilesResult> fetchFilesResultMap = new HashMap<>();
    FetchFilesResultBuilder fetchFilesResult = FetchFilesResult.builder();
    if (withBP) {
      fetchFilesResultMap.put("bluePrint",
          fetchFilesResult
              .files(new ArrayList<>(
                  Arrays.asList(GitFile.builder().fileContent("bluePrint").filePath("blueprint.json").build(),
                      GitFile.builder().fileContent("AssignContent").filePath("assign.json").build(),
                      GitFile.builder().filePath("artifacts/superfile").fileContent("ArtifactsContent").build())))
              .build());
    }
    if (withParameter) {
      fetchFilesResultMap.put("templateFile",
          fetchFilesResult
              .files(new ArrayList<>(
                  Arrays.asList(GitFile.builder().fileContent("templateFile").filePath("templateFile.json").build())))
              .build());
    }
    if (withTemplate) {
      fetchFilesResultMap.put("parameterFile",
          fetchFilesResult
              .files(new ArrayList<>(
                  Arrays.asList(GitFile.builder().fileContent("parameterFile").filePath("parameterFile.json").build())))
              .build());
    }
    return fetchFilesResultMap;
  }

  public ConnectorInfoDTO createAzureConnectorDTO() {
    return ConnectorInfoDTO.builder()
        .name("connectorName")
        .connectorConfig(
            AzureConnectorDTO.builder()
                .azureEnvironmentType(AzureEnvironmentType.AZURE_US_GOVERNMENT)
                .credential(
                    AzureCredentialDTO.builder()
                        .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                        .config(
                            AzureManualDetailsDTO.builder()
                                .clientId("client-id")
                                .tenantId("tenant-id")
                                .authDTO(AzureAuthDTO.builder()
                                             .azureSecretType(AzureSecretType.SECRET_KEY)
                                             .credentials(AzureClientSecretKeyDTO.builder()
                                                              .secretKey(SecretRefData.builder()
                                                                             .decryptedValue("secret-key".toCharArray())
                                                                             .build())
                                                              .build())
                                             .build())
                                .build())
                        .build())
                .build())
        .build();
  }

  public Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "test-account");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");

    return Ambiance.newBuilder()
        .putAllSetupAbstractions(setupAbstractions)
        .setStageExecutionId("stageExecutionId")
        .build();
  }

  public GitFetchFilesConfig getARMTemplate() {
    return GitFetchFilesConfig.builder()
        .manifestType("Azure Template")
        .identifier(TEMPLATE_FILE_IDENTIFIER)
        .gitStoreDelegateConfig(GitStoreDelegateConfig.builder().build())
        .build();
  }

  public GitFetchFilesConfig getBPTemplate() {
    return GitFetchFilesConfig.builder()
        .manifestType("Azure Template")
        .identifier(BLUEPRINT_IDENTIFIER)
        .gitStoreDelegateConfig(GitStoreDelegateConfig.builder().build())
        .build();
  }
}
