/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.OrchestrationService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.retry.RetryExecutionParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.gitsync.beans.StoreType;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.plan.Plan;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.plan.RerunInfo;
import io.harness.pms.contracts.plan.RetryExecutionInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.pipeline.service.PipelineEnforcementService;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.plan.creation.PlanCreatorMergeService;
import io.harness.pms.plan.execution.beans.ExecArgs;
import io.harness.pms.rbac.validator.PipelineRbacService;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(PIPELINE)
@PrepareForTest({PlanExecutionUtils.class, UUIDGenerator.class})
public class ExecutionHelperTest extends CategoryTest {
  @InjectMocks ExecutionHelper executionHelper;
  @Mock PMSPipelineService pmsPipelineService;

  @Mock PipelineMetadataService pipelineMetadataService;

  @Mock PipelineGovernanceService pipelineGovernanceService;
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
  @Mock PmsExecutionSummaryRepository pmsExecutionSummaryRespository;
  @Mock PmsFeatureFlagHelper featureFlagService;
  @Mock RollbackModeExecutionHelper rollbackModeExecutionHelper;

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
      + "      description: <+input>\n"
      + "  allowStageExecutions: true\n";
  String pipelineYamlWithExpressions = "pipeline:\n"
      + "  stages:\n"
      + "  - stage:\n"
      + "      identifier: \"s1\"\n"
      + "      description: \"desc\"\n"
      + "  - stage:\n"
      + "      identifier: \"s2\"\n"
      + "      description: \"<+pipeline.stages.s1.description>\"\n"
      + "  allowStageExecutions: true\n";
  Map<String, String> expressionValues = Collections.singletonMap("<+pipeline.stages.s1.description>", "desc");
  String mergedPipelineYaml = "pipeline:\n"
      + "  stages:\n"
      + "  - stage:\n"
      + "      identifier: \"s1\"\n"
      + "      description: \"desc\"\n"
      + "  - stage:\n"
      + "      identifier: \"s2\"\n"
      + "      description: \"desc\"\n"
      + "  allowStageExecutions: true\n";

  String mergedPipelineYamlForS2 = "pipeline:\n"
      + "  stages:\n"
      + "  - stage:\n"
      + "      identifier: \"s2\"\n"
      + "      description: \"desc\"\n"
      + "  allowStageExecutions: true\n";

  String mergedPipelineYamlForS2WithExpression = "pipeline:\n"
      + "  stages:\n"
      + "  - stage:\n"
      + "      identifier: \"s2\"\n"
      + "      description: \"<+pipeline.stages.s1.description>\"\n"
      + "  allowStageExecutions: true\n";
  String originalExecutionId = "originalExecutionId";
  String generatedExecutionId = "newExecId";
  String pipelineYamlV1;

  PipelineEntity pipelineEntity;
  PipelineEntity pipelineEntityWithExpressions;
  TriggeredBy triggeredBy;
  ExecutionTriggerInfo executionTriggerInfo;
  ExecutionPrincipalInfo executionPrincipalInfo;
  MockedStatic<UUIDGenerator> aStatic;

  public ExecutionHelperTest() throws IOException {}

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
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
    aStatic = Mockito.mockStatic(UUIDGenerator.class);
    aStatic.when(UUIDGenerator::generateUuid).thenReturn(generatedExecutionId);

    pipelineYamlV1 = readFile("simplified-pipeline.yaml");
  }

  @After
  public void afterMethod() {
    aStatic.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFetchPipelineEntity() {
    doReturn(Optional.of(pipelineEntity))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false);
    PipelineEntity fetchedPipelineEntity = executionHelper.fetchPipelineEntity(accountId, orgId, projectId, pipelineId);
    assertThat(fetchedPipelineEntity).isEqualTo(fetchedPipelineEntity);

    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false);
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
    verify(planExecutionService, times(0)).getExecutionMetadataFromPlanExecution(anyString());

    ExecutionMetadata firstExecutionMetadata =
        ExecutionMetadata.newBuilder().setTriggerInfo(firstExecutionTriggerInfo).build();
    doReturn(firstExecutionMetadata)
        .when(planExecutionService)
        .getExecutionMetadataFromPlanExecution(originalExecutionId);

    ExecutionTriggerInfo rerunExecutionTriggerInfo = executionHelper.buildTriggerInfo(originalExecutionId);
    rerunExecutionAssertions(triggeredBy, rerunExecutionTriggerInfo);
    verify(triggeredByHelper, times(2)).getFromSecurityContext();
    verify(planExecutionService, times(1)).getExecutionMetadataFromPlanExecution(originalExecutionId);

    ExecutionMetadata secondExecutionMetadata =
        ExecutionMetadata.newBuilder().setTriggerInfo(rerunExecutionTriggerInfo).build();
    doReturn(secondExecutionMetadata)
        .when(planExecutionService)
        .getExecutionMetadataFromPlanExecution("originalExecutionId2");

    ExecutionTriggerInfo reRerunExecutionTriggerInfo = executionHelper.buildTriggerInfo("originalExecutionId2");
    rerunExecutionAssertions(triggeredBy, reRerunExecutionTriggerInfo);
    verify(triggeredByHelper, times(3)).getFromSecurityContext();
    verify(planExecutionService, times(1)).getExecutionMetadataFromPlanExecution(originalExecutionId);
    verify(planExecutionService, times(1)).getExecutionMetadataFromPlanExecution("originalExecutionId2");
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
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(pipelineEntity.getAccountId(),
            pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), mergedYaml, true, false,
            BOOLEAN_FALSE_VALUE);
    ExecArgs execArgs =
        executionHelper.buildExecutionArgs(pipelineEntity, moduleType, runtimeInputYaml, Collections.emptyList(), null,
            executionTriggerInfo, null, RetryExecutionParameters.builder().isRetry(false).build(), false, false);
    executionMetadataAssertions(execArgs.getMetadata());
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.UNDEFINED);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEmpty();
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(PipelineVersion.V0);
    assertThat(execArgs.getMetadata().getExecutionMode()).isEqualTo(ExecutionMode.NORMAL);

    PlanExecutionMetadata planExecutionMetadata = execArgs.getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(runtimeInputYaml);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYaml);
    assertThat(planExecutionMetadata.getStagesExecutionMetadata().isStagesExecution()).isEqualTo(false);
    assertThat(planExecutionMetadata.getProcessedYaml()).isEqualTo(YamlUtils.injectUuid(mergedPipelineYaml));
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(accountId, orgId, projectId, mergedPipelineYaml, true);

    buildExecutionMetadataVerifications(pipelineEntity);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsForInlinePipeline() throws IOException {
    buildExecutionArgsMocks();
    PipelineEntity inlinePipeline = pipelineEntity.withStoreType(StoreType.INLINE);

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedPipelineYaml).build();
    String mergedYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(inlinePipeline.getYaml(), runtimeInputYaml, true);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(inlinePipeline.getAccountId(),
            inlinePipeline.getOrgIdentifier(), inlinePipeline.getProjectIdentifier(), mergedYaml, true, false,
            BOOLEAN_FALSE_VALUE);
    ExecArgs execArgs =
        executionHelper.buildExecutionArgs(inlinePipeline, moduleType, runtimeInputYaml, Collections.emptyList(), null,
            executionTriggerInfo, null, RetryExecutionParameters.builder().isRetry(false).build(), false, false);
    executionMetadataAssertions(execArgs.getMetadata());
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.INLINE);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEmpty();
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(PipelineVersion.V0);

    PlanExecutionMetadata planExecutionMetadata = execArgs.getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(runtimeInputYaml);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYaml);
    assertThat(planExecutionMetadata.getStagesExecutionMetadata().isStagesExecution()).isEqualTo(false);
    assertThat(planExecutionMetadata.getProcessedYaml()).isEqualTo(YamlUtils.injectUuid(mergedPipelineYaml));
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(accountId, orgId, projectId, mergedPipelineYaml, true);

    buildExecutionMetadataVerifications(inlinePipeline);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsForRemotePipeline() throws IOException {
    buildExecutionArgsMocks();
    PipelineEntity remotePipeline = pipelineEntity.withStoreType(StoreType.REMOTE).withConnectorRef("conn");

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedPipelineYaml).build();
    String mergedYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(remotePipeline.getYaml(), runtimeInputYaml, true);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(remotePipeline.getAccountId(),
            remotePipeline.getOrgIdentifier(), remotePipeline.getProjectIdentifier(), mergedYaml, true, false,
            BOOLEAN_FALSE_VALUE);
    ExecArgs execArgs =
        executionHelper.buildExecutionArgs(remotePipeline, moduleType, runtimeInputYaml, Collections.emptyList(), null,
            executionTriggerInfo, null, RetryExecutionParameters.builder().isRetry(false).build(), false, false);
    executionMetadataAssertions(execArgs.getMetadata());
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.REMOTE);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEqualTo("conn");
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(PipelineVersion.V0);

    PlanExecutionMetadata planExecutionMetadata = execArgs.getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(runtimeInputYaml);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYaml);
    assertThat(planExecutionMetadata.getStagesExecutionMetadata().isStagesExecution()).isEqualTo(false);
    assertThat(planExecutionMetadata.getProcessedYaml()).isEqualTo(YamlUtils.injectUuid(mergedPipelineYaml));
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(accountId, orgId, projectId, mergedPipelineYaml, true);

    buildExecutionMetadataVerifications(remotePipeline);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsWithTemplateRef() throws IOException {
    buildExecutionArgsMocks();
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedPipelineYaml).build();
    String mergedYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineEntity.getYaml(), runtimeInputYaml, true);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(pipelineEntity.getAccountId(),
            pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), mergedYaml, true, false,
            BOOLEAN_FALSE_VALUE);
    ExecArgs execArgs =
        executionHelper.buildExecutionArgs(pipelineEntity, moduleType, runtimeInputYaml, Collections.emptyList(), null,
            executionTriggerInfo, null, RetryExecutionParameters.builder().isRetry(false).build(), false, false);
    executionMetadataAssertions(execArgs.getMetadata());
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.UNDEFINED);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEmpty();
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(PipelineVersion.V0);

    PlanExecutionMetadata planExecutionMetadata = execArgs.getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(runtimeInputYaml);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYaml);
    assertThat(planExecutionMetadata.getStagesExecutionMetadata().isStagesExecution()).isEqualTo(false);
    assertThat(planExecutionMetadata.getProcessedYaml()).isEqualTo(YamlUtils.injectUuid(mergedPipelineYaml));

    verify(principalInfoHelper, times(1)).getPrincipalInfoFromSecurityContext();
    verify(pmsGitSyncHelper, times(1)).getGitSyncBranchContextBytesThreadLocal(pipelineEntity, null, null, null);
    verify(pmsYamlSchemaService, times(0)).validateYamlSchema(accountId, orgId, projectId, pipelineYaml);
    verify(pmsYamlSchemaService, times(1)).validateYamlSchema(accountId, orgId, projectId, mergedPipelineYaml);
    verify(pipelineRbacServiceImpl, times(1))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, mergedPipelineYaml);
    verify(pipelineRbacServiceImpl, times(0))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, pipelineYaml);
    verify(planExecutionMetadataService, times(0)).findByPlanExecutionId(anyString());
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(accountId, orgId, projectId, mergedPipelineYaml, true);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsForRunStage() throws IOException {
    buildExecutionArgsMocks();

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedPipelineYamlForS2).build();
    String mergedYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineEntity.getYaml(), runtimeInputYaml, true);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(pipelineEntity.getAccountId(),
            pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), mergedYaml, true, false,
            BOOLEAN_FALSE_VALUE);
    ExecArgs execArgs = executionHelper.buildExecutionArgs(pipelineEntity, moduleType, runtimeInputYaml,
        Collections.singletonList("s2"), null, executionTriggerInfo, null,
        RetryExecutionParameters.builder().isRetry(false).build(), false, false);
    executionMetadataAssertions(execArgs.getMetadata());
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.UNDEFINED);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEmpty();
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(PipelineVersion.V0);

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
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(accountId, orgId, projectId, mergedPipelineYamlForS2, true);

    verify(principalInfoHelper, times(1)).getPrincipalInfoFromSecurityContext();
    verify(pmsGitSyncHelper, times(1))
        .getGitSyncBranchContextBytesThreadLocal(pipelineEntity, pipelineEntity.getStoreType(), null, null);
    verify(pmsYamlSchemaService, times(1)).validateYamlSchema(accountId, orgId, projectId, mergedPipelineYaml);
    if (pipelineEntity.getStoreType() != StoreType.REMOTE) {
      verify(pipelineRbacServiceImpl, times(1))
          .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, mergedPipelineYaml);
    }
    verify(pipelineRbacServiceImpl, times(0))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, pipelineYaml);
    verify(planExecutionMetadataService, times(0)).findByPlanExecutionId(anyString());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsForRunStageWithExpressions() throws IOException {
    buildExecutionArgsMocks();

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder()
            .mergedPipelineYaml(pipelineYamlWithExpressions)
            .mergedPipelineYamlWithTemplateRef(pipelineYamlWithExpressions)
            .build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(pipelineEntityWithExpressions.getAccountId(),
            pipelineEntityWithExpressions.getOrgIdentifier(), pipelineEntityWithExpressions.getProjectIdentifier(),
            pipelineYamlWithExpressions, true, true, BOOLEAN_FALSE_VALUE);
    ExecArgs execArgs = executionHelper.buildExecutionArgs(pipelineEntityWithExpressions, moduleType, null,
        Collections.singletonList("s2"), expressionValues, executionTriggerInfo, null,
        RetryExecutionParameters.builder().isRetry(false).build(), false, false);
    executionMetadataAssertions(execArgs.getMetadata());
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.UNDEFINED);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEmpty();
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(PipelineVersion.V0);

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
    verify(pmsGitSyncHelper, times(1))
        .getGitSyncBranchContextBytesThreadLocal(pipelineEntityWithExpressions, null, null, null);
    verify(pmsYamlSchemaService, times(1)).validateYamlSchema(accountId, orgId, projectId, pipelineYamlWithExpressions);
    verify(pipelineRbacServiceImpl, times(1))
        .extractAndValidateStaticallyReferredEntities(
            accountId, orgId, projectId, pipelineId, pipelineYamlWithExpressions);
    verify(planExecutionMetadataService, times(0)).findByPlanExecutionId(anyString());
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(accountId, orgId, projectId, mergedPipelineYamlForS2WithExpression, true);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsWithError() {
    // this will throw a WingsException in the try block, and the first catch block should be invoked
    assertThatThrownBy(()
                           -> executionHelper.buildExecutionArgs(pipelineEntity, "CD", null, Collections.emptyList(),
                               Collections.emptyMap(), null, null, null, false, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Debug executions are not allowed for pipeline [pipelineId]");

    // this will throw an NPE in the try block, and the second catch block should be invoked
    assertThatThrownBy(()
                           -> executionHelper.buildExecutionArgs(
                               pipelineEntity, null, null, null, null, null, null, null, false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Failed to start execution for Pipeline.");
  }

  private void buildExecutionArgsMocks() {
    doReturn(executionPrincipalInfo).when(principalInfoHelper).getPrincipalInfoFromSecurityContext();
    doReturn(394).when(pipelineMetadataService).incrementRunSequence(any());
    doReturn(null).when(pmsGitSyncHelper).getGitSyncBranchContextBytesThreadLocal(pipelineEntity, null, null, null);
    doReturn(true).when(pmsYamlSchemaService).validateYamlSchema(accountId, orgId, projectId, mergedPipelineYaml);
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

  private void buildExecutionMetadataVerifications(PipelineEntity pipelineEntity) {
    verify(principalInfoHelper, times(1)).getPrincipalInfoFromSecurityContext();
    verify(pmsGitSyncHelper, times(1))
        .getGitSyncBranchContextBytesThreadLocal(
            pipelineEntity, pipelineEntity.getStoreType(), null, pipelineEntity.getConnectorRef());
    verify(pmsYamlSchemaService, times(0)).validateYamlSchema(accountId, orgId, projectId, pipelineYaml);
    verify(pmsYamlSchemaService, times(1)).validateYamlSchema(accountId, orgId, projectId, mergedPipelineYaml);
    if (pipelineEntity.getStoreType() != StoreType.REMOTE) {
      verify(pipelineRbacServiceImpl, times(1))
          .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, mergedPipelineYaml);
    }
    verify(pipelineRbacServiceImpl, times(0))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, pipelineYaml);
    verify(planExecutionMetadataService, times(0)).findByPlanExecutionId(anyString());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineYamlAndValidateForRbacCheck() throws IOException {
    String pipelineYaml = "pipeline:\n"
        + "  template:\n"
        + "    templateInputs:\n"
        + "      serviceRef: <+input>\n";
    String mergedRuntimeInputYaml = "pipeline:\n"
        + "  template:\n"
        + "    templateInputs:\n"
        + "      serviceRef: \"svc_v2\"\n";
    String resolvedYaml = "pipeline:\n"
        + "  stage:\n"
        + "    serviceConfig:\n"
        + "      serviceRef: \"svc_v2\"\n";
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(resolvedYaml).build())
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(
            any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();
    executionHelper.getPipelineYamlAndValidateStaticallyReferredEntities(mergedRuntimeInputYaml, pipelineEntity);
    verify(pipelineRbacServiceImpl, times(1))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, mergedRuntimeInputYaml);
    verify(pipelineRbacServiceImpl, times(0))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, resolvedYaml);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineYamlAndValidateForPipelineWithAllowedValues() throws IOException {
    String pipelineYamlWithAllowedValues = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "      description: \"<+input>.allowedValues(a, b)\"\n";
    String runtimeInputYaml = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "      description: \"a\"\n";
    String mergedYamlWithValidators = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "      description: \"a.allowedValues(a, b)\"\n";
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .yaml(pipelineYamlWithAllowedValues)
                                        .build();
    TemplateMergeResponseDTO response =
        executionHelper.getPipelineYamlAndValidateStaticallyReferredEntities(runtimeInputYaml, pipelineEntity);
    assertThat(response.getMergedPipelineYaml()).isEqualTo(mergedYamlWithValidators);
    assertThat(response.getMergedPipelineYamlWithTemplateRef()).isEqualTo(mergedYamlWithValidators);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineYamlAndValidateForInlineAndRemotePipelines() throws IOException {
    PipelineEntity inline = PipelineEntity.builder()
                                .accountId(accountId)
                                .orgIdentifier(orgId)
                                .projectIdentifier(projectId)
                                .identifier(pipelineId)
                                .yaml(mergedPipelineYaml)
                                .runSequence(394)
                                .storeType(StoreType.INLINE)
                                .build();
    executionHelper.getPipelineYamlAndValidateStaticallyReferredEntities("", inline);

    doThrow(
        new InvalidRequestException(
            "pipelineRbacServiceImpl.extractAndValidateStaticallyReferredEntities(...) was not supposed to be called"))
        .when(pipelineRbacServiceImpl)
        .extractAndValidateStaticallyReferredEntities(any(), any(), any(), any(), any());
    PipelineEntity remote = PipelineEntity.builder()
                                .accountId(accountId)
                                .orgIdentifier(orgId)
                                .projectIdentifier(projectId)
                                .identifier(pipelineId)
                                .yaml(mergedPipelineYaml)
                                .runSequence(394)
                                .storeType(StoreType.REMOTE)
                                .build();
    executionHelper.getPipelineYamlAndValidateStaticallyReferredEntities("", remote);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  @Ignore("Will remove this ignore annotation and modify this test when service env changes are done")
  public void testGetPipelineYamlAndValidateParallelAndIndependentStages() {
    String pipelineYaml = readFile("pipelineTest.yaml");
    String inputSetYaml = readFile("inputSetTest.yaml");
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .yaml(pipelineYaml)
                                        .build();
    assertThatThrownBy(
        () -> executionHelper.getPipelineYamlAndValidateStaticallyReferredEntities(inputSetYaml, pipelineEntity))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetPipelineYamlAndValidateWhenOPAFFisOff() throws IOException {
    String yamlWithTempRef = "pipeline:\n"
        + "  name: \"ww\"\n"
        + "  template:\n"
        + "    templateRef: \"new_pipeline_template_name\"\n"
        + "    versionLabel: \"v1\"\n"
        + "  tags: {}\n";
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(yamlWithTempRef)
                                        .build();
    when(featureFlagService.isEnabled(pipelineEntity.getAccountId(), FeatureName.OPA_PIPELINE_GOVERNANCE))
        .thenReturn(false);
    TemplateMergeResponseDTO templateMergeResponse =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yamlWithTempRef).build();

    doReturn(templateMergeResponse)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(pipelineEntity.getAccountId(),
            pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), yamlWithTempRef, true, false,
            BOOLEAN_FALSE_VALUE);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        executionHelper.getPipelineYamlAndValidateStaticallyReferredEntities("", pipelineEntity);
    assertThat(templateMergeResponseDTO.getMergedPipelineYaml())
        .isEqualTo(templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testStartExecution() throws IOException {
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setHarnessVersion(PipelineVersion.V0).build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
    String startingNodeId = "startingNodeId";
    PlanCreationBlobResponse planCreationBlobResponse =
        PlanCreationBlobResponse.newBuilder().setStartingNodeId(startingNodeId).build();
    doReturn(planCreationBlobResponse)
        .when(planCreatorMergeService)
        .createPlanVersioned(accountId, orgId, projectId, PipelineVersion.V0, executionMetadata, planExecutionMetadata);

    PlanExecution planExecution = PlanExecution.builder().build();
    Plan plan = PlanExecutionUtils.extractPlan(planCreationBlobResponse);
    MockedStatic<PlanExecutionUtils> aStatic = Mockito.mockStatic(PlanExecutionUtils.class);
    aStatic.when(() -> PlanExecutionUtils.extractPlan(planCreationBlobResponse)).thenReturn(plan);
    ImmutableMap<String, String> abstractions = ImmutableMap.<String, String>builder()
                                                    .put(SetupAbstractionKeys.accountId, accountId)
                                                    .put(SetupAbstractionKeys.orgIdentifier, orgId)
                                                    .put(SetupAbstractionKeys.projectIdentifier, projectId)
                                                    .build();
    doReturn(planExecution)
        .when(orchestrationService)
        .startExecution(plan, abstractions, executionMetadata, planExecutionMetadata);
    PlanExecution createdPlanExecution = executionHelper.startExecution(
        accountId, orgId, projectId, executionMetadata, planExecutionMetadata, false, null, null, null);
    assertThat(createdPlanExecution).isEqualTo(planExecution);
    verify(planCreatorMergeService, times(1))
        .createPlanVersioned(accountId, orgId, projectId, PipelineVersion.V0, executionMetadata, planExecutionMetadata);
    verify(orchestrationService, times(1)).startExecution(plan, abstractions, executionMetadata, planExecutionMetadata);
    verify(rollbackModeExecutionHelper, times(0)).transformPlanForRollbackMode(any(), anyString(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testStartExecutionInPostExecutionRollbackMode() throws IOException {
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setHarnessVersion(PipelineVersion.V0)
                                              .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                                              .build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
    String startingNodeId = "startingNodeId";
    PlanCreationBlobResponse planCreationBlobResponse =
        PlanCreationBlobResponse.newBuilder().setStartingNodeId(startingNodeId).build();
    doReturn(planCreationBlobResponse)
        .when(planCreatorMergeService)
        .createPlanVersioned(accountId, orgId, projectId, PipelineVersion.V0, executionMetadata, planExecutionMetadata);

    PlanExecution planExecution = PlanExecution.builder().build();
    Plan plan = PlanExecutionUtils.extractPlan(planCreationBlobResponse);
    MockedStatic<PlanExecutionUtils> aStatic = Mockito.mockStatic(PlanExecutionUtils.class);
    aStatic.when(() -> PlanExecutionUtils.extractPlan(planCreationBlobResponse)).thenReturn(plan);
    ImmutableMap<String, String> abstractions = ImmutableMap.<String, String>builder()
                                                    .put(SetupAbstractionKeys.accountId, accountId)
                                                    .put(SetupAbstractionKeys.orgIdentifier, orgId)
                                                    .put(SetupAbstractionKeys.projectIdentifier, projectId)
                                                    .build();
    doReturn(plan)
        .when(rollbackModeExecutionHelper)
        .transformPlanForRollbackMode(plan, "prevId", Collections.emptyList(), ExecutionMode.POST_EXECUTION_ROLLBACK);
    doReturn(planExecution)
        .when(orchestrationService)
        .startExecution(plan, abstractions, executionMetadata, planExecutionMetadata);
    PlanExecution createdPlanExecution = executionHelper.startExecution(
        accountId, orgId, projectId, executionMetadata, planExecutionMetadata, false, null, "prevId", null);
    assertThat(createdPlanExecution).isEqualTo(planExecution);
    verify(planCreatorMergeService, times(1))
        .createPlanVersioned(accountId, orgId, projectId, PipelineVersion.V0, executionMetadata, planExecutionMetadata);
    verify(orchestrationService, times(1)).startExecution(plan, abstractions, executionMetadata, planExecutionMetadata);
    verify(rollbackModeExecutionHelper, times(1))
        .transformPlanForRollbackMode(plan, "prevId", Collections.emptyList(), ExecutionMode.POST_EXECUTION_ROLLBACK);
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

  private void buildExecutionMetadataVerificationsWithV1Version(PipelineEntity pipelineEntity) {
    verify(principalInfoHelper, times(1)).getPrincipalInfoFromSecurityContext();
    verify(pmsGitSyncHelper, times(1))
        .getGitSyncBranchContextBytesThreadLocal(pipelineEntity, pipelineEntity.getStoreType(), null, null);
    verify(pmsYamlSchemaService, times(0)).validateYamlSchema(accountId, orgId, projectId, pipelineYaml);
    verify(pipelineRbacServiceImpl, times(0))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, pipelineYaml);
    verify(planExecutionMetadataService, times(0)).findByPlanExecutionId(anyString());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsV1Yaml() {
    doReturn(executionPrincipalInfo).when(principalInfoHelper).getPrincipalInfoFromSecurityContext();
    pipelineEntity.setYaml(pipelineYamlV1);
    pipelineEntity.setHarnessVersion(PipelineVersion.V1);
    ExecArgs execArgs = executionHelper.buildExecutionArgs(pipelineEntity, moduleType, "", Collections.emptyList(),
        null, executionTriggerInfo, null, RetryExecutionParameters.builder().isRetry(false).build(), false, false);
    assertThat(execArgs.getMetadata().getExecutionUuid()).isEqualTo(generatedExecutionId);
    assertThat(execArgs.getMetadata().getTriggerInfo()).isEqualTo(executionTriggerInfo);
    assertThat(execArgs.getMetadata().getModuleType()).isEqualTo(moduleType);
    assertThat(execArgs.getMetadata().getPipelineIdentifier()).isEqualTo(pipelineId);
    assertThat(execArgs.getMetadata().getPrincipalInfo()).isEqualTo(executionPrincipalInfo);
    assertThat(execArgs.getMetadata().getGitSyncBranchContext().size()).isEqualTo(0);
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.UNDEFINED);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEmpty();
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(PipelineVersion.V1);

    PlanExecutionMetadata planExecutionMetadata = execArgs.getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEmpty();
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(pipelineYamlV1);
    assertThat(planExecutionMetadata.getStagesExecutionMetadata().isStagesExecution()).isFalse();
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(accountId, orgId, projectId, pipelineYamlV1, true);
    verify(principalInfoHelper, times(1)).getPrincipalInfoFromSecurityContext();
    verify(pmsGitSyncHelper, times(1))
        .getGitSyncBranchContextBytesThreadLocal(pipelineEntity, pipelineEntity.getStoreType(), null, null);
    verify(pmsYamlSchemaService, times(0)).validateYamlSchema(accountId, orgId, projectId, pipelineYamlV1);
    verify(pipelineRbacServiceImpl, times(0))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, pipelineYamlV1);
    verify(planExecutionMetadataService, times(0)).findByPlanExecutionId(anyString());
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }
}
