/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.FAILED_CHILDREN_OUTPUT;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.configfile.steps.ConfigFileStepParameters;
import io.harness.cdng.configfile.steps.IndividualConfigFileStep;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class IndividualConfigFileStepTest extends CDNGTestBase {
  private static final String IDENTIFIER = "identifier";
  private static final String FILE_PATH = "/file/path";
  private static final String FILE_PATH_OVERRIDE = "/file/path/override";
  private static final String MASTER = "master";
  private static final String COMMIT_ID = "commitId";
  private static final String CONNECTOR_REF = "connectorRef";
  private static final String REPO_NAME = "repoName";
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String CONNECTOR_NAME = "connectorName";
  private static final String CONFIG_FILE_NAME = "configFileName";
  private static final String CONFIG_FILE_IDENTIFIER = "configFileIdentifier";
  private static final String CONFIG_FILE_PARENT_IDENTIFIER = "configFileParentIdentifier";

  @Mock private ServiceStepsHelper serviceStepsHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private ConnectorService connectorService;
  @Mock private FileStoreService fileStoreService;
  @Mock private CDExpressionResolver cdExpressionResolver;

  @InjectMocks private IndividualConfigFileStep individualConfigFileStep;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(individualConfigFileStep.getStepParametersClass()).isEqualTo(ConfigFileStepParameters.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteSyncHarnessStore() {
    Ambiance ambiance = getAmbiance();
    Map<String, ResponseData> responseData = new HashMap<>();
    when(serviceStepsHelper.getChildrenOutcomes(responseData))
        .thenReturn(Collections.singletonList(ConfigFileOutcome.builder().identifier(IDENTIFIER).build()));
    when(executionSweepingOutputService.listOutputsWithGivenNameAndSetupIds(
             any(), eq(FAILED_CHILDREN_OUTPUT), anyList()))
        .thenReturn(Collections.emptyList());
    when(fileStoreService.getWithChildrenByPath(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FILE_PATH, false))
        .thenReturn(Optional.of(getFileStoreNode()));
    doNothing().when(cdExpressionResolver).updateStoreConfigExpressions(any(), any());

    ConfigFileStepParameters stepParameters =
        ConfigFileStepParameters.builder().identifier(IDENTIFIER).order(0).spec(getConfigFileAttributes()).build();
    StepResponse response =
        individualConfigFileStep.executeSync(ambiance, stepParameters, getStepInputPackage(), getPassThroughData());

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).hasSize(1);

    StepResponse.StepOutcome[] stepOutcomes = response.getStepOutcomes().toArray(new StepResponse.StepOutcome[1]);
    ConfigFileOutcome configFileOutcome = (ConfigFileOutcome) stepOutcomes[0].getOutcome();
    assertThat(configFileOutcome.getIdentifier()).isEqualTo(IDENTIFIER);

    assertThat(configFileOutcome.getStore().getKind()).isEqualTo(StoreConfigType.HARNESS.getDisplayName());
    HarnessStore store = (HarnessStore) configFileOutcome.getStore();
    String harnessStoreFile = store.getFiles().getValue().get(0);

    assertThat(harnessStoreFile).isEqualTo(FILE_PATH);
  }

  private FileStoreNodeDTO getFileStoreNode() {
    return FileNodeDTO.builder()
        .name(CONFIG_FILE_NAME)
        .identifier(CONFIG_FILE_IDENTIFIER)
        .fileUsage(FileUsage.CONFIG)
        .parentIdentifier(CONFIG_FILE_PARENT_IDENTIFIER)
        .build();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteSyncHarnessStoreWithStageOverride() {
    Ambiance ambiance = getAmbiance();
    Map<String, ResponseData> responseData = new HashMap<>();
    when(serviceStepsHelper.getChildrenOutcomes(responseData))
        .thenReturn(Collections.singletonList(ConfigFileOutcome.builder().identifier(IDENTIFIER).build()));
    when(executionSweepingOutputService.listOutputsWithGivenNameAndSetupIds(
             any(), eq(FAILED_CHILDREN_OUTPUT), anyList()))
        .thenReturn(Collections.emptyList());
    when(fileStoreService.getWithChildrenByPath(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, FILE_PATH_OVERRIDE, false))
        .thenReturn(Optional.of(getFileStoreNode()));
    doNothing().when(cdExpressionResolver).updateStoreConfigExpressions(any(), any());

    ConfigFileAttributes spec = getConfigFileAttributes();
    ConfigFileStepParameters stepParameters = ConfigFileStepParameters.builder()
                                                  .identifier(IDENTIFIER)
                                                  .order(0)
                                                  .spec(spec)
                                                  .stageOverride(getConfigFileAttributesOverride())
                                                  .build();
    StepInputPackage stepInputPackage = getStepInputPackage();
    StepExceptionPassThroughData passThroughData = getPassThroughData();

    StepResponse response =
        individualConfigFileStep.executeSync(ambiance, stepParameters, stepInputPackage, passThroughData);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).hasSize(1);

    StepResponse.StepOutcome[] stepOutcomes = response.getStepOutcomes().toArray(new StepResponse.StepOutcome[1]);
    ConfigFileOutcome configFileOutcome = (ConfigFileOutcome) stepOutcomes[0].getOutcome();
    assertThat(configFileOutcome.getIdentifier()).isEqualTo(IDENTIFIER);

    assertThat(configFileOutcome.getStore().getKind()).isEqualTo(StoreConfigType.HARNESS.getDisplayName());
    HarnessStore store = (HarnessStore) configFileOutcome.getStore();
    String harnessStoreFile = store.getFiles().getValue().get(0);

    assertThat(harnessStoreFile).isEqualTo(FILE_PATH_OVERRIDE);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteSyncGitStore() {
    Ambiance ambiance = getAmbiance();
    Map<String, ResponseData> responseData = new HashMap<>();
    when(serviceStepsHelper.getChildrenOutcomes(responseData))
        .thenReturn(Collections.singletonList(ConfigFileOutcome.builder().identifier(IDENTIFIER).build()));
    when(executionSweepingOutputService.listOutputsWithGivenNameAndSetupIds(
             any(), eq(FAILED_CHILDREN_OUTPUT), anyList()))
        .thenReturn(Collections.emptyList());

    when(connectorService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_REF))
        .thenReturn(Optional.of(
            ConnectorResponseDTO.builder()
                .connector(ConnectorInfoDTO.builder().identifier(CONNECTOR_REF).name(CONNECTOR_NAME).build())
                .entityValidityDetails(EntityValidityDetails.builder().valid(true).build())
                .build()));
    doNothing().when(cdExpressionResolver).updateStoreConfigExpressions(any(), any());

    ConfigFileAttributes spec = getConfigFileAttributesWithGitStore();
    ConfigFileStepParameters stepParameters =
        ConfigFileStepParameters.builder().identifier(IDENTIFIER).order(0).spec(spec).build();

    StepResponse response =
        individualConfigFileStep.executeSync(ambiance, stepParameters, getStepInputPackage(), getPassThroughData());

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).hasSize(1);

    StepResponse.StepOutcome[] stepOutcomes = response.getStepOutcomes().toArray(new StepResponse.StepOutcome[1]);
    ConfigFileOutcome configFileOutcome = (ConfigFileOutcome) stepOutcomes[0].getOutcome();
    assertThat(configFileOutcome.getIdentifier()).isEqualTo(IDENTIFIER);

    assertThat(configFileOutcome.getStore().getKind()).isEqualTo(StoreConfigType.GIT.getDisplayName());
    GitStore store = (GitStore) configFileOutcome.getStore();
    assertThat(store.getBranch().getValue()).isEqualTo(MASTER);
    assertThat(store.getCommitId().getValue()).isEqualTo(COMMIT_ID);
    assertThat(store.getConnectorRef().getValue()).isEqualTo(CONNECTOR_REF);
    assertThat(store.getRepoName().getValue()).isEqualTo(REPO_NAME);
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
        .build();
  }

  private StepInputPackage getStepInputPackage() {
    return StepInputPackage.builder().build();
  }

  private StepExceptionPassThroughData getPassThroughData() {
    return StepExceptionPassThroughData.builder().build();
  }

  private ConfigFileAttributes getConfigFileAttributes() {
    return ConfigFileAttributes.builder()
        .store(ParameterField.createValueField(StoreConfigWrapper.builder()
                                                   .type(StoreConfigType.HARNESS)
                                                   .spec(HarnessStore.builder().files(getFiles()).build())
                                                   .build()))
        .build();
  }

  private ParameterField<List<String>> getFiles() {
    return ParameterField.createValueField(Collections.singletonList(FILE_PATH));
  }

  private ConfigFileAttributes getConfigFileAttributesOverride() {
    return ConfigFileAttributes.builder()
        .store(ParameterField.createValueField(StoreConfigWrapper.builder()
                                                   .type(StoreConfigType.HARNESS)
                                                   .spec(HarnessStore.builder().files(getFilesOverride()).build())
                                                   .build()))
        .build();
  }

  private ParameterField<List<String>> getFilesOverride() {
    return ParameterField.createValueField(Collections.singletonList(FILE_PATH_OVERRIDE));
  }

  private ConfigFileAttributes getConfigFileAttributesWithGitStore() {
    return ConfigFileAttributes.builder()
        .store(
            ParameterField.createValueField(StoreConfigWrapper.builder()
                                                .type(StoreConfigType.GIT)
                                                .spec(GitStore.builder()
                                                          .branch(ParameterField.createValueField(MASTER))
                                                          .commitId(ParameterField.createValueField(COMMIT_ID))
                                                          .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                                                          .repoName(ParameterField.createValueField(REPO_NAME))
                                                          .build())
                                                .build()))
        .build();
  }
}
