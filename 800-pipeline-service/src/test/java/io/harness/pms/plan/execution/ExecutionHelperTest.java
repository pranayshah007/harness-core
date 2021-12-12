package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.OrchestrationService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.retry.RetryExecutionParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.plan.Plan;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.plan.RerunInfo;
import io.harness.pms.contracts.plan.RetryExecutionInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.pipeline.service.PipelineEnforcementService;
import io.harness.pms.plan.creation.PlanCreatorMergeService;
import io.harness.pms.plan.execution.beans.ExecArgs;
import io.harness.pms.rbac.validator.PipelineRbacService;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.executions.PmsExecutionSummaryRespository;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@OwnedBy(PIPELINE)
@RunWith(PowerMockRunner.class)
@PrepareForTest({PlanExecutionUtils.class, UUIDGenerator.class})
public class ExecutionHelperTest extends CategoryTest {
  @InjectMocks ExecutionHelper executionHelper;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock TriggeredByHelper triggeredByHelper;
  @Mock PlanExecutionService planExecutionService;
  @Mock PrincipalInfoHelper principalInfoHelper;
  @Mock PmsGitSyncHelper pmsGitSyncHelper;
  @Mock PMSYamlSchemaService pmsYamlSchemaService;
  @Mock PipelineRbacService pipelineRbacServiceImpl;
  @Mock PlanCreatorMergeService planCreatorMergeService;
  @Mock PipelineEnforcementService pipelineEnforcementService;
  @Mock OrchestrationService orchestrationService;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock PmsExecutionSummaryRespository pmsExecutionSummaryRespository;

  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String pipelineId = "pipelineId";
  String moduleType = "cd";
  String runtimeInputYaml = "pipeline:\n"
      + "  stages:\n"
      + "  - stage:\n"
      + "      identifier: s1\n"
      + "      description: desc\n"
      + "  - stage:\n"
      + "      identifier: s2\n"
      + "      description: desc\n";
  String pipelineYaml = "pipeline:\n"
      + "  stages:\n"
      + "  - stage:\n"
      + "      identifier: s1\n"
      + "      description: <+input>\n"
      + "  - stage:\n"
      + "      identifier: s2\n"
      + "      description: <+input>\n";
  String pipelineYamlWithExpressions = "pipeline:\n"
      + "  stages:\n"
      + "  - stage:\n"
      + "      identifier: \"s1\"\n"
      + "      description: \"desc\"\n"
      + "  - stage:\n"
      + "      identifier: \"s2\"\n"
      + "      description: \"<+pipeline.stages.s1.description>\"\n";
  Map<String, String> expressionValues = Collections.singletonMap("<+pipeline.stages.s1.description>", "desc");
  String mergedPipelineYaml = "pipeline:\n"
      + "  stages:\n"
      + "  - stage:\n"
      + "      identifier: \"s1\"\n"
      + "      description: \"desc\"\n"
      + "  - stage:\n"
      + "      identifier: \"s2\"\n"
      + "      description: \"desc\"\n";
  String mergedPipelineYamlForS2 = "pipeline:\n"
      + "  stages:\n"
      + "  - stage:\n"
      + "      identifier: \"s2\"\n"
      + "      description: \"desc\"\n";
  String originalExecutionId = "originalExecutionId";
  String generatedExecutionId = "newExecId";

  PipelineEntity pipelineEntity;
  PipelineEntity pipelineEntityWithExpressions;
  TriggeredBy triggeredBy;
  ExecutionTriggerInfo executionTriggerInfo;
  ExecutionPrincipalInfo executionPrincipalInfo;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    pipelineEntity = PipelineEntity.builder()
                         .accountId(accountId)
                         .orgIdentifier(orgId)
                         .projectIdentifier(projectId)
                         .identifier(pipelineId)
                         .yaml(pipelineYaml)
                         .runSequence(394)
                         .build();
    pipelineEntityWithExpressions = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYamlWithExpressions)
                                        .runSequence(394)
                                        .build();
    triggeredBy = TriggeredBy.newBuilder().setUuid("userUuid").setIdentifier("username").build();
    executionTriggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggeredBy(triggeredBy).setTriggerType(MANUAL).setIsRerun(false).build();
    executionPrincipalInfo = ExecutionPrincipalInfo.newBuilder().build();
    doNothing().when(pipelineEnforcementService).validateExecutionEnforcementsBasedOnStage(anyString(), any());
    doReturn(394).when(pmsPipelineService).incrementRunSequence(accountId, orgId, projectId, pipelineId, false);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFetchPipelineEntity() {
    doReturn(Optional.of(pipelineEntity)).when(pmsPipelineService).get(accountId, orgId, projectId, pipelineId, false);
    PipelineEntity fetchedPipelineEntity = executionHelper.fetchPipelineEntity(accountId, orgId, projectId, pipelineId);
    assertThat(fetchedPipelineEntity).isEqualTo(fetchedPipelineEntity);

    doReturn(Optional.empty()).when(pmsPipelineService).get(accountId, orgId, projectId, pipelineId, false);
    assertThatThrownBy(() -> executionHelper.fetchPipelineEntity(accountId, orgId, projectId, pipelineId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline with the given ID: pipelineId does not exist or has been deleted");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildTriggerInfo() {
    doReturn(triggeredBy).when(triggeredByHelper).getFromSecurityContext();

    ExecutionTriggerInfo firstExecutionTriggerInfo = executionHelper.buildTriggerInfo(null);
    assertThat(firstExecutionTriggerInfo.getIsRerun()).isEqualTo(false);
    assertThat(firstExecutionTriggerInfo.getTriggerType()).isEqualTo(MANUAL);
    assertThat(firstExecutionTriggerInfo.getTriggeredBy()).isEqualTo(triggeredBy);
    verify(triggeredByHelper, times(1)).getFromSecurityContext();
    verify(planExecutionService, times(0)).get(anyString());

    ExecutionMetadata firstExecutionMetadata =
        ExecutionMetadata.newBuilder().setTriggerInfo(firstExecutionTriggerInfo).build();
    PlanExecution firstPlanExecution = PlanExecution.builder().metadata(firstExecutionMetadata).build();
    doReturn(firstPlanExecution).when(planExecutionService).get(originalExecutionId);

    ExecutionTriggerInfo rerunExecutionTriggerInfo = executionHelper.buildTriggerInfo(originalExecutionId);
    rerunExecutionAssertions(triggeredBy, rerunExecutionTriggerInfo);
    verify(triggeredByHelper, times(2)).getFromSecurityContext();
    verify(planExecutionService, times(1)).get(originalExecutionId);

    ExecutionMetadata secondExecutionMetadata =
        ExecutionMetadata.newBuilder().setTriggerInfo(rerunExecutionTriggerInfo).build();
    PlanExecution secondPlanExecution = PlanExecution.builder().metadata(secondExecutionMetadata).build();
    doReturn(secondPlanExecution).when(planExecutionService).get("originalExecutionId2");

    ExecutionTriggerInfo reRerunExecutionTriggerInfo = executionHelper.buildTriggerInfo("originalExecutionId2");
    rerunExecutionAssertions(triggeredBy, reRerunExecutionTriggerInfo);
    verify(triggeredByHelper, times(3)).getFromSecurityContext();
    verify(planExecutionService, times(1)).get(originalExecutionId);
    verify(planExecutionService, times(1)).get("originalExecutionId2");
  }

  private void rerunExecutionAssertions(TriggeredBy triggeredBy, ExecutionTriggerInfo reRerunExecutionTriggerInfo) {
    assertThat(reRerunExecutionTriggerInfo.getIsRerun()).isEqualTo(true);
    assertThat(reRerunExecutionTriggerInfo.getTriggerType()).isEqualTo(MANUAL);
    assertThat(reRerunExecutionTriggerInfo.getTriggeredBy()).isEqualTo(triggeredBy);
    RerunInfo reRerunInfo = reRerunExecutionTriggerInfo.getRerunInfo();
    assertThat(reRerunInfo.getRootExecutionId()).isEqualTo(originalExecutionId);
    assertThat(reRerunInfo.getRootTriggerType()).isEqualTo(MANUAL);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildExecutionArgs() throws IOException {
    buildExecutionArgsMocks();

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedPipelineYaml).build();
    String mergedYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineEntity.getYaml(), runtimeInputYaml, true);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
            pipelineEntity.getProjectIdentifier(), mergedYaml, true);
    ExecArgs execArgs =
        executionHelper.buildExecutionArgs(pipelineEntity, moduleType, runtimeInputYaml, Collections.emptyList(), null,
            executionTriggerInfo, null, RetryExecutionParameters.builder().isRetry(false).build());
    executionMetadataAssertions(execArgs.getMetadata());

    PlanExecutionMetadata planExecutionMetadata = execArgs.getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(runtimeInputYaml);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYaml);
    assertThat(planExecutionMetadata.getStagesExecutionMetadata().isStagesExecution()).isEqualTo(false);
    assertThat(planExecutionMetadata.getProcessedYaml()).isEqualTo(YamlUtils.injectUuid(mergedPipelineYaml));

    buildExecutionMetadataVerifications();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsWithTemplateRef() throws IOException {
    buildExecutionArgsMocks();
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedPipelineYaml).build();
    String mergedYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineEntity.getYaml(), runtimeInputYaml, true);
    PipelineEntity entityWithTemplateReference = pipelineEntity.withTemplateReference(true);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(entityWithTemplateReference.getAccountId(),
            entityWithTemplateReference.getOrgIdentifier(), entityWithTemplateReference.getProjectIdentifier(),
            mergedYaml, true);
    ExecArgs execArgs = executionHelper.buildExecutionArgs(entityWithTemplateReference, moduleType, runtimeInputYaml,
        Collections.emptyList(), null, executionTriggerInfo, null,
        RetryExecutionParameters.builder().isRetry(false).build());
    executionMetadataAssertions(execArgs.getMetadata());

    PlanExecutionMetadata planExecutionMetadata = execArgs.getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(runtimeInputYaml);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYaml);
    assertThat(planExecutionMetadata.getStagesExecutionMetadata().isStagesExecution()).isEqualTo(false);
    assertThat(planExecutionMetadata.getProcessedYaml()).isEqualTo(YamlUtils.injectUuid(mergedPipelineYaml));

    verify(principalInfoHelper, times(1)).getPrincipalInfoFromSecurityContext();
    verify(pmsGitSyncHelper, times(1)).getGitSyncBranchContextBytesThreadLocal(entityWithTemplateReference);
    verify(pmsYamlSchemaService, times(0)).validateYamlSchema(accountId, orgId, projectId, pipelineYaml);
    verify(pmsYamlSchemaService, times(1)).validateYamlSchema(accountId, orgId, projectId, mergedPipelineYaml);
    verify(pipelineRbacServiceImpl, times(1))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, mergedPipelineYaml);
    verify(pipelineRbacServiceImpl, times(0))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, pipelineYaml);
    verify(planExecutionMetadataService, times(0)).findByPlanExecutionId(anyString());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsForRunStage() throws IOException {
    buildExecutionArgsMocks();

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedPipelineYaml).build();
    String mergedYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineEntity.getYaml(), runtimeInputYaml, true);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
            pipelineEntity.getProjectIdentifier(), mergedYaml, true);
    ExecArgs execArgs = executionHelper.buildExecutionArgs(pipelineEntity, moduleType, runtimeInputYaml,
        Collections.singletonList("s2"), null, executionTriggerInfo, null,
        RetryExecutionParameters.builder().isRetry(false).build());
    executionMetadataAssertions(execArgs.getMetadata());

    PlanExecutionMetadata planExecutionMetadata = execArgs.getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(runtimeInputYaml);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYamlForS2);
    assertThat(planExecutionMetadata.getStagesExecutionMetadata().isStagesExecution()).isEqualTo(true);
    assertThat(planExecutionMetadata.getStagesExecutionMetadata().getFullPipelineYaml()).isEqualTo(mergedPipelineYaml);
    assertThat(planExecutionMetadata.getStagesExecutionMetadata().getStageIdentifiers())
        .isEqualTo(Collections.singletonList("s2"));
    assertThat(planExecutionMetadata.getStagesExecutionMetadata().getExpressionValues()).isNull();
    assertThat(planExecutionMetadata.getProcessedYaml()).isEqualTo(YamlUtils.injectUuid(mergedPipelineYamlForS2));

    buildExecutionMetadataVerifications();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsForRunStageWithExpressions() throws IOException {
    buildExecutionArgsMocks();

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(pipelineYamlWithExpressions).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntityWithExpressions.getAccountId(),
            pipelineEntityWithExpressions.getOrgIdentifier(), pipelineEntityWithExpressions.getProjectIdentifier(),
            pipelineYamlWithExpressions, true);
    ExecArgs execArgs = executionHelper.buildExecutionArgs(pipelineEntityWithExpressions, moduleType, null,
        Collections.singletonList("s2"), expressionValues, executionTriggerInfo, null,
        RetryExecutionParameters.builder().isRetry(false).build());
    executionMetadataAssertions(execArgs.getMetadata());

    PlanExecutionMetadata planExecutionMetadata = execArgs.getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(null);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYamlForS2);
    assertThat(planExecutionMetadata.getStagesExecutionMetadata().isStagesExecution()).isEqualTo(true);
    assertThat(planExecutionMetadata.getStagesExecutionMetadata().getFullPipelineYaml()).isEqualTo(mergedPipelineYaml);
    assertThat(planExecutionMetadata.getStagesExecutionMetadata().getStageIdentifiers())
        .isEqualTo(Collections.singletonList("s2"));
    assertThat(planExecutionMetadata.getStagesExecutionMetadata().getExpressionValues()).isEqualTo(expressionValues);
    assertThat(planExecutionMetadata.getProcessedYaml()).isEqualTo(YamlUtils.injectUuid(mergedPipelineYamlForS2));

    verify(principalInfoHelper, times(1)).getPrincipalInfoFromSecurityContext();
    verify(pmsGitSyncHelper, times(1)).getGitSyncBranchContextBytesThreadLocal(pipelineEntityWithExpressions);
    verify(pmsYamlSchemaService, times(1)).validateYamlSchema(accountId, orgId, projectId, pipelineYamlWithExpressions);
    verify(pipelineRbacServiceImpl, times(1))
        .extractAndValidateStaticallyReferredEntities(
            accountId, orgId, projectId, pipelineId, pipelineYamlWithExpressions);
    verify(planExecutionMetadataService, times(0)).findByPlanExecutionId(anyString());
  }

  private void buildExecutionArgsMocks() {
    PowerMockito.mockStatic(UUIDGenerator.class);
    when(UUIDGenerator.generateUuid()).thenReturn(generatedExecutionId);

    doReturn(executionPrincipalInfo).when(principalInfoHelper).getPrincipalInfoFromSecurityContext();

    doReturn(null).when(pmsGitSyncHelper).getGitSyncBranchContextBytesThreadLocal(pipelineEntity);
    doNothing().when(pmsYamlSchemaService).validateYamlSchema(accountId, orgId, projectId, mergedPipelineYaml);
    doNothing()
        .when(pipelineRbacServiceImpl)
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, mergedPipelineYaml);
  }

  private void executionMetadataAssertions(ExecutionMetadata metadata) {
    assertThat(metadata.getExecutionUuid()).isEqualTo(generatedExecutionId);
    assertThat(metadata.getTriggerInfo()).isEqualTo(executionTriggerInfo);
    assertThat(metadata.getModuleType()).isEqualTo(moduleType);
    assertThat(metadata.getRunSequence()).isEqualTo(394);
    assertThat(metadata.getPipelineIdentifier()).isEqualTo(pipelineId);
    assertThat(metadata.getPrincipalInfo()).isEqualTo(executionPrincipalInfo);
    assertThat(metadata.getGitSyncBranchContext().size()).isEqualTo(0);
  }

  private void buildExecutionMetadataVerifications() {
    verify(principalInfoHelper, times(1)).getPrincipalInfoFromSecurityContext();
    verify(pmsGitSyncHelper, times(1)).getGitSyncBranchContextBytesThreadLocal(pipelineEntity);
    verify(pmsYamlSchemaService, times(0)).validateYamlSchema(accountId, orgId, projectId, pipelineYaml);
    verify(pmsYamlSchemaService, times(1)).validateYamlSchema(accountId, orgId, projectId, mergedPipelineYaml);
    verify(pipelineRbacServiceImpl, times(1))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, mergedPipelineYaml);
    verify(pipelineRbacServiceImpl, times(0))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, pipelineYaml);
    verify(planExecutionMetadataService, times(0)).findByPlanExecutionId(anyString());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineYamlAndValidate() {
    String wrongRuntimeInputYaml = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      description: desc\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      name: s2\n"
        + "      description: desc\n";
    assertThatThrownBy(() -> executionHelper.getPipelineYamlAndValidate(wrongRuntimeInputYaml, pipelineEntity))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testStartExecution() throws IOException {
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
    String startingNodeId = "startingNodeId";
    PlanCreationBlobResponse planCreationBlobResponse =
        PlanCreationBlobResponse.newBuilder().setStartingNodeId(startingNodeId).build();
    doReturn(planCreationBlobResponse)
        .when(planCreatorMergeService)
        .createPlan(accountId, orgId, projectId, executionMetadata, planExecutionMetadata);

    PlanExecution planExecution = PlanExecution.builder().build();
    Plan plan = PlanExecutionUtils.extractPlan(planCreationBlobResponse);
    PowerMockito.mockStatic(PlanExecutionUtils.class);
    when(PlanExecutionUtils.extractPlan(planCreationBlobResponse)).thenReturn(plan);
    ImmutableMap<String, String> abstractions = ImmutableMap.<String, String>builder()
                                                    .put(SetupAbstractionKeys.accountId, accountId)
                                                    .put(SetupAbstractionKeys.orgIdentifier, orgId)
                                                    .put(SetupAbstractionKeys.projectIdentifier, projectId)
                                                    .build();
    doReturn(planExecution)
        .when(orchestrationService)
        .startExecution(plan, abstractions, executionMetadata, planExecutionMetadata);
    PlanExecution createdPlanExecution = executionHelper.startExecution(
        accountId, orgId, projectId, executionMetadata, planExecutionMetadata, false, null, null);
    assertThat(createdPlanExecution).isEqualTo(planExecution);
    verify(planCreatorMergeService, times(1))
        .createPlan(accountId, orgId, projectId, executionMetadata, planExecutionMetadata);
    verify(orchestrationService, times(1)).startExecution(plan, abstractions, executionMetadata, planExecutionMetadata);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testBuildRetryInfo() {
    // isRetry: false
    RetryExecutionInfo retryExecutionInfo = executionHelper.buildRetryInfo(false, null);
    assertThat(retryExecutionInfo.getIsRetry()).isEqualTo(false);

    // isRetry: true and originalId: null
    retryExecutionInfo = executionHelper.buildRetryInfo(true, null);
    assertThat(retryExecutionInfo.getIsRetry()).isEqualTo(false);

    // isRetry: true and originalId: empty
    retryExecutionInfo = executionHelper.buildRetryInfo(true, "");
    assertThat(retryExecutionInfo.getIsRetry()).isEqualTo(false);

    // isRetry: true
    when(pmsExecutionSummaryRespository.fetchRootRetryExecutionId("originalId")).thenReturn("rootParentId");
    retryExecutionInfo = executionHelper.buildRetryInfo(true, "originalId");
    assertThat(retryExecutionInfo.getIsRetry()).isEqualTo(true);
    assertThat(retryExecutionInfo.getParentRetryId()).isEqualTo("originalId");
    assertThat(retryExecutionInfo.getRootExecutionId()).isEqualTo("rootParentId");
  }
}