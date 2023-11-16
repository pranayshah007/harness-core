/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.interrupts.InterruptType.ABORT_ALL;
import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.SOUMYAJIT;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.PipelineServiceTestHelper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.triggers.ManifestData;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.contracts.triggers.Type;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.YamlExpressionResolveHelper;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.merger.helpers.InputSetTemplateHelper;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.pipeline.PMSPipelineListBranchesResponse;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.ExecutionDataResponseDTO;
import io.harness.pms.plan.execution.beans.dto.ExecutionMetaDataResponseDetailsDTO;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionFilterPropertiesDTO;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.service.GraphGenerationService;

import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import com.google.common.io.Resources;
import com.mongodb.BasicDBList;
import com.mongodb.client.result.UpdateResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.data.util.CloseableIterator;

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
  @Mock FilterService filterService;
  @Mock YamlExpressionResolveHelper yamlExpressionResolveHelper;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock GraphGenerationService graphGenerationService;
  @Mock PMSPipelineServiceHelper pmsPipelineServiceHelper;

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
  PlanExecutionMetadata planExecutionMetadata;

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

    planExecutionMetadata = PlanExecutionMetadata.builder().inputSetYaml(inputSetYaml).build();

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

    doReturn(Arrays.asList(PIPELINE_IDENTIFIER))
        .when(pmsPipelineService)
        .getPermittedPipelineIdentifier(any(), any(), any(), any());
    Criteria form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        null, null, null, null, null, false, !PIPELINE_DELETED, true);

    assertThat(form.getCriteriaObject().get("accountId").toString().contentEquals(ACCOUNT_ID)).isEqualTo(true);
    assertThat(form.getCriteriaObject().get("orgIdentifier").toString().contentEquals(ORG_IDENTIFIER)).isEqualTo(true);
    assertThat(form.getCriteriaObject().get("projectIdentifier").toString().contentEquals(PROJ_IDENTIFIER))
        .isEqualTo(true);
    assertThat(form.getCriteriaObject()
                   .get("pipelineIdentifier")
                   .toString()
                   .contentEquals("Document{{$in=[" + PIPELINE_IDENTIFIER + "]}}"))
        .isEqualTo(true);
    assertThat(form.getCriteriaObject().containsKey("status")).isEqualTo(false);
    assertThat(form.getCriteriaObject().get("pipelineDeleted")).isNotEqualTo(true);
    assertThat(form.getCriteriaObject().containsKey("executionTriggerInfo")).isEqualTo(false);
    assertThat(form.getCriteriaObject().get("isLatestExecution")).isNotEqualTo(false);
    assertThat(form.getCriteriaObject().get("executionMode")).isNotEqualTo(false);

    PipelineExecutionFilterPropertiesDTO pipelineExecutionFilterPropertiesDTO =
        PipelineExecutionFilterPropertiesDTO.builder()
            .triggerIdentifiers(Collections.singletonList("triggerIdentifier"))
            .build();
    doNothing().when(pmsPipelineServiceHelper).setPermittedPipelines(any(), any(), any(), any(), any());
    Criteria form1 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        null, pipelineExecutionFilterPropertiesDTO, null, null, null, false, !PIPELINE_DELETED, false);
    assertThat(form1.getCriteriaObject().toString())
        .isEqualTo(
            "Document{{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, pipelineIdentifier=Document{{$in=[basichttpFail]}}, isLatestExecution=Document{{$ne=false}}, $and=[Document{{$and=[Document{{executionTriggerInfo.triggeredBy.triggerIdentifier=Document{{$in=[triggerIdentifier]}}}}]}}]}}");
    PipelineExecutionFilterPropertiesDTO pipelineExecutionFilterPropertiesDTO1 =
        PipelineExecutionFilterPropertiesDTO.builder()
            .triggerTypes(Collections.singletonList(TriggerType.WEBHOOK))
            .build();
    Criteria form2 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        null, pipelineExecutionFilterPropertiesDTO1, null, null, null, false, !PIPELINE_DELETED, false);
    assertThat(form2.getCriteriaObject().toString())
        .isEqualTo(
            "Document{{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, pipelineIdentifier=Document{{$in=[basichttpFail]}}, isLatestExecution=Document{{$ne=false}}, $and=[Document{{$and=[Document{{executionTriggerInfo.triggerType=Document{{$in=[WEBHOOK]}}}}]}}]}}");
    Criteria form3 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        null, pipelineExecutionFilterPropertiesDTO1, null, null, null, false, !PIPELINE_DELETED, true);
    assertThat(form3.getCriteriaObject().toString())
        .isEqualTo(
            "Document{{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, pipelineIdentifier=Document{{$in=[basichttpFail]}}, $and=[Document{{$and=[Document{{executionTriggerInfo.triggerType=Document{{$in=[WEBHOOK]}}}}]}}]}}");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testFormCriteria_1() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);

    doReturn(Arrays.asList(PIPELINE_IDENTIFIER))
        .when(pmsPipelineService)
        .getPermittedPipelineIdentifier(any(), any(), any(), any());

    PipelineExecutionFilterPropertiesDTO pipelineExecutionFilterPropertiesDTO =
        PipelineExecutionFilterPropertiesDTO.builder()
            .triggerTypes(Collections.singletonList(TriggerType.WEBHOOK))
            .build();
    doNothing().when(pmsPipelineServiceHelper).setPermittedPipelines(any(), any(), any(), any(), any());
    doReturn(FilterDTO.builder().filterProperties(pipelineExecutionFilterPropertiesDTO).build())
        .when(filterService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "FILTER_IDENTIFIER", FilterType.PIPELINEEXECUTION);
    Criteria form3 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        "FILTER_IDENTIFIER", null, null, null, null, false, !PIPELINE_DELETED, true);
    assertThat(form3.getCriteriaObject().toString())
        .isEqualTo(
            "Document{{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, pipelineIdentifier=Document{{$in=[basichttpFail]}}, $and=[Document{{$and=[Document{{executionTriggerInfo.triggerType=Document{{$in=[WEBHOOK]}}}}]}}]}}");
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testFormCriteriaForParentInfoCriteria() {
    doNothing().when(pmsPipelineServiceHelper).setPermittedPipelines(any(), any(), any(), any(), any());
    Criteria form = pmsExecutionService.formCriteria(
        null, null, null, "", null, null, null, null, null, false, !PIPELINE_DELETED, true);
    Criteria childCriteria = new Criteria();
    childCriteria.orOperator(Criteria.where(PlanExecutionSummaryKeys.parentStageInfo).exists(false),
        Criteria.where(PlanExecutionSummaryKeys.isChildPipeline).is(false));

    List<Boolean> inChildList =
        (List<Boolean>) ((Document) ((Document) ((BasicDBList) form.getCriteriaObject().get("$and")).get(0))
                             .get(PlanExecutionSummaryKeys.isChildPipeline))
            .get("$in");
    assertThat(inChildList.size()).isEqualTo(2);
    assertThat(inChildList.get(0)).isNull();
    assertThat(inChildList.get(1)).isEqualTo(false);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFormCriteriaOROperatorOnModules() {
    when(pmsPipelineService.getPermittedPipelineIdentifier(any(), any(), any(), any()))
        .thenReturn(PIPELINE_IDENTIFIER_LIST);
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

    assertThat(((Document) ((BasicDBList) form.getCriteriaObject().get("$and")).get(0)).size()).isEqualTo(3);
    assertThat(searchCriteria.getCriteriaObject().toString())
        .isEqualTo("Document{{$or=[Document{{modules=[pms]}}, Document{{modules=Document{{$in=[cd]}}}}]}}");
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetInputSetYaml() {
    doReturn(planExecutionMetadata)
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(PLAN_EXECUTION_ID, Set.of(PlanExecutionMetadataKeys.inputSetYaml));
    doReturn(Optional.of(executionSummaryEntity))
        .when(pmsExecutionSummaryRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID, !PIPELINE_DELETED);
    doReturn(null).when(pmsGitSyncHelper).getEntityGitDetailsFromBytes(any());
    doReturn(template).when(validateAndMergeHelper).getPipelineTemplate(any(), any(), any(), any(), any());

    String inputSet = pmsExecutionService
                          .getInputSetYamlWithTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID,
                              PIPELINE_DELETED, false, ResolveInputYamlType.UNKNOWN)
                          .getInputSetYaml();

    assertThat(inputSet).isEqualTo(inputSetYaml);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetInputSetYamlWithResolvedTriggerExpressions() throws IOException {
    ClassLoader classLoaderWithTriggerExpression = this.getClass().getClassLoader();
    String inputSetWithTriggerExpressionFilename = "inputsetWithTriggerExpression.yaml";
    String inputSetYamlWithTriggerExpression = Resources.toString(
        Objects.requireNonNull(classLoaderWithTriggerExpression.getResource(inputSetWithTriggerExpressionFilename)),
        StandardCharsets.UTF_8);

    String inputSetWithResolvedTriggerExpressionFilename = "inputsetWithResolvedTriggerExpressions.yaml";

    PipelineExecutionSummaryEntity executionSummaryEntity1 = PipelineExecutionSummaryEntity.builder()
                                                                 .accountId(ACCOUNT_ID)
                                                                 .orgIdentifier(ORG_IDENTIFIER)
                                                                 .projectIdentifier(PROJ_IDENTIFIER)
                                                                 .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                                 .planExecutionId(PLAN_EXECUTION_ID)
                                                                 .name(PLAN_EXECUTION_ID)
                                                                 .runSequence(0)
                                                                 .inputSetYaml(inputSetYamlWithTriggerExpression)
                                                                 .pipelineTemplate(template)
                                                                 .build();

    PlanExecutionMetadata planExecutionMetadata1 =
        PlanExecutionMetadata.builder().inputSetYaml(inputSetYamlWithTriggerExpression).build();

    doReturn(planExecutionMetadata1)
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(PLAN_EXECUTION_ID, Set.of(PlanExecutionMetadataKeys.inputSetYaml));
    doReturn(Optional.of(executionSummaryEntity1))
        .when(pmsExecutionSummaryRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID, !PIPELINE_DELETED);
    doReturn(null).when(pmsGitSyncHelper).getEntityGitDetailsFromBytes(any());
    doReturn(template).when(validateAndMergeHelper).getPipelineTemplate(any(), any(), any(), any(), any());
    doReturn(inputSetWithResolvedTriggerExpressionFilename)
        .when(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(
            inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID, ResolveInputYamlType.RESOLVE_TRIGGER_EXPRESSIONS);
    String inputSet = pmsExecutionService
                          .getInputSetYamlWithTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID,
                              PIPELINE_DELETED, false, ResolveInputYamlType.RESOLVE_TRIGGER_EXPRESSIONS)
                          .getInputSetYaml();

    assertThat(inputSet).isEqualTo(inputSetWithResolvedTriggerExpressionFilename);
    verify(yamlExpressionResolveHelper, times(1))
        .resolveExpressionsInYaml(
            inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID, ResolveInputYamlType.RESOLVE_TRIGGER_EXPRESSIONS);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testMergeRuntimeInputIntoPipeline_1() throws IOException {
    ClassLoader classLoaderWithTriggerExpression = this.getClass().getClassLoader();
    String inputSetWithTriggerExpressionFilename = "inputsetWithTriggerExpression.yaml";
    String inputSetYamlWithTriggerExpression = Resources.toString(
        Objects.requireNonNull(classLoaderWithTriggerExpression.getResource(inputSetWithTriggerExpressionFilename)),
        StandardCharsets.UTF_8);

    String inputSetWithResolvedTriggerExpressionFilename = "inputsetWithResolvedTriggerExpressions.yaml";

    PipelineExecutionSummaryEntity executionSummaryEntity1 = PipelineExecutionSummaryEntity.builder()
                                                                 .accountId(ACCOUNT_ID)
                                                                 .orgIdentifier(ORG_IDENTIFIER)
                                                                 .projectIdentifier(PROJ_IDENTIFIER)
                                                                 .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                                 .planExecutionId(PLAN_EXECUTION_ID)
                                                                 .name(PLAN_EXECUTION_ID)
                                                                 .pipelineTemplate(template)
                                                                 .runSequence(0)
                                                                 .inputSetYaml(inputSetYamlWithTriggerExpression)
                                                                 .build();

    PlanExecutionMetadata planExecutionMetadata1 =
        PlanExecutionMetadata.builder().inputSetYaml(inputSetYamlWithTriggerExpression).build();

    doReturn(planExecutionMetadata1)
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(PLAN_EXECUTION_ID, Set.of(PlanExecutionMetadataKeys.inputSetYaml));
    doReturn(Optional.of(executionSummaryEntity1))
        .when(pmsExecutionSummaryRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionId(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID);
    doReturn(inputSetWithResolvedTriggerExpressionFilename)
        .when(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(
            inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);
    String inputSet = pmsExecutionService.mergeRuntimeInputIntoPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PLAN_EXECUTION_ID, false, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);

    assertThat(inputSet).isEqualTo(InputSetMergeHelper.mergeInputSetIntoPipeline(template, "", false));
    verify(yamlExpressionResolveHelper, times(1))
        .resolveExpressionsInYaml(
            inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);

    doReturn(null)
        .when(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(
            inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);
    inputSet = pmsExecutionService.mergeRuntimeInputIntoPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PLAN_EXECUTION_ID, false, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);
    assertThat(inputSet).isEqualTo(InputSetMergeHelper.mergeInputSetIntoPipeline(template, "", false));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testMergeRuntimeInputIntoPipeline() throws IOException {
    ClassLoader classLoaderWithTriggerExpression = this.getClass().getClassLoader();
    String inputSetWithTriggerExpressionFilename = "inputsetWithTriggerExpression.yaml";
    String inputSetYamlWithTriggerExpression = Resources.toString(
        Objects.requireNonNull(classLoaderWithTriggerExpression.getResource(inputSetWithTriggerExpressionFilename)),
        StandardCharsets.UTF_8);

    String inputSetWithResolvedTriggerExpressionFilename = "inputsetWithResolvedTriggerExpressions.yaml";

    PipelineExecutionSummaryEntity executionSummaryEntity1 = PipelineExecutionSummaryEntity.builder()
                                                                 .accountId(ACCOUNT_ID)
                                                                 .orgIdentifier(ORG_IDENTIFIER)
                                                                 .projectIdentifier(PROJ_IDENTIFIER)
                                                                 .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                                 .planExecutionId(PLAN_EXECUTION_ID)
                                                                 .name(PLAN_EXECUTION_ID)
                                                                 .runSequence(0)
                                                                 .inputSetYaml(inputSetYamlWithTriggerExpression)
                                                                 .build();

    PlanExecutionMetadata planExecutionMetadata1 =
        PlanExecutionMetadata.builder().inputSetYaml(inputSetYamlWithTriggerExpression).build();

    doReturn(planExecutionMetadata1)
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(PLAN_EXECUTION_ID, Set.of(PlanExecutionMetadataKeys.inputSetYaml));
    doReturn(Optional.of(executionSummaryEntity1))
        .when(pmsExecutionSummaryRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionId(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID);
    doReturn(inputSetWithResolvedTriggerExpressionFilename)
        .when(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(
            inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);
    String inputSet = pmsExecutionService.mergeRuntimeInputIntoPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PLAN_EXECUTION_ID, true, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);

    assertThat(inputSet).isEqualTo("");
    verify(yamlExpressionResolveHelper, times(1))
        .resolveExpressionsInYaml(
            inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetCountOfExecutions() {
    pmsExecutionService.getCountOfExecutions(Criteria.where("key").is("value"));
    verify(pmsExecutionSummaryRepository, times(1)).getCountOfExecutionSummary(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetOrchestrationGraph() {
    pmsExecutionService.getOrchestrationGraph("stageNodeId", "planExecutionId", "stageNodeExecutionId");
    verify(graphGenerationService, times(1))
        .generatePartialOrchestrationGraphFromSetupNodeIdAndExecutionId(any(), any(), any());
    pmsExecutionService.getOrchestrationGraph("", "planExecutionId", "stageNodeExecutionId");
    verify(graphGenerationService, times(1)).generateOrchestrationGraphV2(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testSendGraphUpdateEvent() {
    pmsExecutionService.sendGraphUpdateEvent(PipelineExecutionSummaryEntity.builder().build());
    verify(graphGenerationService, times(1)).sendUpdateEventIfAny(any());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetInputSetYamlWithResolvedExpressionsUsingResolvedFieldFromSummary() throws IOException {
    ClassLoader classLoaderWithTriggerExpression = this.getClass().getClassLoader();
    String inputSetWithTriggerExpressionFilename = "inputsetWithTriggerExpression.yaml";
    String inputSetYamlWithTriggerExpression = Resources.toString(
        Objects.requireNonNull(classLoaderWithTriggerExpression.getResource(inputSetWithTriggerExpressionFilename)),
        StandardCharsets.UTF_8);

    String inputSetWithResolvedTriggerExpressionFilename = "inputsetWithResolvedTriggerExpressions.yaml";

    PipelineExecutionSummaryEntity executionSummaryEntity1 =
        PipelineExecutionSummaryEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .pipelineIdentifier(PIPELINE_IDENTIFIER)
            .planExecutionId(PLAN_EXECUTION_ID)
            .name(PLAN_EXECUTION_ID)
            .runSequence(0)
            .inputSetYaml(inputSetYamlWithTriggerExpression)
            .resolvedUserInputSetYaml(inputSetWithResolvedTriggerExpressionFilename)
            .pipelineTemplate(template)
            .build();
    doReturn(Optional.of(executionSummaryEntity1))
        .when(pmsExecutionSummaryRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID, !PIPELINE_DELETED);
    doReturn(null).when(pmsGitSyncHelper).getEntityGitDetailsFromBytes(any());
    doReturn(template).when(validateAndMergeHelper).getPipelineTemplate(any(), any(), any(), any(), any());
    String inputSet = pmsExecutionService
                          .getInputSetYamlWithTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID,
                              PIPELINE_DELETED, false, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS)
                          .getInputSetYaml();

    assertThat(inputSet).isEqualTo(inputSetWithResolvedTriggerExpressionFilename);
    verify(yamlExpressionResolveHelper, times(0))
        .resolveExpressionsInYaml(
            inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);
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

    assertThatThrownBy(
        ()
            -> pmsExecutionService.getInputSetYamlWithTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                INVALID_PLAN_EXECUTION_ID, PIPELINE_DELETED, false, ResolveInputYamlType.UNKNOWN))
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
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionSummaryWithInvalidExecutionId() {
    doReturn(Optional.empty())
        .when(pmsExecutionSummaryRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionId(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID);

    assertThatThrownBy(()
                           -> pmsExecutionService.getPipelineExecutionSummaryEntity(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, INVALID_PLAN_EXECUTION_ID))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Plan Execution Summary does not exist or has been deleted for planExecutionId: "
            + INVALID_PLAN_EXECUTION_ID);
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
            .triggerPayload(TriggerPayload.newBuilder()
                                .setType(Type.MANIFEST)
                                .setManifestData(ManifestData.newBuilder().setVersion("1.0").build())
                                .build())
            .build();

    doReturn(Optional.of(planExecutionMetadata))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(planExecutionID);
    ExecutionMetaDataResponseDetailsDTO executionData = pmsExecutionService.getExecutionDataDetails(planExecutionID);

    assertThat(executionData.getExecutionYaml()).isEqualTo(planExecutionMetadata.getYaml());
    assertThat(executionData.getTriggerPayload().getType()).isEqualTo(Type.MANIFEST);
    assertThat(executionData.getTriggerPayload().getManifestData().getVersion()).isEqualTo("1.0");
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
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetInputSetYamlForRerunWithInvalidExecutionId() {
    doReturn(Optional.empty())
        .when(pmsExecutionSummaryRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, INVALID_PLAN_EXECUTION_ID, true);
    assertThatThrownBy(()
                           -> pmsExecutionService.getInputSetYamlForRerun(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, INVALID_PLAN_EXECUTION_ID, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid request : pipeline execution with planExecutionId " + INVALID_PLAN_EXECUTION_ID
            + " has been deleted");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testFormCriteriaForRepoAndBranchListing() {
    String repoName = "repoName";
    Criteria criteria1 = pmsExecutionService.formCriteriaForRepoAndBranchListing(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, repoName);
    assertThat(criteria1).isEqualTo(Criteria.where(PlanExecutionSummaryKeys.accountId)
                                        .is(ACCOUNT_ID)
                                        .and(PlanExecutionSummaryKeys.orgIdentifier)
                                        .is(ORG_IDENTIFIER)
                                        .and(PlanExecutionSummaryKeys.projectIdentifier)
                                        .is(PROJ_IDENTIFIER)
                                        .and(PlanExecutionSummaryKeys.pipelineIdentifier)
                                        .is(PIPELINE_IDENTIFIER)
                                        .and(PlanExecutionSummaryKeys.entityGitDetailsRepoName)
                                        .is(repoName));
    Criteria criteria2 =
        pmsExecutionService.formCriteriaForRepoAndBranchListing(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "", "");
    assertThat(criteria2).isEqualTo(Criteria.where(PlanExecutionSummaryKeys.accountId)
                                        .is(ACCOUNT_ID)
                                        .and(PlanExecutionSummaryKeys.orgIdentifier)
                                        .is(ORG_IDENTIFIER)
                                        .and(PlanExecutionSummaryKeys.projectIdentifier)
                                        .is(PROJ_IDENTIFIER));
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

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testFindListOfBranchesForInlinePipelines() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .pipelineIdentifier("test")
            .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("root").build())
            .status(ExecutionStatus.SKIPPED)
            .build();
    Criteria criteria = new Criteria();
    criteria.and(PlanExecutionSummaryKeys.accountId).is(ACCOUNT_ID);
    criteria.and(PlanExecutionSummaryKeys.orgIdentifier).is(ORG_IDENTIFIER);
    criteria.and(PlanExecutionSummaryKeys.projectIdentifier).is(PROJ_IDENTIFIER);

    List<PipelineExecutionSummaryEntity> list = new ArrayList<>();
    list.add(pipelineExecutionSummaryEntity);
    CloseableIterator<PipelineExecutionSummaryEntity> iterator =
        PipelineServiceTestHelper.createCloseableIterator(list.iterator());

    doReturn(iterator).when(pmsExecutionSummaryRepository).findListOfRepositories(any());

    assertThatCode(() -> pmsExecutionService.getListOfRepo(criteria)).doesNotThrowAnyException();
    CloseableIterator<PipelineExecutionSummaryEntity> iterator1 =
        PipelineServiceTestHelper.createCloseableIterator(list.iterator());

    doReturn(iterator1).when(pmsExecutionSummaryRepository).findListOfBranches(any());

    assertThatCode(() -> pmsExecutionService.getListOfBranches(criteria)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testFindListOfBranchesForRemotePipelines() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .pipelineIdentifier("test")
            .accountId("account")
            .orgIdentifier("orgIdentifier")
            .projectIdentifier("projectIdentifier")
            .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("root").build())
            .entityGitDetails(EntityGitDetails.builder().branch("main").repoName("test-repo").build())
            .status(ExecutionStatus.SKIPPED)
            .build();
    Criteria criteria = new Criteria();
    criteria.and(PlanExecutionSummaryKeys.accountId).is(ACCOUNT_ID);
    criteria.and(PlanExecutionSummaryKeys.orgIdentifier).is(ORG_IDENTIFIER);
    criteria.and(PlanExecutionSummaryKeys.projectIdentifier).is(PROJ_IDENTIFIER);
    criteria.and(PlanExecutionSummaryKeys.pipelineIdentifier).is("test");
    criteria.and(PlanExecutionSummaryKeys.entityGitDetailsRepoName).is("test-repo");
    criteria.and(PlanExecutionSummaryKeys.entityGitDetailsBranch).is("main");

    List<PipelineExecutionSummaryEntity> list = new ArrayList<>();
    list.add(pipelineExecutionSummaryEntity);
    CloseableIterator<PipelineExecutionSummaryEntity> iterator =
        PipelineServiceTestHelper.createCloseableIterator(list.iterator());

    doReturn(iterator).when(pmsExecutionSummaryRepository).findListOfRepositories(any());
    PMSPipelineListRepoResponse response = pmsExecutionService.getListOfRepo(criteria);

    assertEquals(response.getRepositories().size(), 1);
    assertEquals(response.getRepositories().get(0), "test-repo");

    CloseableIterator<PipelineExecutionSummaryEntity> iterator1 =
        PipelineServiceTestHelper.createCloseableIterator(list.iterator());

    doReturn(iterator1).when(pmsExecutionSummaryRepository).findListOfBranches(any());
    PMSPipelineListBranchesResponse response1 = pmsExecutionService.getListOfBranches(criteria);

    assertEquals(response1.getBranches().size(), 1);
    assertEquals(response1.getBranches().get(0), "main");
  }
}
