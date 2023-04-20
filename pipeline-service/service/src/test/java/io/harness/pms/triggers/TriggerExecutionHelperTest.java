/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.execution.PlanExecution.EXEC_TAG_SET_BY_TRIGGER;
import static io.harness.ngtriggers.Constants.EVENT_CORRELATION_ID;
import static io.harness.ngtriggers.Constants.GIT_USER;
import static io.harness.ngtriggers.Constants.TRIGGER_REF;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TriggerException;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.scheduled.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.scheduled.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.custom.CustomTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.GithubSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubPRSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.opaclient.model.OpaConstants;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.contracts.triggers.Type;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceServiceImpl;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PipelineEnforcementService;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.plan.execution.ExecutionHelper;
import io.harness.pms.triggers.beans.TriggerPlanExecArgs;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.User;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
public class TriggerExecutionHelperTest extends CategoryTest {
  @Inject @InjectMocks TriggerExecutionHelper triggerExecutionHelper;
  private final String accountId = "acc";
  private final String orgId = "org";
  private final String projectId = "proj";
  private final String pipelineId = "target";

  private NGTriggerEntity ngTriggerEntity;
  private TriggerWebhookEvent triggerWebhookEvent;
  private PipelineEntity pipelineEntityV1;

  private final ExecutionMetadata metadata = ExecutionMetadata.newBuilder().build();
  private final PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
  private final PlanExecution planExecution = PlanExecution.builder().build();

  @Mock PmsGitSyncHelper pmsGitSyncHelper;
  @Mock NGTriggerElementMapper ngTriggerElementMapper;
  @Mock PipelineServiceClient pipelineServiceClient;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PipelineMetadataService pipelineMetadataService;
  @Mock PipelineEnforcementService pipelineEnforcementService;
  @Mock PipelineGovernanceServiceImpl pipelineGovernanceService;
  @Mock ExecutionHelper executionHelper;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Before
  public void setUp() {
    triggerWebhookEvent =
        TriggerWebhookEvent.builder()
            .sourceRepoType("CUSTOM")
            .headers(Arrays.asList(
                HeaderConfig.builder().key("content-type").values(Arrays.asList("application/json")).build(),
                HeaderConfig.builder().key("X-GitHub-Event").values(Arrays.asList("someValue")).build()))
            .payload("{branch: main}")
            .build();
    MockitoAnnotations.initMocks(this);

    ngTriggerEntity = NGTriggerEntity.builder()
                          .accountId("acc")
                          .orgIdentifier("org")
                          .projectIdentifier("proj")
                          .targetIdentifier("target")
                          .identifier("trigger")
                          .build();

    String simplifiedYaml = readFile("simplified-pipeline.yaml");
    pipelineEntityV1 = PipelineEntity.builder()
                           .accountId(accountId)
                           .orgIdentifier(orgId)
                           .projectIdentifier(projectId)
                           .identifier(pipelineId)
                           .yaml(simplifiedYaml)
                           .runSequence(394)
                           .harnessVersion(PipelineVersion.V1)
                           .build();
  }

  private String readFile(String filename) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerPlanExecArgs() throws Exception {
    PipelineEntity pipelineEntity =
        PipelineEntity.builder().repo("repo").filePath("filePath").connectorRef("connectorRef").build();

    NGTriggerEntity ngTriggerEntityGitSync = NGTriggerEntity.builder()
                                                 .accountId("ACCOUNT_ID")
                                                 .orgIdentifier("ORG_IDENTIFIER")
                                                 .projectIdentifier("PROJ_IDENTIFIER")
                                                 .targetIdentifier("PIPELINE_IDENTIFIER")
                                                 .identifier("IDENTIFIER")
                                                 .name("NAME")
                                                 .targetType(TargetType.PIPELINE)
                                                 .type(NGTriggerType.WEBHOOK)
                                                 .version(0L)
                                                 .build();

    TriggerDetails triggerDetails = TriggerDetails.builder()
                                        .ngTriggerEntity(ngTriggerEntityGitSync)
                                        .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                                               .inputSetRefs(Arrays.asList("inputSet1", "inputSet2"))
                                                               .pipelineBranchName("pipelineBranchName")
                                                               .build())
                                        .build();

    when(ngTriggerElementMapper.toTriggerConfigV2(ngTriggerEntityGitSync))
        .thenReturn(triggerDetails.getNgTriggerConfigV2());
    doReturn(Optional.of(pipelineEntity))
        .when(pmsPipelineService)
        .getPipeline("ACCOUNT_ID", "ORG_IDENTIFIER", "PROJ_IDENTIFIER", "PIPELINE_IDENTIFIER", false, false);
    when(pmsGitSyncHelper.serializeGitSyncBranchContext(any())).thenReturn(ByteString.copyFrom(new byte[2]));
    TriggerPlanExecArgs triggerPlanExecArgs =
        triggerExecutionHelper.getTriggerPlanExecArgs(triggerDetails, triggerWebhookEvent);
    assertThat(triggerPlanExecArgs.getPipelineEntity()).isEqualToComparingFieldByField(pipelineEntity);
    assertThat(triggerPlanExecArgs.getGitSyncBranchContextByteString()).isNotNull();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateTriggerRef() {
    assertThat(triggerExecutionHelper.generateTriggerRef(ngTriggerEntity)).isEqualTo("acc/org/proj/trigger");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testIsAutoAbort() {
    GithubPRSpec githubPRSpec = GithubPRSpec.builder().autoAbortPreviousExecutions(true).build();
    NGTriggerConfigV2 ngTriggerConfigV2 =
        NGTriggerConfigV2.builder()
            .source(
                NGTriggerSourceV2.builder()
                    .type(NGTriggerType.WEBHOOK)
                    .spec(
                        WebhookTriggerConfigV2.builder()
                            .type(WebhookTriggerType.GITHUB)
                            .spec(GithubSpec.builder().type(GithubTriggerEvent.PULL_REQUEST).spec(githubPRSpec).build())
                            .build())
                    .build())
            .build();
    assertThat(triggerExecutionHelper.isAutoAbortSelected(ngTriggerConfigV2)).isTrue();

    githubPRSpec.setAutoAbortPreviousExecutions(false);
    assertThat(triggerExecutionHelper.isAutoAbortSelected(ngTriggerConfigV2)).isFalse();

    ngTriggerConfigV2 = NGTriggerConfigV2.builder()
                            .source(NGTriggerSourceV2.builder()
                                        .type(NGTriggerType.WEBHOOK)
                                        .spec(WebhookTriggerConfigV2.builder()
                                                  .type(WebhookTriggerType.CUSTOM)
                                                  .spec(CustomTriggerSpec.builder().build())
                                                  .build())
                                        .build())
                            .build();
    assertThat(triggerExecutionHelper.isAutoAbortSelected(ngTriggerConfigV2)).isFalse();

    ngTriggerConfigV2 = NGTriggerConfigV2.builder()
                            .source(NGTriggerSourceV2.builder()
                                        .type(NGTriggerType.SCHEDULED)
                                        .spec(ScheduledTriggerConfig.builder()
                                                  .type("Cron")
                                                  .spec(CronTriggerSpec.builder().expression("").build())
                                                  .build())
                                        .build())
                            .build();
    assertThat(triggerExecutionHelper.isAutoAbortSelected(ngTriggerConfigV2)).isFalse();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateExecutionTagForEvent() {
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();

    TriggerPayload.Builder payloadBuilder = TriggerPayload.newBuilder().setType(Type.GIT).setParsedPayload(
        ParsedPayload.newBuilder()
            .setPr(PullRequestHook.newBuilder()
                       .setPr(PullRequest.newBuilder().setNumber(1).setSource("source").setTarget("target").build())
                       .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                       .build())
            .build());

    String executionTagForEvent =
        triggerExecutionHelper.generateExecutionTagForEvent(triggerDetails, payloadBuilder.build());
    assertThat(executionTagForEvent).isEqualTo("acc:org:proj:target:PR:https://github.com:1:source:target");

    payloadBuilder = TriggerPayload.newBuilder().setType(Type.GIT).setParsedPayload(
        ParsedPayload.newBuilder()
            .setPush(PushHook.newBuilder()
                         .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                         .setRef("ref")
                         .build())
            .build());
    executionTagForEvent = triggerExecutionHelper.generateExecutionTagForEvent(triggerDetails, payloadBuilder.build());
    assertThat(executionTagForEvent).isEqualTo("acc:org:proj:target:PUSH:https://github.com:ref");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testBranchExpr() {
    assertThat(triggerExecutionHelper.isBranchExpr("<+trigger.branch>")).isEqualTo(true);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testFetchInputSetYAML() throws Exception {
    NGTriggerEntity ngTriggerEntityGitSync = NGTriggerEntity.builder()
                                                 .accountId("ACCOUNT_ID")
                                                 .orgIdentifier("ORG_IDENTIFIER")
                                                 .projectIdentifier("PROJ_IDENTIFIER")
                                                 .targetIdentifier("PIPELINE_IDENTIFIER")
                                                 .identifier("IDENTIFIER")
                                                 .name("NAME")
                                                 .targetType(TargetType.PIPELINE)
                                                 .type(NGTriggerType.WEBHOOK)
                                                 .version(0L)
                                                 .build();

    TriggerDetails triggerDetails = TriggerDetails.builder()
                                        .ngTriggerEntity(ngTriggerEntityGitSync)
                                        .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                                               .inputSetRefs(Arrays.asList("inputSet1", "inputSet2"))
                                                               .pipelineBranchName("pipelineBranchName")
                                                               .build())
                                        .build();

    Call<ResponseDTO<MergeInputSetResponseDTOPMS>> mergeInputSetResponseDTOPMS = Mockito.mock(Call.class);
    when(ngTriggerElementMapper.toTriggerConfigV2(ngTriggerEntityGitSync))
        .thenReturn(triggerDetails.getNgTriggerConfigV2());

    when(pipelineServiceClient.getMergeInputSetFromPipelineTemplate(any(), any(), any(), any(), any(), any()))
        .thenReturn(mergeInputSetResponseDTOPMS);
    when(mergeInputSetResponseDTOPMS.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            MergeInputSetResponseDTOPMS.builder().pipelineYaml("pipelineYaml").isErrorResponse(false).build())));
    assertThat(triggerExecutionHelper.fetchInputSetYAML(triggerDetails, triggerWebhookEvent)).isEqualTo("pipelineYaml");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateTriggeredBy() {
    User user = User.newBuilder().setLogin("login").setEmail("user@email.com").setName("name").build();

    TriggeredBy triggeredBy = triggerExecutionHelper.generateTriggerdBy("tag", ngTriggerEntity,
        TriggerPayload.newBuilder()
            .setParsedPayload(ParsedPayload.newBuilder().setPush(PushHook.newBuilder().setSender(user).build()).build())
            .build(),
        "eventId");

    assertTriggerBy(triggeredBy);

    triggeredBy = triggerExecutionHelper.generateTriggerdBy("tag", ngTriggerEntity,
        TriggerPayload.newBuilder()
            .setParsedPayload(
                ParsedPayload.newBuilder().setPr(PullRequestHook.newBuilder().setSender(user).build()).build())
            .build(),
        "eventId");

    assertTriggerBy(triggeredBy);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetSerializedGitSyncContextWithRepoAndFilePath() {
    String repo = "repo";
    String filePath = "filePath";
    String connectorRef = "connectorRef";
    String branch = "branch";
    PipelineEntity pipelineEntityToExecute =
        PipelineEntity.builder().repo(repo).filePath(filePath).connectorRef(connectorRef).build();
    GitSyncBranchContext gitSyncBranchContext =
        triggerExecutionHelper.getGitSyncContextWithRepoAndFilePath(pipelineEntityToExecute, branch);
    when(pmsGitSyncHelper.createGitSyncBranchContextGuardFromBytes(any(), eq(false)))
        .thenReturn(new PmsGitSyncBranchContextGuard(gitSyncBranchContext, false));
    try (PmsGitSyncBranchContextGuard ignore =
             pmsGitSyncHelper.createGitSyncBranchContextGuardFromBytes(ByteString.copyFrom(new byte[2]), false)) {
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
      assertThat(gitEntityInfo).isNotNull();
      assertThat(gitEntityInfo.getRepoName()).isEqualTo(repo);
      assertThat(gitEntityInfo.getFilePath()).isEqualTo(filePath);
      assertThat(gitEntityInfo.getConnectorRef()).isEqualTo(connectorRef);
      assertThat(gitEntityInfo.getBranch()).isEqualTo(branch);
    }
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testEmptyBranchNameInTrigger() {
    TriggerDetails triggerDetails = TriggerDetails.builder()
                                        .ngTriggerEntity(ngTriggerEntity)
                                        .ngTriggerConfigV2(NGTriggerConfigV2.builder().build())
                                        .build();
    TriggerPayload.Builder payloadBuilder = TriggerPayload.newBuilder().setType(Type.GIT).setParsedPayload(
        ParsedPayload.newBuilder()
            .setPr(PullRequestHook.newBuilder()
                       .setPr(PullRequest.newBuilder().setNumber(1).setSource("source").setTarget("target").build())
                       .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                       .build())
            .build());
    Optional<PipelineEntity> pipelineEntityToExecute =
        Optional.of(PipelineEntity.builder().storeType(StoreType.REMOTE).build());
    doReturn(pipelineEntityToExecute)
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false);
    assertThatThrownBy(()
                           -> triggerExecutionHelper.resolveRuntimeInputAndSubmitExecutionReques(
                               triggerDetails, payloadBuilder.build(), null))
        .isInstanceOf(TriggerException.class)
        .hasMessage(
            "Failed while requesting Pipeline Execution through Trigger: pipelineBranchName is missing or is empty in trigger yaml.");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testResolveRuntimeInputAndSubmitExecutionRequestV1Yaml() {
    TriggerDetails triggerDetails = TriggerDetails.builder()
                                        .ngTriggerEntity(ngTriggerEntity)
                                        .ngTriggerConfigV2(NGTriggerConfigV2.builder().build())
                                        .build();
    TriggerPayload.Builder payloadBuilder = TriggerPayload.newBuilder().setType(Type.GIT).setParsedPayload(
        ParsedPayload.newBuilder()
            .setPr(PullRequestHook.newBuilder()
                       .setPr(PullRequest.newBuilder().setNumber(1).setSource("source").setTarget("target").build())
                       .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                       .build())
            .build());
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled("acc", FeatureName.CDS_NG_TRIGGER_EXECUTION_REFACTOR);
    doReturn(Optional.of(pipelineEntityV1))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false);
    doCallRealMethod()
        .when(pipelineGovernanceService)
        .fetchExpandedPipelineJSONFromYaml(pipelineEntityV1.getAccountId(), pipelineEntityV1.getOrgIdentifier(),
            pipelineEntityV1.getProjectIdentifier(), pipelineEntityV1.getYaml(),
            OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled("acc", FeatureName.CDS_NG_TRIGGER_SELECTIVE_STAGE_EXECUTION);
    triggerExecutionHelper.resolveRuntimeInputAndSubmitExecutionRequest(
        triggerDetails, payloadBuilder.build(), triggerWebhookEvent, null, null);
  }

  private void assertTriggerBy(TriggeredBy triggeredBy) {
    Map<String, String> extraInfoMap = triggeredBy.getExtraInfoMap();
    assertThat(extraInfoMap.containsKey(EXEC_TAG_SET_BY_TRIGGER)).isTrue();
    assertThat(extraInfoMap.containsKey(TRIGGER_REF)).isTrue();
    assertThat(extraInfoMap.containsKey(EVENT_CORRELATION_ID)).isTrue();

    assertThat(extraInfoMap.get(EXEC_TAG_SET_BY_TRIGGER)).isEqualTo("tag");
    assertThat(extraInfoMap.get(TRIGGER_REF)).isEqualTo("acc/org/proj/trigger");
    assertThat(extraInfoMap.get(GIT_USER)).isEqualTo("login");
    assertThat(extraInfoMap.get(EVENT_CORRELATION_ID)).isEqualTo("eventId");
  }
}
