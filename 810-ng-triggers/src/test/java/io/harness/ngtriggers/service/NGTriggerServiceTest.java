/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.MATT;
import static io.harness.rule.OwnerRule.SRIDHAR;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.scheduled.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.scheduled.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.impl.NGTriggerServiceImpl;
import io.harness.outbox.api.OutboxService;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.PmsFeatureFlagService;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.rule.Owner;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.io.Resources;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
public class NGTriggerServiceTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock NGTriggerElementMapper ngTriggerElementMapper;
  @Mock PipelineServiceClient pipelineServiceClient;
  @InjectMocks NGTriggerServiceImpl ngTriggerServiceImpl;
  @Mock BuildTriggerHelper validationHelper;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock NGTriggerRepository ngTriggerRepository;

  @Mock OutboxService outboxService;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String IDENTIFIER = "first_trigger";
  private final String NAME = "first trigger";
  private final String PIPELINE_IDENTIFIER = "myPipeline";
  ClassLoader classLoader = getClass().getClassLoader();

  String filenameGitSync = "ng-trigger-github-pr-gitsync.yaml";
  WebhookTriggerConfigV2 webhookTriggerConfig;

  String ngTriggerYamlWithGitSync;
  NGTriggerConfigV2 ngTriggerConfig;

  WebhookMetadata metadata;

  NGTriggerMetadata ngTriggerMetadata;

  NGTriggerEntity ngTriggerEntityGitSync = NGTriggerEntity.builder()
                                               .accountId(ACCOUNT_ID)
                                               .orgIdentifier(ORG_IDENTIFIER)
                                               .projectIdentifier(PROJ_IDENTIFIER)
                                               .targetIdentifier(PIPELINE_IDENTIFIER)
                                               .identifier(IDENTIFIER)
                                               .name(NAME)
                                               .targetType(TargetType.PIPELINE)
                                               .type(NGTriggerType.WEBHOOK)
                                               .metadata(ngTriggerMetadata)
                                               .yaml(ngTriggerYamlWithGitSync)
                                               .version(0L)
                                               .build();
  @Before
  public void setup() throws Exception {
    on(ngTriggerServiceImpl).set("ngTriggerElementMapper", ngTriggerElementMapper);
    when(validationHelper.fetchPipelineForTrigger(any())).thenReturn(Optional.empty());
    ngTriggerYamlWithGitSync =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filenameGitSync)), StandardCharsets.UTF_8);
    ngTriggerConfig = YamlPipelineUtils.read(ngTriggerYamlWithGitSync, NGTriggerConfigV2.class);

    webhookTriggerConfig = (WebhookTriggerConfigV2) ngTriggerConfig.getSource().getSpec();
    metadata = WebhookMetadata.builder().type(webhookTriggerConfig.getType().getValue()).build();

    ngTriggerMetadata = NGTriggerMetadata.builder().webhook(metadata).build();
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testCronTriggerFailure() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(NGTriggerEntity.builder().identifier("id").name("name").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .type(NGTriggerType.SCHEDULED)
                                .spec(ScheduledTriggerConfig.builder()
                                          .type("Cron")
                                          .spec(CronTriggerSpec.builder().expression("not a cron").build())
                                          .build())
                                .build())
                    .build())
            .build();
    try {
      ngTriggerServiceImpl.validateTriggerConfig(triggerDetails);
      fail("bad cron not caught");
    } catch (Exception e) {
      assertThat(e instanceof IllegalArgumentException).isTrue();
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testEmptyIdentifierTriggerFailure() {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder().identifier("").name("name").build();
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();

    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .hasMessage("Identifier can not be empty");

    ngTriggerEntity.setIdentifier(null);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .hasMessage("Identifier can not be empty");

    ngTriggerEntity.setIdentifier("  ");
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .hasMessage("Identifier can not be empty");

    ngTriggerEntity.setIdentifier("a1");
    ngTriggerEntity.setName("");
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .hasMessage("Name can not be empty");
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testCronTrigger() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(NGTriggerEntity.builder().identifier("id").name("name").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .type(NGTriggerType.SCHEDULED)
                                .spec(ScheduledTriggerConfig.builder()
                                          .type("Cron")
                                          .spec(CronTriggerSpec.builder().expression("20 4 * * *").build())
                                          .build())
                                .build())
                    .build())
            .build();

    ngTriggerServiceImpl.validateTriggerConfig(triggerDetails);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIsBranchExpr() {
    assertThat(ngTriggerServiceImpl.isBranchExpr("<+trigger.branch>")).isEqualTo(true);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testInputSetValidation() throws Exception {
    TriggerDetails triggerDetails = TriggerDetails.builder()
                                        .ngTriggerEntity(ngTriggerEntityGitSync)
                                        .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                                               .inputSetRefs(Arrays.asList("inputSet1", "inputSet2"))
                                                               .pipelineBranchName("pipelineBranchName")
                                                               .build())
                                        .build();
    Call<ResponseDTO<MergeInputSetResponseDTOPMS>> mergeInputSetResponseDTOPMS = mock(Call.class);
    when(ngTriggerElementMapper.toTriggerConfigV2(ngTriggerEntityGitSync))
        .thenReturn(triggerDetails.getNgTriggerConfigV2());

    when(pipelineServiceClient.getMergeInputSetFromPipelineTemplate(any(), any(), any(), any(), any(), any()))
        .thenReturn(mergeInputSetResponseDTOPMS);
    when(mergeInputSetResponseDTOPMS.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(MergeInputSetResponseDTOPMS.builder().isErrorResponse(false).build())));
    assertThat(ngTriggerServiceImpl.validateInputSetsInternal(triggerDetails))
        .isEqualTo(MergeInputSetResponseDTOPMS.builder().isErrorResponse(false).build());
  }

  @Test
  @Owner(developers =  SRIDHAR)
  @Category(UnitTests.class)

  public void testDelete() {
    NGTriggerEntity ngTrigger = NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .build();

    UpdateResult deleteResult = mock(UpdateResult.class);

    Optional<NGTriggerEntity> optionalNGTrigger = Optional.of(ngTrigger);

    when(pmsFeatureFlagService.isEnabled(eq(ACCOUNT_ID), eq(FeatureName.HARD_DELETE_ENTITIES))).thenReturn(Boolean.FALSE);
    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot
            (eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER), eq(Boolean.TRUE)))
            .thenReturn(optionalNGTrigger);
    when(ngTriggerRepository.delete(any(Criteria.class))).thenReturn(deleteResult);
    when(deleteResult.wasAcknowledged()).thenReturn(Boolean.TRUE);
    when(deleteResult.getModifiedCount()).thenReturn(1L);

    Boolean res = ngTriggerServiceImpl.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null);
    assertTrue(res);

  }

  @Test
  @Owner(developers =  SRIDHAR)
  @Category(UnitTests.class)
  public void testHardDelete() {
    NGTriggerEntity ngTrigger = NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .build();

    Optional<NGTriggerEntity> optionalNGTrigger = Optional.of(ngTrigger);

    when(pmsFeatureFlagService.isEnabled(eq(ACCOUNT_ID), eq(FeatureName.HARD_DELETE_ENTITIES))).thenReturn(Boolean.TRUE);
    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot
            (eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER), eq(Boolean.TRUE)))
            .thenReturn(optionalNGTrigger);
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.acknowledged(1));

    Boolean res = ngTriggerServiceImpl.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null);
    assertTrue(res);
  }

  @Test (expected = InvalidRequestException.class)
  @Owner(developers =  SRIDHAR)
  @Category(UnitTests.class)
  public void testDeleteException() {
    NGTriggerEntity ngTrigger = NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .build();

    Optional<NGTriggerEntity> optionalNGTrigger = Optional.of(ngTrigger);

    when(pmsFeatureFlagService.isEnabled(eq(ACCOUNT_ID), eq(FeatureName.HARD_DELETE_ENTITIES))).thenReturn(Boolean.TRUE);
    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot
            (eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER), eq(Boolean.TRUE)))
            .thenReturn(optionalNGTrigger);
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.unacknowledged());

    ngTriggerServiceImpl.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null);
  }
}
