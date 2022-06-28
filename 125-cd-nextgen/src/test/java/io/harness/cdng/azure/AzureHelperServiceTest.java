/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStoreFile;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.TMACARI;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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

    @InjectMocks private AzureHelperService azureHelperService;

    @Test
    @Owner(developers = {TMACARI, ABOSII})
    @Category(UnitTests.class)
    public void testValidateSettingsStoreReferences() {
        when(fileStoreService.getByPath(ACCOUNT_IDENTIFIER, null, null, FILE_PATH, false))
                .thenReturn(Optional.of(getFileStoreNode()));
        assertThatCode(() ->
        azureHelperService.validateSettingsStoreReferences(getStoreConfigWrapper(), getAmbiance(), "Test Entity"))
                .doesNotThrowAnyException();
    }

    @Test
    @Owner(developers = {TMACARI, ABOSII})
    @Category(UnitTests.class)
    public void testValidateSettingsStoreReferencesFileNotFound() {
        when(fileStoreService.get(ACCOUNT_IDENTIFIER, null, null, FILE_PATH, false))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                azureHelperService.validateSettingsStoreReferences(getStoreConfigWrapper(), getAmbiance(), "Test Entity"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("file not found in File Store with ref");
    }

    @Test
    @Owner(developers = {TMACARI, ABOSII})
    @Category(UnitTests.class)
    public void testValidateSettingsStoreReferencesMoreThanOneFileProvided() {
        StoreConfigWrapper storeConfigWrapper = StoreConfigWrapper.builder()
                .spec(HarnessStore.builder()
                        .files(ParameterField.createValueField(Arrays.asList(getHarnessFile(), getHarnessFile())))
                        .build())
                .build();

        assertThatThrownBy(() ->
                azureHelperService.validateSettingsStoreReferences(storeConfigWrapper, getAmbiance(), "Test Entity"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Only one file should be provided for Test Entity, store kind");
    }

    @Test
    @Owner(developers = {TMACARI, ABOSII})
    @Category(UnitTests.class)
    public void testValidateSettingsStoreReferencesNoFilePaths() {
        StoreConfigWrapper storeConfigWrapper = StoreConfigWrapper.builder()
                .spec(HarnessStore.builder()
                        .files(ParameterField.createValueField(Collections.emptyList()))
                        .build())
                .build();

        assertThatThrownBy(() ->
                azureHelperService.validateSettingsStoreReferences(storeConfigWrapper, getAmbiance(), "Test Entity"))
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
        assertThatCode(() -> azureHelperService.validateSettingsStoreReferences(storeConfigWrapper, ambiance, "Test Entity"))
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

    private ParameterField<List<HarnessStoreFile>> getFiles() {
        return ParameterField.createValueField(Collections.singletonList(getHarnessFile()));
    }

    private HarnessStoreFile getHarnessFile() {
        return HarnessStoreFile.builder()
                .path(ParameterField.createValueField(FILE_PATH))
                .scope(ParameterField.createValueField(Scope.ACCOUNT))
                .build();
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
}