/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.interrupts.InterruptType.ABORT_ALL;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.SOUMYAJIT;
import static io.harness.rule.OwnerRule.SRIDHAR;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.triggers.ManifestData;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.contracts.triggers.Type;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.YamlExpressionResolveHelper;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.merger.helpers.InputSetTemplateHelper;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.ExecutionDataResponseDTO;
import io.harness.pms.plan.execution.beans.dto.ExecutionMetaDataResponseDetailsDTO;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.UserPrincipal;

import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import com.google.common.io.Resources;
import com.mongodb.BasicDBList;
import com.mongodb.client.result.UpdateResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(PIPELINE)
public class PMSExecutionServiceImplTest extends CategoryTest {
  @Mock private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Mock private UpdateResult updateResult;
  @InjectMocks private PMSExecutionServiceImpl pmsExecutionService;
  @Mock private PmsGitSyncHelper pmsGitSyncHelper;
  @Mock private ValidateAndMergeHelper validateAndMergeHelper;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;
  @Mock private GitSyncSdkService gitSyncSdkService;
  @Mock OrchestrationService orchestrationService;
  @Mock YamlExpressionResolveHelper yamlExpressionResolveHelper;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "basichttpFail";
  private final String PLAN_EXECUTION_ID = "planId";
  private final List<String> PIPELINE_IDENTIFIER_LIST = Arrays.asList(PIPELINE_IDENTIFIER);
  private final String INVALID_PLAN_EXECUTION_ID = "InvalidPlanId";
  private final Boolean PIPELINE_DELETED = Boolean.FALSE;
  private String inputSetYaml;
  private String template;
  private String executionYaml;

  PipelineExecutionSummaryEntity executionSummaryEntity;
  PipelineEntity pipelineEntity;

  @Before
  public void setUp() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String inputSetFilename = "inputSet1.yml";
    inputSetYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(inputSetFilename)), StandardCharsets.UTF_8);

    String executionYamlFilename = "execution-yaml.yaml";
    executionYaml = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(executionYamlFilename)), StandardCharsets.UTF_8);

    String templateFilename = "pipeline-extensive-template.yml";
    template =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(templateFilename)), StandardCharsets.UTF_8);

    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(ACCOUNT_ID)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .projectIdentifier(PROJ_IDENTIFIER)
                                 .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                 .planExecutionId(PLAN_EXECUTION_ID)
                                 .name(PLAN_EXECUTION_ID)
                                 .runSequence(0)
                                 .inputSetYaml(inputSetYaml)
                                 .pipelineTemplate(template)
                                 .build();

    String pipelineYamlFileName = "failure-strategy.yaml";
    String pipelineYaml = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(pipelineYamlFileName)), StandardCharsets.UTF_8);

    pipelineEntity = PipelineEntity.builder()
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .identifier(PIPELINE_IDENTIFIER)
                         .name(PIPELINE_IDENTIFIER)
                         .yaml(pipelineYaml)
                         .build();
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testFormCriteria() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);
    Criteria form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        null, null, null, null, null, false, !PIPELINE_DELETED, true);

    assertThat(form.getCriteriaObject().get("accountId").toString().contentEquals(ACCOUNT_ID)).isEqualTo(true);
    assertThat(form.getCriteriaObject().get("orgIdentifier").toString().contentEquals(ORG_IDENTIFIER)).isEqualTo(true);
    assertThat(form.getCriteriaObject().get("projectIdentifier").toString().contentEquals(PROJ_IDENTIFIER))
        .isEqualTo(true);
    assertThat(form.getCriteriaObject().get("pipelineIdentifier").toString().contentEquals(PIPELINE_IDENTIFIER))
        .isEqualTo(true);
    assertThat(form.getCriteriaObject().containsKey("status")).isEqualTo(false);
    assertThat(form.getCriteriaObject().get("pipelineDeleted")).isNotEqualTo(true);
    assertThat(form.getCriteriaObject().containsKey("executionTriggerInfo")).isEqualTo(false);
    assertThat(form.getCriteriaObject().get("isLatestExecution")).isNotEqualTo(false);
    assertThat(form.getCriteriaObject().get("executionMode")).isNotEqualTo(false);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFormCriteriaForParentInfoCriteria() {
    Criteria form = pmsExecutionService.formCriteria(
        null, null, null, "", null, null, null, null, null, false, !PIPELINE_DELETED, true);
    Criteria childCriteria = new Criteria();
    childCriteria.orOperator(Criteria.where(PlanExecutionSummaryKeys.parentStageInfo).exists(false),
        Criteria.where(PlanExecutionSummaryKeys.isChildPipeline).is(false));

    List<Boolean> inChildList =
        (List<Boolean>) ((Document) form.getCriteriaObject().get(PlanExecutionSummaryKeys.isChildPipeline)).get("$in");
    assertThat(inChildList.size()).isEqualTo(2);
    assertThat(inChildList.get(0)).isNull();
    assertThat(inChildList.get(1)).isEqualTo(false);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFormCriteriaOROperatorOnModules() {
    Criteria form = pmsExecutionService.formCriteriaOROperatorOnModules(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER_LIST, null, null);
    BasicDBList orList = (BasicDBList) form.getCriteriaObject().get("$or");
    Document scopeCriteria = (Document) orList.get(0);
    Document pipelineIdentifierCriteria = (Document) scopeCriteria.get("pipelineIdentifier");
    List<String> pipelineList = (List<String>) pipelineIdentifierCriteria.get("$in");

    assertThat(form.getCriteriaObject().get("accountId").toString().contentEquals(ACCOUNT_ID)).isEqualTo(true);
    assertThat(form.getCriteriaObject().get("orgIdentifier").toString().contentEquals(ORG_IDENTIFIER)).isEqualTo(true);
    assertThat(form.getCriteriaObject().get("projectIdentifier").toString().contentEquals(PROJ_IDENTIFIER))
        .isEqualTo(true);
    assertThat(pipelineList.equals(PIPELINE_IDENTIFIER_LIST)).isEqualTo(true);
    assertThat(form.getCriteriaObject().containsKey("pipelineIdentifier")).isEqualTo(false);
    assertThat(form.getCriteriaObject().get("pipelineDeleted")).isNotEqualTo(true);
    assertThat(form.getCriteriaObject().containsKey("executionTriggerInfo")).isEqualTo(false);
    assertThat(form.getCriteriaObject().get("isLatestExecution")).isNotEqualTo(false);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFormCriteriaWithModuleName() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);
    Criteria form =
        pmsExecutionService.formCriteria(null, null, null, null, null, null, "cd", null, null, false, true, true);
    Criteria criteria = new Criteria();

    Criteria searchCriteria = new Criteria();
    searchCriteria.orOperator(Criteria.where(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.modules)
                                  .is(Collections.singletonList(ModuleType.PMS.name().toLowerCase())),
        Criteria.where(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.modules).in("cd"));

    criteria.andOperator(searchCriteria);

    assertThat(form.getCriteriaObject().get("$and")).isEqualTo(criteria.getCriteriaObject().get("$and"));
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetInputSetYaml() {
    doReturn(Optional.of(executionSummaryEntity))
        .when(pmsExecutionSummaryRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID, !PIPELINE_DELETED);
    doReturn(null).when(pmsGitSyncHelper).getEntityGitDetailsFromBytes(any());
    doReturn(template).when(validateAndMergeHelper).getPipelineTemplate(any(), any(), any(), any(), any());

    String inputSet = pmsExecutionService
                          .getInputSetYamlWithTemplate(
                              ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID, PIPELINE_DELETED, false)
                          .getInputSetYaml();

    assertThat(inputSet).isEqualTo(inputSetYaml);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetInputSetYamlWithInvalidExecutionId() {
    doReturn(Optional.empty())
        .when(pmsExecutionSummaryRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, INVALID_PLAN_EXECUTION_ID, !PIPELINE_DELETED);
    doReturn(null).when(pmsGitSyncHelper).getEntityGitDetailsFromBytes(any());
    doReturn(template).when(validateAndMergeHelper).getPipelineTemplate(any(), any(), any(), any(), any());

    assertThatThrownBy(()
                           -> pmsExecutionService.getInputSetYamlWithTemplate(ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, INVALID_PLAN_EXECUTION_ID, PIPELINE_DELETED, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid request : Input Set did not exist or pipeline execution has been deleted");
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionSummaryEntity() {
    doReturn(Optional.of(executionSummaryEntity))
        .when(pmsExecutionSummaryRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID, !PIPELINE_DELETED);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID, PIPELINE_DELETED);

    assertThat(pipelineExecutionSummaryEntity).isEqualTo(executionSummaryEntity);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionSummaryEntityWithInvalidExecutionId() {
    doReturn(Optional.empty())
        .when(pmsExecutionSummaryRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, INVALID_PLAN_EXECUTION_ID, !PIPELINE_DELETED);

    assertThatThrownBy(()
                           -> pmsExecutionService.getPipelineExecutionSummaryEntity(ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, INVALID_PLAN_EXECUTION_ID, PIPELINE_DELETED))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Plan Execution Summary does not exist or has been deleted for planExecutionId: "
            + INVALID_PLAN_EXECUTION_ID);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testDeleteExecutionsOnPipelineDeletion() {
    Criteria criteria = new Criteria();
    criteria.and("accountId")
        .is(ACCOUNT_ID)
        .and("orgIdentifier")
        .is(ORG_IDENTIFIER)
        .and("projectIdentifier")
        .is(PROJ_IDENTIFIER)
        .and("pipelineIdentifier")
        .is(PIPELINE_IDENTIFIER);
    Query query = new Query(criteria);

    Update update = new Update();
    update.set("pipelineDeleted", Boolean.TRUE);

    doReturn(true).when(updateResult).wasAcknowledged();
    doReturn(updateResult).when(pmsExecutionSummaryRepository).deleteAllExecutionsWhenPipelineDeleted(query, update);

    pmsExecutionService.deleteExecutionsOnPipelineDeletion(pipelineEntity);

    verify(pmsExecutionSummaryRepository, times(1)).deleteAllExecutionsWhenPipelineDeleted(query, update);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testDeleteExecutionsOnPipelineDeletionWhenDeleteFailed() {
    Criteria criteria = new Criteria();
    criteria.and("accountId")
        .is(ACCOUNT_ID)
        .and("orgIdentifier")
        .is(ORG_IDENTIFIER)
        .and("projectIdentifier")
        .is(PROJ_IDENTIFIER)
        .and("pipelineIdentifier")
        .is(PIPELINE_IDENTIFIER);
    Query query = new Query(criteria);

    Update update = new Update();
    update.set("pipelineDeleted", Boolean.TRUE);

    doReturn(false).when(updateResult).wasAcknowledged();
    doReturn(updateResult).when(pmsExecutionSummaryRepository).deleteAllExecutionsWhenPipelineDeleted(query, update);

    assertThatThrownBy(() -> pmsExecutionService.deleteExecutionsOnPipelineDeletion(pipelineEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            String.format("Executions for Pipeline [%s] under Project[%s], Organization [%s] couldn't be deleted.",
                PIPELINE_IDENTIFIER, PROJ_IDENTIFIER, ORG_IDENTIFIER));
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionSummary() {
    doReturn(Optional.of(executionSummaryEntity))
        .when(pmsExecutionSummaryRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionId(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID);

    assertThat(pipelineExecutionSummaryEntity).isEqualTo(executionSummaryEntity);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testGetExecutionMetadata() {
    String planExecutionID = "tempID";

    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().yaml(executionYaml).planExecutionId(planExecutionID).build();

    doReturn(Optional.of(planExecutionMetadata))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(planExecutionID);

    ExecutionDataResponseDTO executionData = pmsExecutionService.getExecutionData(planExecutionID);

    assertThat(executionData.getExecutionYaml()).isEqualTo(planExecutionMetadata.getYaml());
    verify(planExecutionMetadataService, times(1)).findByPlanExecutionId(planExecutionID);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testGetExecutionMetadataFailure() {
    String planExecutionID = "tempID";

    assertThatThrownBy(() -> pmsExecutionService.getExecutionData(planExecutionID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("Execution with id [%s] is not present or deleted", planExecutionID));
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetExecutionMetadataDetails() {
    String planExecutionID = "tempID";

    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder()
            .yaml(executionYaml)
            .planExecutionId(planExecutionID)
            .inputSetYaml("inputSetYaml")
            .triggerPayload(TriggerPayload.newBuilder()
                                .setType(Type.MANIFEST)
                                .setManifestData(ManifestData.newBuilder().setVersion("1.0").build())
                                .build())
            .build();

    doReturn(Optional.of(planExecutionMetadata))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(planExecutionID);
    doReturn("resolvedYaml")
        .when(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml("inputSetYaml", planExecutionID);
    ExecutionMetaDataResponseDetailsDTO executionData = pmsExecutionService.getExecutionDataDetails(planExecutionID);

    assertThat(executionData.getExecutionYaml()).isEqualTo(planExecutionMetadata.getYaml());
    assertThat(executionData.getTriggerPayload().getType()).isEqualTo(Type.MANIFEST);
    assertThat(executionData.getTriggerPayload().getManifestData().getVersion()).isEqualTo("1.0");
    assertThat(executionData.getResolvedYaml()).isEqualTo("resolvedYaml");
    verify(planExecutionMetadataService, times(1)).findByPlanExecutionId(planExecutionID);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetExecutionMetadataDetailsFailure() {
    String planExecutionID = "tempID";

    assertThatThrownBy(() -> pmsExecutionService.getExecutionDataDetails(planExecutionID))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining(String.format("Execution with id [%s] is not present or deleted", planExecutionID));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetInputSetYamlForRerun() {
    doReturn(Optional.of(PipelineExecutionSummaryEntity.builder().inputSetYaml("inputSetYaml").build()))
        .when(pmsExecutionSummaryRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID, true);
    assertEquals("inputSetYaml",
        pmsExecutionService.getInputSetYamlForRerun(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID, false));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testMergeInputSetIntoPipelineForRerun() {
    doReturn(PipelineEntity.builder().yaml("pipelineYaml").build())
        .when(validateAndMergeHelper)
        .getPipelineEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, false);
    doReturn(Optional.of(PipelineExecutionSummaryEntity.builder().inputSetYaml("inputSetYaml").build()))
        .when(pmsExecutionSummaryRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID, true);
    MockedStatic<InputSetMergeHelper> aStatic = Mockito.mockStatic(InputSetMergeHelper.class);
    aStatic.when(() -> InputSetMergeHelper.mergeInputSetIntoPipeline("pipelineTemplate", "inputSetYaml", false))
        .thenReturn("finalMergedYaml");
    MockedStatic<InputSetTemplateHelper> bStatic = Mockito.mockStatic(InputSetTemplateHelper.class);
    bStatic.when(() -> InputSetTemplateHelper.createTemplateFromPipeline("pipelineYaml"))
        .thenReturn("pipelineTemplate");
    Assertions.assertEquals("finalMergedYaml",
        pmsExecutionService.mergeRuntimeInputIntoPipelineForRerun(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, PLAN_EXECUTION_ID, null, null, Collections.emptyList()));
    bStatic.when(() -> InputSetTemplateHelper.createTemplateFromPipeline("pipelineYaml")).thenReturn("");
    assertThat(pmsExecutionService.mergeRuntimeInputIntoPipelineForRerun(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                   PIPELINE_IDENTIFIER, PLAN_EXECUTION_ID, null, null, Collections.emptyList()))
        .isEqualTo("");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testRegisterInterrupt() {
    MockedStatic<SecurityContextBuilder> mockedStatic = Mockito.mockStatic(SecurityContextBuilder.class);
    mockedStatic.when(() -> SecurityContextBuilder.getPrincipal())
        .thenReturn(new UserPrincipal("name1", "user1@harness.io", "user1", "accountId"));
    Interrupt interrupt = Interrupt.builder().uuid("uuid").type(ABORT_ALL).planExecutionId("planExecutionId").build();
    when(orchestrationService.registerInterrupt(any())).thenReturn(interrupt);
    InterruptDTO interruptDTO = pmsExecutionService.registerInterrupt(
        PlanExecutionInterruptType.ABORTALL, "planExecutionId", "nodeExecutionId");
    assertEquals(interruptDTO.getPlanExecutionId(), "planExecutionId");
    assertEquals(interruptDTO.getId(), "uuid");
    assertEquals(interruptDTO.getType(), PlanExecutionInterruptType.ABORTALL);
    ArgumentCaptor<InterruptPackage> interruptPackageArgumentCaptor = ArgumentCaptor.forClass(InterruptPackage.class);
    verify(orchestrationService, times(1)).registerInterrupt(interruptPackageArgumentCaptor.capture());
    assertEquals(
        interruptPackageArgumentCaptor.getValue().getInterruptConfig().getIssuedBy().getManualIssuer().getEmailId(),
        "user1@harness.io");
    assertEquals(
        interruptPackageArgumentCaptor.getValue().getInterruptConfig().getIssuedBy().getManualIssuer().getUserId(),
        "user1");
    assertEquals(
        interruptPackageArgumentCaptor.getValue().getInterruptConfig().getIssuedBy().getManualIssuer().getIdentifier(),
        "name1");
    assertEquals(
        interruptPackageArgumentCaptor.getValue().getInterruptConfig().getIssuedBy().getManualIssuer().getType(),
        "USER");
  }
}
