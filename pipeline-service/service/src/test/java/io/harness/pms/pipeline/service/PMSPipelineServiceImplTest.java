/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.joor.Reflect.on;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.PipelineSettingsService;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.impl.OutboxServiceImpl;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.helpers.PipelineCloneHelper;
import io.harness.pms.pipeline.ClonePipelineDTO;
import io.harness.pms.pipeline.DestinationPipelineConfig;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.MoveConfigOperationDTO;
import io.harness.pms.pipeline.MoveConfigOperationType;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.SourceIdentifierConfig;
import io.harness.pms.pipeline.StepCategory;
import io.harness.pms.pipeline.StepData;
import io.harness.pms.pipeline.StepPalleteInfo;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
public class PMSPipelineServiceImplTest extends PipelineServiceTestBase {
  @Mock private PmsSdkInstanceService pmsSdkInstanceService;
  @Mock private PMSPipelineServiceStepHelper pmsPipelineServiceStepHelper;
  @Mock private PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Mock private OutboxServiceImpl outboxService;
  @Mock private GitSyncSdkService gitSyncSdkService;
  @Mock private EntitySetupUsageClient entitySetupUsageClient;
  @Inject private PipelineMetadataService pipelineMetadataService;
  @Mock private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;

  @Mock private PipelineSettingsService pipelineSettingsService;

  @InjectMocks private PMSPipelineServiceImpl pmsPipelineService;
  @Inject private PMSPipelineRepository pmsPipelineRepository;
  @Mock private PMSPipelineRepository pmsPipelineRepositoryMock;
  @Mock private PipelineCloneHelper pipelineCloneHelper;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private PipelineAsyncValidationService pipelineAsyncValidationService;
  @Mock private ProjectClient projectClient;
  @Mock private AccountClient accountClient;
  @Mock GitXSettingsHelper gitXSettingsHelper;

  StepCategory library;
  StepCategory cv;

  private final String accountId = RandomStringUtils.randomAlphanumeric(6);
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "myPipeline";
  private final String DEST_ORG_IDENTIFIER = "orgId_d";
  private final String DEST_PROJ_IDENTIFIER = "projId_d";
  private final String DEST_PIPELINE_IDENTIFIER = "myPipeline_d";
  private final String DEST_PIPELINE_DESCRIPTION = "test description_d";

  PipelineEntity pipelineEntity;
  PipelineEntity updatedPipelineEntity;
  OutboxEvent outboxEvent = OutboxEvent.builder().build();
  String PIPELINE_YAML;
  String PIPELINE_YAML_V1;

  @Before
  public void setUp() throws IOException {
    StepCategory testStepCD =
        StepCategory.builder()
            .name("Single")
            .stepsData(Collections.singletonList(StepData.builder().name("testStepCD").type("testStepCD").build()))
            .stepCategories(Collections.emptyList())
            .build();
    StepCategory libraryDouble = StepCategory.builder()
                                     .name("Double")
                                     .stepsData(Collections.emptyList())
                                     .stepCategories(Collections.singletonList(testStepCD))
                                     .build();
    List<StepCategory> list = new ArrayList<>();
    list.add(libraryDouble);
    library = StepCategory.builder().name("Library").stepsData(new ArrayList<>()).stepCategories(list).build();

    StepCategory testStepCV =
        StepCategory.builder()
            .name("Single")
            .stepsData(Collections.singletonList(StepData.builder().name("testStepCV").type("testStepCV").build()))
            .stepCategories(Collections.emptyList())
            .build();
    StepCategory libraryDoubleCV = StepCategory.builder()
                                       .name("Double")
                                       .stepsData(Collections.emptyList())
                                       .stepCategories(Collections.singletonList(testStepCV))
                                       .build();
    List<StepCategory> listCV = new ArrayList<>();
    listCV.add(libraryDoubleCV);
    cv = StepCategory.builder().name("cv").stepsData(new ArrayList<>()).stepCategories(listCV).build();

    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "failure-strategy.yaml";
    String yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);

    pipelineEntity = PipelineEntity.builder()
                         .accountId(accountId)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .identifier(PIPELINE_IDENTIFIER)
                         .name(PIPELINE_IDENTIFIER)
                         .yaml(yaml)
                         .harnessVersion(PipelineVersion.V0)
                         .stageCount(1)
                         .stageName("qaStage")
                         .version(null)
                         .deleted(false)
                         .createdAt(System.currentTimeMillis())
                         .lastUpdatedAt(System.currentTimeMillis())
                         .build();

    updatedPipelineEntity = pipelineEntity.withStageCount(1).withStageNames(Collections.singletonList("qaStage"));

    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pmsPipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean());
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pmsPipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean());
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pmsPipelineServiceHelper)
        .validatePipeline(any(), any(), anyBoolean());
    doReturn(TemplateMergeResponseDTO.builder().build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(any(), anyBoolean(), anyBoolean());

    String pipeline_yaml_filename = "clonePipelineInput.yaml";
    PIPELINE_YAML = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(pipeline_yaml_filename)), StandardCharsets.UTF_8);
    PIPELINE_YAML_V1 =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("pipeline-v1.yaml")), StandardCharsets.UTF_8);
  }

  private ClonePipelineDTO buildCloneDTO() {
    SourceIdentifierConfig sourceIdentifierConfig = SourceIdentifierConfig.builder()
                                                        .orgIdentifier(ORG_IDENTIFIER)
                                                        .projectIdentifier(PROJ_IDENTIFIER)
                                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                        .build();
    DestinationPipelineConfig destinationPipelineConfig = DestinationPipelineConfig.builder()
                                                              .pipelineIdentifier(DEST_PIPELINE_IDENTIFIER)
                                                              .orgIdentifier(DEST_ORG_IDENTIFIER)
                                                              .pipelineName(DEST_PIPELINE_IDENTIFIER)
                                                              .projectIdentifier(DEST_PROJ_IDENTIFIER)
                                                              .description(DEST_PIPELINE_DESCRIPTION)
                                                              .build();
    ClonePipelineDTO clonePipelineDTO = ClonePipelineDTO.builder()
                                            .sourceConfig(sourceIdentifierConfig)
                                            .destinationConfig(destinationPipelineConfig)
                                            .build();
    return clonePipelineDTO;
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetSteps() {
    Map<String, StepPalleteInfo> serviceInstanceNameToSupportedSteps = new HashMap<>();
    serviceInstanceNameToSupportedSteps.put("cd",
        StepPalleteInfo.builder()
            .moduleName("cd")
            .stepTypes(Collections.singletonList(
                StepInfo.newBuilder()
                    .setName("testStepCD")
                    .setType("testStepCD")
                    .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Double/Single").build())
                    .build()))
            .build());
    serviceInstanceNameToSupportedSteps.put("cv",
        StepPalleteInfo.builder()
            .moduleName("cv")
            .stepTypes(Collections.singletonList(
                StepInfo.newBuilder()
                    .setName("testStepCV")
                    .setType("testStepCV")
                    .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Double/Single").build())
                    .build()))
            .build());

    Mockito.when(pmsSdkInstanceService.getModuleNameToStepPalleteInfo())
        .thenReturn(serviceInstanceNameToSupportedSteps);
    Mockito
        .when(pmsPipelineServiceStepHelper.calculateStepsForModuleBasedOnCategory(
            null, serviceInstanceNameToSupportedSteps.get("cd").getStepTypes(), accountId))
        .thenReturn(library);
    Mockito
        .when(pmsPipelineServiceStepHelper.calculateStepsForCategory(
            "cv", serviceInstanceNameToSupportedSteps.get("cv").getStepTypes(), accountId))
        .thenReturn(cv);
    StepCategory stepCategory = pmsPipelineService.getSteps("cd", null, accountId);
    String expected =
        "StepCategory(name=Library, stepsData=[], stepCategories=[StepCategory(name=Double, stepsData=[], stepCategories=[StepCategory(name=Single, stepsData=[StepData(name=testStepCD, type=testStepCD, disabled=false, featureRestrictionName=null)], stepCategories=[])]), StepCategory(name=cv, stepsData=[], stepCategories=[StepCategory(name=Double, stepsData=[], stepCategories=[StepCategory(name=Single, stepsData=[StepData(name=testStepCV, type=testStepCV, disabled=false, featureRestrictionName=null)], stepCategories=[])])])])";
    assertThat(stepCategory.toString()).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetStepsWithCategory() {
    Map<String, StepPalleteInfo> serviceInstanceNameToSupportedSteps = new HashMap<>();
    serviceInstanceNameToSupportedSteps.put("cd",
        StepPalleteInfo.builder()
            .moduleName("cd")
            .stepTypes(Collections.singletonList(
                StepInfo.newBuilder()
                    .setName("testStepCD")
                    .setType("testStepCD")
                    .setStepMetaData(
                        StepMetaData.newBuilder().addCategory("K8S").addFolderPaths("Double/Single").build())
                    .build()))
            .build());
    serviceInstanceNameToSupportedSteps.put("cv",
        StepPalleteInfo.builder()
            .moduleName("cv")
            .stepTypes(Collections.singletonList(
                StepInfo.newBuilder()
                    .setName("testStepCV")
                    .setType("testStepCV")
                    .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Double/Single").build())
                    .build()))
            .build());

    Mockito.when(pmsSdkInstanceService.getModuleNameToStepPalleteInfo())
        .thenReturn(serviceInstanceNameToSupportedSteps);
    Mockito
        .when(pmsPipelineServiceStepHelper.calculateStepsForModuleBasedOnCategory(
            "Terraform", serviceInstanceNameToSupportedSteps.get("cd").getStepTypes(), accountId))
        .thenReturn(StepCategory.builder()
                        .name("Library")
                        .stepsData(new ArrayList<>())
                        .stepCategories(new ArrayList<>())
                        .build());
    Mockito
        .when(pmsPipelineServiceStepHelper.calculateStepsForCategory(
            "cv", serviceInstanceNameToSupportedSteps.get("cv").getStepTypes(), accountId))
        .thenReturn(cv);

    StepCategory stepCategory = pmsPipelineService.getSteps("cd", "Terraform", accountId);
    String expected =
        "StepCategory(name=Library, stepsData=[], stepCategories=[StepCategory(name=cv, stepsData=[], stepCategories=[StepCategory(name=Double, stepsData=[], stepCategories=[StepCategory(name=Single, stepsData=[StepData(name=testStepCV, type=testStepCV, disabled=false, featureRestrictionName=null)], stepCategories=[])])])])";
    assertThat(stepCategory.toString()).isEqualTo(expected);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testDelete() throws IOException {
    doReturn(Optional.empty()).when(pipelineMetadataService).getMetadata(any(), any(), any(), any());
    on(pmsPipelineService).set("pmsPipelineRepository", pmsPipelineRepository);
    doReturn(outboxEvent).when(outboxService).save(any());
    doReturn(updatedPipelineEntity)
        .when(pmsPipelineServiceHelper)
        .updatePipelineInfo(pipelineEntity, PipelineVersion.V0);
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    MockedStatic<CGRestUtils> cgStatic = Mockito.mockStatic(CGRestUtils.class);
    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenReturn(projDTOCall);
    pmsPipelineService.validateAndCreatePipeline(pipelineEntity, true);
    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(false);
    cgStatic.when(() -> CGRestUtils.getResponse(any())).thenReturn(false);
    pmsPipelineService.delete(accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, 1L);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdatePipelineYaml() throws IOException {
    doReturn(Optional.empty()).when(pipelineMetadataService).getMetadata(any(), any(), any(), any());
    on(pmsPipelineService).set("pmsPipelineRepository", pmsPipelineRepository);
    doReturn(updatedPipelineEntity)
        .when(pmsPipelineServiceHelper)
        .updatePipelineInfo(pipelineEntity, PipelineVersion.V0);
    assertThatThrownBy(() -> pmsPipelineService.validateAndUpdatePipeline(pipelineEntity, ChangeType.ADD, true))
        .isInstanceOf(InvalidRequestException.class);
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenReturn(projDTOCall);
    pmsPipelineService.validateAndCreatePipeline(pipelineEntity, true);
    doReturn(updatedPipelineEntity).when(pmsPipelineServiceHelper).updatePipelineInfo(any(), eq(PipelineVersion.V0));
    pmsPipelineService.validateAndUpdatePipeline(pipelineEntity, ChangeType.ADD, true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidateStoreYaml() {
    String invalidYaml = "---\n"
        + "name: \"parent pipeline\"\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n"
        + "timeout: \"1w\"\n"
        + "type: \"Pipeline\"\n"
        + "type: \"Pipeline\"\n"
        + "spec:\n"
        + "  pipeline: \"childPipeline\"\n"
        + "  org: \"org\"\n"
        + "  project: \"project\"\n";
    PipelineEntity entity = PipelineEntity.builder().yaml(invalidYaml).build();
    assertThatThrownBy(() -> pmsPipelineService.validateStoredYaml(entity)).isInstanceOf(InvalidYamlException.class);

    String validYaml = "---\n"
        + "name: \"parent pipeline\"\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n"
        + "timeout: \"1w\"\n"
        + "type: \"Pipeline\"\n"
        + "spec:\n"
        + "  pipeline: \"childPipeline\"\n"
        + "  org: \"org\"\n"
        + "  project: \"project\"\n";

    entity.setYaml(validYaml);
    assertThatCode(() -> pmsPipelineService.validateStoredYaml(entity)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetThrowException() {
    doThrow(new InvalidRequestException("Invalid request"))
        .when(pmsPipelineRepositoryMock)
        .find(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    assertThatThrownBy(()
                           -> pmsPipelineService.getAndValidatePipeline(
                               accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSaveExecutionInfo() {
    ExecutionSummaryInfo executionSummaryInfo = ExecutionSummaryInfo.builder().build();
    on(pmsPipelineService).set("pmsPipelineRepository", pmsPipelineRepository);
    assertThatCode(()
                       -> pmsPipelineService.saveExecutionInfo(
                           accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, executionSummaryInfo))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testClonePipeline() throws IOException {
    ClonePipelineDTO clonePipelineDTO = buildCloneDTO();

    doReturn(Optional.empty()).when(pipelineMetadataService).getMetadata(any(), any(), any(), any());
    on(pmsPipelineService).set("pmsPipelineRepository", pmsPipelineRepository);
    doReturn(outboxEvent).when(outboxService).save(any());
    doReturn(updatedPipelineEntity).when(pmsPipelineServiceHelper).updatePipelineInfo(any(), any());
    doNothing().when(pmsPipelineServiceHelper).resolveTemplatesAndValidatePipelineEntity(pipelineEntity, false);
    doReturn(PIPELINE_YAML).when(pipelineCloneHelper).updatePipelineMetadataInSourceYaml(any(), any(), any());
    doReturn(true).when(pmsFeatureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pmsPipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean());
    pmsPipelineRepository.save(pipelineEntity);
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenReturn(projDTOCall);

    PipelineSaveResponse pipelineSaveResponse =
        pmsPipelineService.validateAndClonePipeline(clonePipelineDTO, accountId);
    assertThat(pipelineSaveResponse).isNotEqualTo(null);
    assertThat(pipelineSaveResponse.getGovernanceMetadata()).isNotEqualTo(null);
    assertThat(pipelineSaveResponse.getGovernanceMetadata().getDeny()).isFalse();
    assertThat(pipelineSaveResponse.getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testClonePipelineWithoutGovernance() throws IOException {
    ClonePipelineDTO clonePipelineDTO = buildCloneDTO();

    doReturn(Optional.empty()).when(pipelineMetadataService).getMetadata(any(), any(), any(), any());
    on(pmsPipelineService).set("pmsPipelineRepository", pmsPipelineRepository);
    doReturn(outboxEvent).when(outboxService).save(any());
    doReturn(updatedPipelineEntity).when(pmsPipelineServiceHelper).updatePipelineInfo(any(), eq(PipelineVersion.V0));
    doNothing().when(pmsPipelineServiceHelper).resolveTemplatesAndValidatePipelineEntity(pipelineEntity, false);
    doReturn(PIPELINE_YAML).when(pipelineCloneHelper).updatePipelineMetadataInSourceYaml(any(), any(), any());
    doReturn(true).when(pmsFeatureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(pmsPipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean());
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(pmsPipelineServiceHelper)
        .validatePipeline(any(), any(), anyBoolean());

    pmsPipelineRepository.save(pipelineEntity);
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenReturn(projDTOCall);

    PipelineSaveResponse pipelineSaveResponse =
        pmsPipelineService.validateAndClonePipeline(clonePipelineDTO, accountId);
    assertThat(pipelineSaveResponse).isNotEqualTo(null);
    assertThat(pipelineSaveResponse.getGovernanceMetadata()).isNotEqualTo(null);
    assertThat(pipelineSaveResponse.getGovernanceMetadata().getDeny()).isTrue();
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testUpdatePipelineYamlDraftException() {
    on(pmsPipelineService).set("pmsPipelineRepository", pmsPipelineRepository);
    pipelineEntity.setIsDraft(true);
    assertThatThrownBy(() -> pmsPipelineService.validateAndUpdatePipeline(pipelineEntity, ChangeType.ADD, true))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testUpdateDraft() throws IOException {
    on(pmsPipelineService).set("pmsPipelineRepository", pmsPipelineRepository);
    pipelineEntity.setIsDraft(true);
    doReturn(pipelineEntity).when(pmsPipelineServiceHelper).updatePipelineInfo(any(), eq(PipelineVersion.V0));
    pmsPipelineRepository.save(pipelineEntity);
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenReturn(projDTOCall);
    PipelineCRUDResult pipelineCRUDResult =
        pmsPipelineService.validateAndUpdatePipeline(pipelineEntity, ChangeType.ADD, true);
    assertThat(pipelineCRUDResult.getPipelineEntity().getIdentifier()).isEqualTo(pipelineEntity.getIdentifier());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testUpdatePipelineYamlWithoutHarnessVersion() throws IOException {
    pipelineEntity.setHarnessVersion(null);
    doReturn(Optional.empty()).when(pipelineMetadataService).getMetadata(any(), any(), any(), any());
    on(pmsPipelineService).set("pmsPipelineRepository", pmsPipelineRepository);
    doReturn(updatedPipelineEntity)
        .when(pmsPipelineServiceHelper)
        .updatePipelineInfo(pipelineEntity, PipelineVersion.V0);
    assertThatThrownBy(() -> pmsPipelineService.validateAndUpdatePipeline(pipelineEntity, ChangeType.ADD, true))
        .isInstanceOf(InvalidRequestException.class);
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenReturn(projDTOCall);
    pmsPipelineService.validateAndCreatePipeline(pipelineEntity, true);
    doReturn(updatedPipelineEntity).when(pmsPipelineServiceHelper).updatePipelineInfo(any(), eq(PipelineVersion.V0));
    pmsPipelineService.validateAndUpdatePipeline(pipelineEntity, ChangeType.ADD, true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidateSetupUsage() {
    Call<ResponseDTO<Boolean>> request = getResponseDTOCall(true);
    doReturn(request).when(entitySetupUsageClient).isEntityReferenced(any(), any(), any());
    assertThatThrownBy(
        () -> pmsPipelineService.validateSetupUsage(accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER))
        .isInstanceOf(ReferencedEntityException.class);

    request = getResponseDTOCall(false);
    doReturn(request).when(entitySetupUsageClient).isEntityReferenced(any(), any(), any());
    assertThatCode(
        () -> pmsPipelineService.validateSetupUsage(accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER))
        .doesNotThrowAnyException();
  }

  private Call<ResponseDTO<Boolean>> getResponseDTOCall(boolean setValue) {
    Call<ResponseDTO<Boolean>> request = mock(Call.class);
    try {
      when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(setValue)));
    } catch (IOException ex) {
    }
    return request;
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMovePipelineEntityInlineToRemote() {
    MoveConfigOperationDTO moveConfigOperation =
        MoveConfigOperationDTO.builder().moveConfigOperationType(MoveConfigOperationType.INLINE_TO_REMOTE).build();

    doReturn(pipelineEntity)
        .when(pmsPipelineRepositoryMock)
        .updatePipelineEntity(any(), any(), any(), any(), any(), any());

    PipelineEntity movePipelineEntity = pmsPipelineService.movePipelineEntity(
        accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, moveConfigOperation, pipelineEntity);

    assertEquals(movePipelineEntity.getIdentifier(), pipelineEntity.getIdentifier());

    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    assertEquals(StoreType.REMOTE, gitEntityInfo.getStoreType());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMovePipelineEntityRemoteToInline() {
    MoveConfigOperationDTO moveConfigOperation =
        MoveConfigOperationDTO.builder().moveConfigOperationType(MoveConfigOperationType.REMOTE_TO_INLINE).build();

    doReturn(pipelineEntity)
        .when(pmsPipelineRepositoryMock)
        .updatePipelineEntity(any(), any(), any(), any(), any(), any());

    PipelineEntity movePipelineEntity = pmsPipelineService.movePipelineEntity(
        accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, moveConfigOperation, pipelineEntity);

    assertEquals(movePipelineEntity.getIdentifier(), pipelineEntity.getIdentifier());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testClonePipelineV1() throws IOException {
    ClonePipelineDTO clonePipelineDTO = buildCloneDTO();
    pipelineEntity.setHarnessVersion(PipelineVersion.V1);

    doReturn(Optional.empty()).when(pipelineMetadataService).getMetadata(any(), any(), any(), any());
    on(pmsPipelineService).set("pmsPipelineRepository", pmsPipelineRepository);
    doReturn(outboxEvent).when(outboxService).save(any());
    doReturn(updatedPipelineEntity).when(pmsPipelineServiceHelper).updatePipelineInfo(any(), any());
    doNothing().when(pmsPipelineServiceHelper).resolveTemplatesAndValidatePipelineEntity(pipelineEntity, false);
    doReturn(PIPELINE_YAML_V1).when(pipelineCloneHelper).updatePipelineMetadataInSourceYamlV1(any(), any());
    doReturn(true).when(pmsFeatureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pmsPipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean());
    doNothing().when(gitXSettingsHelper).enforceGitExperienceIfApplicable(any(), any(), any());
    pmsPipelineRepository.save(pipelineEntity);
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenReturn(projDTOCall);
    PipelineSaveResponse pipelineSaveResponse =
        pmsPipelineService.validateAndClonePipeline(clonePipelineDTO, accountId);
    assertThat(pipelineSaveResponse).isNotNull();
    assertThat(pipelineSaveResponse.getGovernanceMetadata()).isNotNull();
    assertThat(pipelineSaveResponse.getGovernanceMetadata().getDeny()).isFalse();
    assertThat(pipelineSaveResponse.getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetInvalidProjectIdentifier() throws IOException {
    doReturn(getResponseDTOCall(false)).when(entitySetupUsageClient).isEntityReferenced(any(), any(), any());
    doReturn(Optional.empty()).when(pipelineMetadataService).getMetadata(any(), any(), any(), any());
    on(pmsPipelineService).set("pmsPipelineRepository", pmsPipelineRepository);
    doReturn(outboxEvent).when(outboxService).save(any());
    doReturn(updatedPipelineEntity)
        .when(pmsPipelineServiceHelper)
        .updatePipelineInfo(pipelineEntity, PipelineVersion.V0);
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenThrow(InvalidRequestException.class);
    final Throwable ex = catchThrowable(() -> pmsPipelineService.validateAndCreatePipeline(pipelineEntity, true));
    assertThat(ex).isInstanceOf(InvalidRequestException.class);
  }
}
