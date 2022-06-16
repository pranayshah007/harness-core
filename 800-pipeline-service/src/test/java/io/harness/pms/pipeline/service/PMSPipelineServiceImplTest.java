/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.impl.OutboxServiceImpl;
import io.harness.pms.PmsFeatureFlagService;
import io.harness.pms.contracts.governance.GovernanceMetadata;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.helpers.PipelineCloneHelper;
import io.harness.pms.pipeline.ClonePipelineDTO;
import io.harness.pms.pipeline.DestinationPipelineConfig;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.SourceIdentifierConfig;
import io.harness.pms.pipeline.StepCategory;
import io.harness.pms.pipeline.StepData;
import io.harness.pms.pipeline.StepPalleteInfo;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.rule.Owner;

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
import org.mockito.Mockito;

@OwnedBy(PIPELINE)
public class PMSPipelineServiceImplTest extends PipelineServiceTestBase {
  @Mock private PmsSdkInstanceService pmsSdkInstanceService;
  @Mock private PMSPipelineServiceStepHelper pmsPipelineServiceStepHelper;
  @Mock private PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Mock private OutboxServiceImpl outboxService;
  @Mock private GitSyncSdkService gitSyncSdkService;
  @Inject private PipelineMetadataService pipelineMetadataService;
  @InjectMocks private PMSPipelineServiceImpl pmsPipelineService;
  @Inject private PMSPipelineRepository pmsPipelineRepository;
  @Mock private PipelineCloneHelper pipelineCloneHelper;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;

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
        .validatePipelineYaml(any());

    String pipeline_yaml_filename = "clonePipelineInput.yaml";
    PIPELINE_YAML = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(pipeline_yaml_filename)), StandardCharsets.UTF_8);
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
    doReturn(updatedPipelineEntity).when(pmsPipelineServiceHelper).updatePipelineInfo(pipelineEntity);
    pmsPipelineService.create(pipelineEntity);
    pmsPipelineService.delete(accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, 1L);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdatePipelineYaml() throws IOException {
    doReturn(Optional.empty()).when(pipelineMetadataService).getMetadata(any(), any(), any(), any());
    on(pmsPipelineService).set("pmsPipelineRepository", pmsPipelineRepository);
    doReturn(updatedPipelineEntity).when(pmsPipelineServiceHelper).updatePipelineInfo(pipelineEntity);
    assertThatThrownBy(() -> pmsPipelineService.updatePipelineYaml(pipelineEntity, ChangeType.ADD))
        .isInstanceOf(InvalidRequestException.class);
    pmsPipelineService.create(pipelineEntity);
    doReturn(updatedPipelineEntity).when(pmsPipelineServiceHelper).updatePipelineInfo(any());
    pmsPipelineService.updatePipelineYaml(pipelineEntity, ChangeType.ADD);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetThrowException() {
    assertThatThrownBy(
        () -> pmsPipelineService.get(accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false))
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
    doReturn(updatedPipelineEntity).when(pmsPipelineServiceHelper).updatePipelineInfo(any());
    doNothing().when(pmsPipelineServiceHelper).validatePipelineFromRemote(pipelineEntity);
    doReturn(PIPELINE_YAML).when(pipelineCloneHelper).updatePipelineMetadataInSourceYaml(any(), any(), any());
    doReturn(true).when(pmsFeatureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pmsPipelineServiceHelper)
        .validatePipelineYaml(any());
    pmsPipelineRepository.save(pipelineEntity);

    PipelineSaveResponse pipelineSaveResponse = pmsPipelineService.clone(clonePipelineDTO, accountId);
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
    doReturn(updatedPipelineEntity).when(pmsPipelineServiceHelper).updatePipelineInfo(any());
    doNothing().when(pmsPipelineServiceHelper).validatePipelineFromRemote(pipelineEntity);
    doReturn(PIPELINE_YAML).when(pipelineCloneHelper).updatePipelineMetadataInSourceYaml(any(), any(), any());
    doReturn(true).when(pmsFeatureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(pmsPipelineServiceHelper)
        .validatePipelineYaml(any());
    pmsPipelineRepository.save(pipelineEntity);

    PipelineSaveResponse pipelineSaveResponse = pmsPipelineService.clone(clonePipelineDTO, accountId);
    assertThat(pipelineSaveResponse).isNotEqualTo(null);
    assertThat(pipelineSaveResponse.getGovernanceMetadata()).isNotEqualTo(null);
    assertThat(pipelineSaveResponse.getGovernanceMetadata().getDeny()).isTrue();
  }
}
