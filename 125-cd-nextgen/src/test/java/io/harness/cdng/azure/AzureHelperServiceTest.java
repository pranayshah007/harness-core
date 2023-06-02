/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.YUVRAJ;
import static io.harness.rule.OwnerRule.vivekveman;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.azure.AcrResponseDTO;
import io.harness.delegate.beans.azure.response.AzureDelegateTaskResponse;
import io.harness.delegate.beans.azure.response.AzureRegistriesResponse;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskParams;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskType;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.encryption.Scope;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidIdentifierRefException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class AzureHelperServiceTest extends CDNGTestBase {
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String FILE_PATH = "file/path";
  private static final String CONFIG_FILE_NAME = "configFileName";
  private static final String CONFIG_FILE_IDENTIFIER = "configFileIdentifier";
  private static final String CONFIG_FILE_PARENT_IDENTIFIER = "configFileParentIdentifier";
  private static final String CONNECTOR_REF = "connectorRef";
  private static final String CONNECTOR_NAME = "connectorName";
  private static final String MASTER = "master";
  private static final String COMMIT_ID = "commitId";
  private static final String REPO_NAME = "repoName";

  @Mock private ConnectorService connectorService;
  @Mock private FileStoreService fileStoreService;
  @Mock private CDExpressionResolver cdExpressionResolver;
  @Mock private NGEncryptedDataService ngEncryptedDataService;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock ExceptionManager exceptionManager;

  @InjectMocks private AzureHelperService azureHelperService;

  @Before
  public void prepare() {
    doNothing().when(cdExpressionResolver).updateStoreConfigExpressions(any(), any());
  }

  @Test
  @Owner(developers = {TMACARI, ABOSII})
  @Category(UnitTests.class)
  public void testValidateSettingsStoreReferences() {
    when(fileStoreService.getWithChildrenByPath(ACCOUNT_IDENTIFIER, null, null, FILE_PATH, false))
        .thenReturn(Optional.of(getFileStoreNode()));
    assertThatCode(
        () -> azureHelperService.validateSettingsStoreReferences(getStoreConfigWrapper(), getAmbiance(), "Test Entity"))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = {TMACARI, ABOSII})
  @Category(UnitTests.class)
  public void testValidateSettingsStoreReferencesFileNotFound() {
    when(fileStoreService.getWithChildrenByPath(ACCOUNT_IDENTIFIER, null, null, FILE_PATH, false))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
        () -> azureHelperService.validateSettingsStoreReferences(getStoreConfigWrapper(), getAmbiance(), "Test Entity"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("file not found in File Store with ref");
  }

  @Test
  @Owner(developers = {TMACARI, ABOSII})
  @Category(UnitTests.class)
  public void testValidateSettingsStoreReferencesMoreThanOneFileProvided() {
    StoreConfigWrapper storeConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(HarnessStore.builder()
                      .files(ParameterField.createValueField(asList(getHarnessFile(), getHarnessFile())))
                      .build())
            .build();

    assertThatThrownBy(
        () -> azureHelperService.validateSettingsStoreReferences(storeConfigWrapper, getAmbiance(), "Test Entity"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Only one file should be provided for Test Entity, store kind");
  }

  @Test
  @Owner(developers = {TMACARI, ABOSII})
  @Category(UnitTests.class)
  public void testValidateSettingsStoreReferencesNoFilePaths() {
    StoreConfigWrapper storeConfigWrapper =
        StoreConfigWrapper.builder()
            .spec(HarnessStore.builder().files(ParameterField.createValueField(Collections.emptyList())).build())
            .build();

    assertThatThrownBy(
        () -> azureHelperService.validateSettingsStoreReferences(storeConfigWrapper, getAmbiance(), "Test Entity"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot find any file for Test Entity, store kind");
  }

  @Test
  @Owner(developers = {TMACARI, ABOSII})
  @Category(UnitTests.class)
  public void testValidateSettingsStoreReferencesGitStore() {
    Ambiance ambiance = getAmbiance();
    when(connectorService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_REF))
        .thenReturn(Optional.of(
            ConnectorResponseDTO.builder()
                .connector(ConnectorInfoDTO.builder().identifier(CONNECTOR_REF).name(CONNECTOR_NAME).build())
                .entityValidityDetails(EntityValidityDetails.builder().valid(true).build())
                .build()));

    StoreConfigWrapper storeConfigWrapper = getStoreConfigWrapperWithGitStore();
    assertThatCode(
        () -> azureHelperService.validateSettingsStoreReferences(storeConfigWrapper, ambiance, "Test Entity"))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateSettingsStoreReferencesNoGitConnector() {
    Ambiance ambiance = getAmbiance();
    when(connectorService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_REF))
        .thenReturn(Optional.empty());
    StoreConfigWrapper storeConfigWrapper = getStoreConfigWrapperWithGitStore();
    assertThatThrownBy(
        () -> azureHelperService.validateSettingsStoreReferences(storeConfigWrapper, ambiance, "Test Entity"))
        .hasMessageContaining("Connector not found with identifier:");
  }

  private StoreConfigWrapper getStoreConfigWrapper() {
    return StoreConfigWrapper.builder()
        .type(StoreConfigType.HARNESS)
        .spec(HarnessStore.builder().files(getFiles()).build())
        .build();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateHarnessStoreNoFilesConfigured() {
    Ambiance ambiance = getAmbiance();
    StoreConfigWrapper storeConfigWrapper = getStoreConfigWrapperWithHarnessStore(null, null);
    assertThatThrownBy(
        () -> azureHelperService.validateSettingsStoreReferences(storeConfigWrapper, ambiance, "Test Entity"))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateHarnessStoreBothSecretFilesAndFilesConfigured() {
    Ambiance ambiance = getAmbiance();
    StoreConfigWrapper storeConfigWrapper = getStoreConfigWrapperWithHarnessStore(
        Collections.singletonList("file"), Collections.singletonList("secretFile"));
    assertThatThrownBy(
        () -> azureHelperService.validateSettingsStoreReferences(storeConfigWrapper, ambiance, "Test Entity"))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateHarnessStoreNoSecretFileFound() {
    Ambiance ambiance = getAmbiance();
    StoreConfigWrapper storeConfigWrapper =
        getStoreConfigWrapperWithHarnessStore(null, Collections.singletonList("account.secretFile"));

    assertThatThrownBy(
        () -> azureHelperService.validateSettingsStoreReferences(storeConfigWrapper, ambiance, "Test Entity"))
        .isInstanceOf(InvalidArgumentsException.class);

    verify(ngEncryptedDataService).get(ACCOUNT_IDENTIFIER, null, null, "secretFile");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateHarnessStoreValidSecretFileRef() {
    Ambiance ambiance = getAmbiance();
    StoreConfigWrapper storeConfigWrapper =
        getStoreConfigWrapperWithHarnessStore(null, Collections.singletonList("account.secretFile"));

    doReturn(mock(NGEncryptedData.class))
        .when(ngEncryptedDataService)
        .get(ACCOUNT_IDENTIFIER, null, null, "secretFile");

    assertThatCode(
        () -> azureHelperService.validateSettingsStoreReferences(storeConfigWrapper, ambiance, "Test Entity"))
        .doesNotThrowAnyException();
    verify(ngEncryptedDataService).get(ACCOUNT_IDENTIFIER, null, null, "secretFile");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetConnectorErrorwithoutconnector() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier("accountId")
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .scope(Scope.PROJECT)
                                      .build();

    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder()
                                                    .connector(ConnectorInfoDTO.builder()
                                                                   .connectorType(ConnectorType.AZURE)
                                                                   .connectorConfig(AzureConnectorDTO.builder().build())
                                                                   .build())
                                                    .build();

    when(connectorService.get("accountId", ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> azureHelperService.getConnector(identifierRef))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Connector not found for identifier : [identifier] with scope: [PROJECT]");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetConnectorWithDifferentType() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier("accountId")
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .scope(Scope.PROJECT)
                                      .build();

    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder()
                                                    .connector(ConnectorInfoDTO.builder()
                                                                   .connectorType(ConnectorType.ARTIFACTORY)
                                                                   .connectorConfig(AzureConnectorDTO.builder().build())
                                                                   .build())
                                                    .build();

    when(connectorService.get("accountId", ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier"))
        .thenReturn(Optional.of(connectorResponseDTO));

    assertThatThrownBy(() -> azureHelperService.getConnector(identifierRef))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Connector with identifier [identifier] with scope: [PROJECT] is not an Azure connector. Please check you configuration.");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetConnectorWithRuntimeConnector() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier("accountId")
                                      .identifier("<+input>")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .scope(Scope.PROJECT)
                                      .build();

    assertThatThrownBy(() -> azureHelperService.getConnector(identifierRef))
        .isInstanceOf(InvalidIdentifierRefException.class)
        .hasMessageContaining(
            "Azure Connector is required to fetch this field. You can make this field Runtime input otherwise.");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetConnector() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier("accountId")
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .scope(Scope.PROJECT)
                                      .build();

    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder()
                                                    .connector(ConnectorInfoDTO.builder()
                                                                   .connectorType(ConnectorType.AZURE)
                                                                   .connectorConfig(AzureConnectorDTO.builder().build())
                                                                   .build())
                                                    .build();

    when(connectorService.get("accountId", ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier"))
        .thenReturn(Optional.of(connectorResponseDTO));

    AzureConnectorDTO azureConnectorDTO = azureHelperService.getConnector(identifierRef);

    assertThat(azureConnectorDTO).isEqualTo(AzureConnectorDTO.builder().build());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetBaseNGAccess() {
    BaseNGAccess expected = BaseNGAccess.builder()
                                .accountIdentifier("accountId")
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJECT_IDENTIFIER)
                                .build();
    BaseNGAccess result = azureHelperService.getBaseNGAccess("accountId", ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(result).isEqualTo(expected);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testEncryptionDetails() {
    AzureConnectorDTO azureArtifactsConnectorDTO = AzureConnectorDTO.builder().build();
    BaseNGAccess expected = BaseNGAccess.builder()
                                .accountIdentifier("accountId")
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJECT_IDENTIFIER)
                                .build();
    List<EncryptedDataDetail> encryptedDataDetails =
        azureHelperService.getEncryptionDetails(azureArtifactsConnectorDTO, expected);
    assertThat(encryptedDataDetails).isEqualTo(new ArrayList<>());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testEncryptionDetailsManual() {
    AzureConnectorDTO azureArtifactsConnectorDTO =
        AzureConnectorDTO.builder()
            .credential(AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                            .config(AzureManualDetailsDTO.builder().authDTO(AzureAuthDTO.builder().build()).build())
                            .build())
            .build();
    BaseNGAccess expected = BaseNGAccess.builder()
                                .accountIdentifier("accountId")
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJECT_IDENTIFIER)
                                .build();

    List<EncryptedDataDetail> ls = new ArrayList<>();

    ls.add(EncryptedDataDetail.builder().build());

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(ls);

    List<EncryptedDataDetail> encryptedDataDetails =
        azureHelperService.getEncryptionDetails(azureArtifactsConnectorDTO, expected);

    assertThat(encryptedDataDetails).isEqualTo(ls);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testExecuteSyncTask() {
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier("accountId")
                                    .orgIdentifier(ORG_IDENTIFIER)
                                    .projectIdentifier(PROJECT_IDENTIFIER)
                                    .build();
    AzureConnectorDTO azureArtifactsConnectorDTO =
        AzureConnectorDTO.builder()
            .credential(AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                            .config(AzureManualDetailsDTO.builder().authDTO(AzureAuthDTO.builder().build()).build())
                            .build())
            .build();
    List<EncryptedDataDetail> ls = new ArrayList<>();

    ls.add(EncryptedDataDetail.builder().build());

    Set<String> delegateSelectors = new HashSet<>();

    delegateSelectors.add("first");

    AzureTaskParams azureTaskParamsTaskParams = AzureTaskParams.builder()
                                                    .azureTaskType(AzureTaskType.LIST_CONTAINER_REGISTRIES)
                                                    .azureConnector(azureArtifactsConnectorDTO)
                                                    .delegateSelectors(delegateSelectors)
                                                    .encryptionDetails(ls)
                                                    .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);
    ArtifactTaskResponse artifactTaskResponseresult = (ArtifactTaskResponse) azureHelperService.executeSyncTask(
        azureTaskParamsTaskParams, baseNGAccess, "Azure list registries task failure due to error");

    assertThat(artifactTaskResponseresult.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    assertThat(artifactTaskResponseresult.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testExecuteSyncTaskOptional() {
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier("accountId")
                                    .orgIdentifier(ORG_IDENTIFIER)
                                    .projectIdentifier(PROJECT_IDENTIFIER)
                                    .build();
    AzureConnectorDTO azureArtifactsConnectorDTO =
        AzureConnectorDTO.builder()
            .credential(AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                            .config(AzureManualDetailsDTO.builder().authDTO(AzureAuthDTO.builder().build()).build())
                            .build())
            .build();
    List<EncryptedDataDetail> ls = new ArrayList<>();

    ls.add(EncryptedDataDetail.builder().build());

    Set<String> delegateSelectors = new HashSet<>();

    delegateSelectors.add("first");

    AzureTaskParams azureTaskParamsTaskParams = AzureTaskParams.builder()
                                                    .azureTaskType(AzureTaskType.LIST_CONTAINER_REGISTRIES)
                                                    .azureConnector(azureArtifactsConnectorDTO)
                                                    .delegateSelectors(delegateSelectors)
                                                    .encryptionDetails(ls)
                                                    .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);
    ArtifactTaskResponse artifactTaskResponseresult = (ArtifactTaskResponse) azureHelperService.executeSyncTask(
        azureTaskParamsTaskParams, baseNGAccess, "Azure list registries task failure due to error", Optional.of(10));

    assertThat(artifactTaskResponseresult.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    assertThat(artifactTaskResponseresult.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testExecuteSyncTaskAmbiance() {
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier("accountId")
                                    .orgIdentifier(ORG_IDENTIFIER)
                                    .projectIdentifier(PROJECT_IDENTIFIER)
                                    .build();
    Set<String> delegateSelectors = new HashSet<>();

    delegateSelectors.add("first");
    AzureConnectorDTO azureArtifactsConnectorDTO =
        AzureConnectorDTO.builder()
            .credential(AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                            .config(AzureManualDetailsDTO.builder().authDTO(AzureAuthDTO.builder().build()).build())
                            .build())
            .delegateSelectors(delegateSelectors)
            .build();

    List<EncryptedDataDetail> ls = new ArrayList<>();

    ls.add(EncryptedDataDetail.builder().build());

    AcrArtifactDelegateRequest acrArtifactDelegateRequest = AcrArtifactDelegateRequest.builder()
                                                                .azureConnectorDTO(azureArtifactsConnectorDTO)
                                                                .encryptedDataDetails(ls)
                                                                .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);
    ArtifactTaskResponse artifactTaskResponseresult = (ArtifactTaskResponse) azureHelperService.executeSyncTask(
        null, acrArtifactDelegateRequest, baseNGAccess, "Azure list registries task failure due to error");

    assertThat(artifactTaskResponseresult.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    assertThat(artifactTaskResponseresult.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testExecuteSyncErrorResponse() {
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier("accountId")
                                    .orgIdentifier(ORG_IDENTIFIER)
                                    .projectIdentifier(PROJECT_IDENTIFIER)
                                    .build();
    AzureConnectorDTO azureArtifactsConnectorDTO =
        AzureConnectorDTO.builder()
            .credential(AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                            .config(AzureManualDetailsDTO.builder().authDTO(AzureAuthDTO.builder().build()).build())
                            .build())
            .build();
    List<EncryptedDataDetail> ls = new ArrayList<>();

    ls.add(EncryptedDataDetail.builder().build());

    Set<String> delegateSelectors = new HashSet<>();

    delegateSelectors.add("first");

    AzureTaskParams azureTaskParamsTaskParams = AzureTaskParams.builder()
                                                    .azureTaskType(AzureTaskType.LIST_CONTAINER_REGISTRIES)
                                                    .azureConnector(azureArtifactsConnectorDTO)
                                                    .delegateSelectors(delegateSelectors)
                                                    .encryptionDetails(ls)
                                                    .build();

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ErrorNotifyResponseData.builder().errorMessage("Testing").build());

    assertThatThrownBy(()
                           -> azureHelperService.executeSyncTask(null, azureTaskParamsTaskParams, baseNGAccess,
                               "Azure list registries task failure due to error"))
        .isInstanceOf(ArtifactServerException.class)
        .hasMessage("Azure list registries task failure due to error - Testing");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testExecuteSyncFailure() {
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier("accountId")
                                    .orgIdentifier(ORG_IDENTIFIER)
                                    .projectIdentifier(PROJECT_IDENTIFIER)
                                    .build();
    AzureConnectorDTO azureArtifactsConnectorDTO =
        AzureConnectorDTO.builder()
            .credential(AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                            .config(AzureManualDetailsDTO.builder().authDTO(AzureAuthDTO.builder().build()).build())
                            .build())
            .build();
    List<EncryptedDataDetail> ls = new ArrayList<>();

    ls.add(EncryptedDataDetail.builder().build());

    Set<String> delegateSelectors = new HashSet<>();

    delegateSelectors.add("first");

    AzureTaskParams azureTaskParamsTaskParams = AzureTaskParams.builder()
                                                    .azureTaskType(AzureTaskType.LIST_CONTAINER_REGISTRIES)
                                                    .azureConnector(azureArtifactsConnectorDTO)
                                                    .delegateSelectors(delegateSelectors)
                                                    .encryptionDetails(ls)
                                                    .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                    .errorCode(ErrorCode.DEFAULT_ERROR_CODE)
                                                    .errorMessage("Test failed")
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .build();

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);

    assertThatThrownBy(()
                           -> azureHelperService.executeSyncTask(null, azureTaskParamsTaskParams, baseNGAccess,
                               "Azure list registries task failure due to error"))
        .isInstanceOf(ArtifactServerException.class)
        .hasMessage("Azure list registries task failure due to error");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testExecuteSyncFailureWithAcrResponse() {
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier("accountId")
                                    .orgIdentifier(ORG_IDENTIFIER)
                                    .projectIdentifier(PROJECT_IDENTIFIER)
                                    .build();
    AzureConnectorDTO azureArtifactsConnectorDTO =
        AzureConnectorDTO.builder()
            .credential(AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                            .config(AzureManualDetailsDTO.builder().authDTO(AzureAuthDTO.builder().build()).build())
                            .build())
            .build();
    List<EncryptedDataDetail> ls = new ArrayList<>();

    ls.add(EncryptedDataDetail.builder().build());

    Set<String> delegateSelectors = new HashSet<>();

    delegateSelectors.add("first");

    AzureTaskParams azureTaskParamsTaskParams = AzureTaskParams.builder()
                                                    .azureTaskType(AzureTaskType.LIST_CONTAINER_REGISTRIES)
                                                    .azureConnector(azureArtifactsConnectorDTO)
                                                    .delegateSelectors(delegateSelectors)
                                                    .encryptionDetails(ls)
                                                    .build();

    AzureDelegateTaskResponse artifactTaskResponse = AzureRegistriesResponse.builder()
                                                         .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                         .errorSummary("Test failed")
                                                         .build();

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);

    assertThatThrownBy(()
                           -> azureHelperService.executeSyncTask(null, azureTaskParamsTaskParams, baseNGAccess,
                               "Azure list registries task failure due to error"))
        .isInstanceOf(ArtifactServerException.class)
        .hasMessage("Azure list registries task failure due to error - Test failed");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testExecuteSyncFailureDelegateException() {
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier("accountId")
                                    .orgIdentifier(ORG_IDENTIFIER)
                                    .projectIdentifier(PROJECT_IDENTIFIER)
                                    .build();
    AzureConnectorDTO azureArtifactsConnectorDTO =
        AzureConnectorDTO.builder()
            .credential(AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                            .config(AzureManualDetailsDTO.builder().authDTO(AzureAuthDTO.builder().build()).build())
                            .build())
            .build();
    List<EncryptedDataDetail> ls = new ArrayList<>();

    ls.add(EncryptedDataDetail.builder().build());

    Set<String> delegateSelectors = new HashSet<>();

    delegateSelectors.add("first");

    AzureTaskParams azureTaskParamsTaskParams = AzureTaskParams.builder()
                                                    .azureTaskType(AzureTaskType.LIST_CONTAINER_REGISTRIES)
                                                    .azureConnector(azureArtifactsConnectorDTO)
                                                    .delegateSelectors(delegateSelectors)
                                                    .encryptionDetails(ls)
                                                    .build();

    AzureDelegateTaskResponse artifactTaskResponse = AzureRegistriesResponse.builder()
                                                         .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                         .errorSummary("Test failed")
                                                         .build();

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenThrow(new DelegateServiceDriverException("DelegateServiceDriverException"));

    when(exceptionManager.processException(any(), any(), any()))
        .thenThrow(new WingsException("wings exception message"));

    assertThatThrownBy(()
                           -> azureHelperService.executeSyncTask(null, azureTaskParamsTaskParams, baseNGAccess,
                               "Azure list registries task failure due to error"))
        .isInstanceOf(WingsException.class)
        .hasMessage("wings exception message");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetAcrResponseDTO() {
    ArtifactDelegateResponse artifactDelegateResponse =
        new AcrArtifactDelegateResponse(ArtifactBuildDetailsNG.builder().number("tag").build(), ArtifactSourceType.ACR,
            "subscription", "registry", "repository", "tag", null);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder()
            .artifactDelegateResponses(Collections.singletonList(artifactDelegateResponse))
            .build();

    AcrResponseDTO acrResponseDTO = azureHelperService.getAcrResponseDTO(artifactTaskExecutionResponse);

    assertThat(acrResponseDTO.getBuildDetailsList().get(0).getTag()).isEqualTo("tag");

    assertThat(acrResponseDTO.getBuildDetailsList().get(0).getRepository()).isEqualTo("repository");
  }

  private ParameterField<List<String>> getFiles() {
    return ParameterField.createValueField(Collections.singletonList(getHarnessFile()));
  }

  private String getHarnessFile() {
    return format("%s:%s", Scope.ACCOUNT.getYamlRepresentation(), FILE_PATH);
  }

  private FileStoreNodeDTO getFileStoreNode() {
    return FileNodeDTO.builder()
        .name(CONFIG_FILE_NAME)
        .identifier(CONFIG_FILE_IDENTIFIER)
        .fileUsage(FileUsage.CONFIG)
        .parentIdentifier(CONFIG_FILE_PARENT_IDENTIFIER)
        .build();
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
        .build();
  }

  private StoreConfigWrapper getStoreConfigWrapperWithGitStore() {
    return StoreConfigWrapper.builder()
        .type(StoreConfigType.GIT)
        .spec(GitStore.builder()
                  .branch(ParameterField.createValueField(MASTER))
                  .commitId(ParameterField.createValueField(COMMIT_ID))
                  .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                  .repoName(ParameterField.createValueField(REPO_NAME))
                  .build())
        .build();
  }

  private StoreConfigWrapper getStoreConfigWrapperWithHarnessStore(List<String> files, List<String> secretFiles) {
    return StoreConfigWrapper.builder()
        .type(StoreConfigType.HARNESS)
        .spec(HarnessStore.builder()
                  .files(files == null ? null : ParameterField.createValueField(files))
                  .secretFiles(secretFiles == null ? null : ParameterField.createValueField(secretFiles))
                  .build())
        .build();
  }
}