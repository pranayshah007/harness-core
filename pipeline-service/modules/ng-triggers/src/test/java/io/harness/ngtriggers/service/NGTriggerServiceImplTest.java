/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.CDS_ENABLE_TRIGGER_YAML_VALIDATION;
import static io.harness.exception.WingsException.USER;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITHUB;
import static io.harness.ngtriggers.beans.source.YamlFields.PIPELINE_BRANCH_NAME;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.MATT;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.category.element.UnitTests;
import io.harness.common.NGExpressionUtils;
import io.harness.common.NGTimeConversionHelper;
import io.harness.dto.PollingResponseDTO;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.PollingConfig;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerYamlDiffDTO;
import io.harness.ngtriggers.beans.dto.WebhookEventProcessingDetails;
import io.harness.ngtriggers.beans.dto.WebhookEventProcessingDetails.WebhookEventProcessingDetailsBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.BuildMetadata;
import io.harness.ngtriggers.beans.entity.metadata.CustomMetadata;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatus;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatusData;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogType;
import io.harness.ngtriggers.beans.entity.metadata.status.StatusResult;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.ValidationStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookAutoRegistrationStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookInfo;
import io.harness.ngtriggers.beans.response.TargetExecutionSummary;
import io.harness.ngtriggers.beans.source.GitMoveOperationType;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.TriggerUpdateCount;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpecWrapper;
import io.harness.ngtriggers.beans.source.artifact.DockerRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.GcrSpec;
import io.harness.ngtriggers.beans.source.artifact.ManifestTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.MultiRegionArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.scheduled.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.scheduled.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.github.GithubSpec;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.helpers.TriggerCatalogHelper;
import io.harness.ngtriggers.helpers.TriggerSetupUsageHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.impl.NGTriggerServiceImpl;
import io.harness.ngtriggers.utils.PollingSubscriptionHelper;
import io.harness.ngtriggers.utils.TriggerReferenceHelper;
import io.harness.ngtriggers.validations.TriggerValidationHandler;
import io.harness.ngtriggers.validations.ValidationResult;
import io.harness.outbox.api.OutboxService;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.polling.client.PollingResourceClient;
import io.harness.polling.contracts.GitPollingPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.service.PollingDocument;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.repositories.spring.TriggerWebhookEventRepository;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.validator.InvalidYamlException;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.io.Resources;
import com.mongodb.client.result.DeleteResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
public class NGTriggerServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock AccessControlClient accessControlClient;
  @Mock NGSettingsClient settingsClient;
  @Mock NGTriggerElementMapper ngTriggerElementMapper;
  @Mock PipelineServiceClient pipelineServiceClient;
  @InjectMocks NGTriggerServiceImpl ngTriggerServiceImpl;
  @Mock BuildTriggerHelper validationHelper;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock NGTriggerRepository ngTriggerRepository;
  @Mock TriggerReferenceHelper triggerReferenceHelper;
  @Mock TriggerSetupUsageHelper triggerSetupUsageHelper;
  @Mock TriggerWebhookEventRepository webhookEventQueueRepository;
  @Mock OutboxService outboxService;
  @Mock ExecutorService executorService;
  @Mock PollingSubscriptionHelper pollingSubscriptionHelper;

  @Mock KryoSerializer kryoSerializer;
  @Mock NGTriggerYamlSchemaService ngTriggerYamlSchemaService;
  @Mock PollingResourceClient pollingResourceClient;
  @Mock NGTriggerWebhookRegistrationService ngTriggerWebhookRegistrationService;
  @Mock TriggerValidationHandler triggerValidationHandler;
  @Mock TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Mock TriggerCatalogHelper triggerCatalogHelper;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String IDENTIFIER = "first_trigger";
  private final String NAME = "first trigger";
  private final String PIPELINE_IDENTIFIER = "myPipeline";
  private final String WEBHOOK_ID = "webhook_id";

  private final String CONNECTOR_REF = "connector_ref";

  private final String POLLING_DOC_ID = "polling_doc_id";

  private final String X_API_KEY = "x-api-key";
  private final String API_KEY = "pat.kmpySmUISimoRrJL6NL73w.6350538bbfd93f472a549604.iCMeDe82VbCG6YnWw80h";
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
  String pipelineYamlV1;

  @Before
  public void setup() throws Exception {
    on(ngTriggerServiceImpl).set("ngTriggerElementMapper", ngTriggerElementMapper);

    when(validationHelper.fetchPipelineYamlForTrigger(any())).thenReturn(Optional.empty());
    ngTriggerYamlWithGitSync =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filenameGitSync)), StandardCharsets.UTF_8);
    ngTriggerConfig = YamlPipelineUtils.read(ngTriggerYamlWithGitSync, NGTriggerConfigV2.class);

    webhookTriggerConfig = (WebhookTriggerConfigV2) ngTriggerConfig.getSource().getSpec();
    metadata = WebhookMetadata.builder().type(webhookTriggerConfig.getType().getValue()).build();

    ngTriggerMetadata = NGTriggerMetadata.builder().webhook(metadata).build();

    pipelineYamlV1 =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("pipeline-v1.yaml")), StandardCharsets.UTF_8);
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
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCronTriggerWithValidQuartz() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(NGTriggerEntity.builder().identifier("id").name("name").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(
                        NGTriggerSourceV2.builder()
                            .type(NGTriggerType.SCHEDULED)
                            .spec(
                                ScheduledTriggerConfig.builder()
                                    .type("Cron")
                                    .spec(
                                        CronTriggerSpec.builder().type("QUARTZ").expression("0 0 3 ? * 3#2 *").build())
                                    .build())
                            .build())
                    .build())
            .build();

    ngTriggerServiceImpl.validateTriggerConfig(triggerDetails);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCronTriggerWithInvalidQuartz() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(NGTriggerEntity.builder().identifier("id").name("name").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(
                        NGTriggerSourceV2.builder()
                            .type(NGTriggerType.SCHEDULED)
                            .spec(ScheduledTriggerConfig.builder()
                                      .type("Cron")
                                      .spec(CronTriggerSpec.builder().type("QUARTZ").expression("0 3 ? * 3#2").build())
                                      .build())
                            .build())
                    .build())
            .build();
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cron expression contains 5 parts but we expect one of [6, 7]");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCronTriggerWithFiringTimeDifferenceLessThan5Minutes() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(NGTriggerEntity.builder().identifier("id").name("name").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(
                        NGTriggerSourceV2.builder()
                            .type(NGTriggerType.SCHEDULED)
                            .spec(ScheduledTriggerConfig.builder()
                                      .type("Cron")
                                      .spec(CronTriggerSpec.builder().type("UNIX").expression("0/4 0 * * *").build())
                                      .build())
                            .build())
                    .build())
            .build();
    CronParser cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
    Cron cron = cronParser.parse("0/4 0 * * *");
    ExecutionTime executionTime = ExecutionTime.forCron(cron);
    Optional<ZonedDateTime> firstExecutionTimeOptional = executionTime.nextExecution(ZonedDateTime.now());
    if (firstExecutionTimeOptional.isPresent()) {
      ZonedDateTime firstExecutionTime = firstExecutionTimeOptional.get();
      Optional<ZonedDateTime> secondExecutionTimeOptional = executionTime.nextExecution(firstExecutionTime);
      if (secondExecutionTimeOptional.isPresent()) {
        ZonedDateTime secondExecutionTime = secondExecutionTimeOptional.get();
        assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
            .isInstanceOf(InvalidArgumentsException.class)
            .hasMessage(
                "Cron interval must be greater than or equal to 5 minutes. The next two execution times when this trigger is suppose to fire are "
                + firstExecutionTime.toLocalTime().toString() + " and " + secondExecutionTime.toLocalTime().toString()
                + " which do not have a difference of 5 minutes between them.");
      }
    }
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCronTriggerWithInvalidUnix() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(NGTriggerEntity.builder().identifier("id").name("name").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(
                        NGTriggerSourceV2.builder()
                            .type(NGTriggerType.SCHEDULED)
                            .spec(ScheduledTriggerConfig.builder()
                                      .type("Cron")
                                      .spec(CronTriggerSpec.builder().type("UNIX").expression("0 3 * * 3 *").build())
                                      .build())
                            .build())
                    .build())
            .build();
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cron expression contains 6 parts but we expect one of [5]");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testValidateTriggerConfig() {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .accountId(ACCOUNT_ID)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJ_IDENTIFIER)
                                          .targetIdentifier(PIPELINE_IDENTIFIER)
                                          .identifier(IDENTIFIER)
                                          .name(NAME)
                                          .targetType(TargetType.PIPELINE)
                                          .type(NGTriggerType.WEBHOOK)
                                          .metadata(ngTriggerMetadata)
                                          .yaml("yaml")
                                          .pollInterval("1m")
                                          .version(0L)
                                          .build();
    TriggerDetails triggerDetails = TriggerDetails.builder()
                                        .ngTriggerEntity(ngTriggerEntity)
                                        .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                                               .source(NGTriggerSourceV2.builder()
                                                                           .type(NGTriggerType.WEBHOOK)
                                                                           .spec(WebhookTriggerConfigV2.builder()
                                                                                     .type(GITHUB)
                                                                                     .spec(GithubSpec.builder().build())
                                                                                     .build())
                                                                           .build())
                                                               .inputSetRefs(Collections.emptyList())
                                                               .pipelineBranchName("pipelineBranchName")
                                                               .build())
                                        .build();
    when(pmsFeatureFlagService.isEnabled(eq(ACCOUNT_ID), eq(FeatureName.CD_GIT_WEBHOOK_POLLING)))
        .thenReturn(Boolean.TRUE);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Poll Interval should be between 2 and 60 minutes");

    // polling interval is null
    ngTriggerEntity.setPollInterval(null);
    triggerDetails.setNgTriggerEntity(ngTriggerEntity);
    assertThatCode(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails)).doesNotThrowAnyException();

    NGTriggerConfigV2 artifactTriggerConfig = NGTriggerConfigV2.builder()
                                                  .source(NGTriggerSourceV2.builder()
                                                              .type(NGTriggerType.ARTIFACT)
                                                              .spec(ArtifactTriggerConfig.builder().build())
                                                              .build())
                                                  .build();
    triggerDetails.setNgTriggerConfigV2(artifactTriggerConfig);
    ngTriggerEntity.setType(NGTriggerType.ARTIFACT);
    triggerDetails.setNgTriggerEntity(ngTriggerEntity);

    // trigger type artifact
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("stageIdentifier can not be blank/missing. artifactRef can not be blank/missing. ");

    ngTriggerEntity.setWithServiceV2(true);
    assertThatCode(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails)).doesNotThrowAnyException();

    // trigger type manifest
    ngTriggerEntity.setWithServiceV2(false);
    NGTriggerConfigV2 manifestTriggerConfig = NGTriggerConfigV2.builder()
                                                  .source(NGTriggerSourceV2.builder()
                                                              .type(NGTriggerType.MANIFEST)
                                                              .spec(ManifestTriggerConfig.builder().build())
                                                              .build())
                                                  .build();
    triggerDetails.setNgTriggerConfigV2(manifestTriggerConfig);
    ngTriggerEntity.setType(NGTriggerType.MANIFEST);
    triggerDetails.setNgTriggerEntity(ngTriggerEntity);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("stageIdentifier can not be blank/missing. manifestRef can not be blank/missing. ");

    ngTriggerEntity.setWithServiceV2(true);
    assertThatCode(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails)).doesNotThrowAnyException();

    // trigger type MULTI_REGION_ARTIFACT
    ngTriggerEntity.setWithServiceV2(false);
    NGTriggerConfigV2 multiRegionArtifactTriggerConfig =
        NGTriggerConfigV2.builder()
            .source(NGTriggerSourceV2.builder()
                        .type(NGTriggerType.MULTI_REGION_ARTIFACT)
                        .spec(MultiRegionArtifactTriggerConfig.builder().build())
                        .build())
            .build();
    triggerDetails.setNgTriggerConfigV2(multiRegionArtifactTriggerConfig);
    ngTriggerEntity.setType(NGTriggerType.MULTI_REGION_ARTIFACT);
    triggerDetails.setNgTriggerEntity(ngTriggerEntity);
    when(pmsFeatureFlagService.isEnabled(eq(ACCOUNT_ID), eq(FeatureName.CDS_NG_TRIGGER_MULTI_ARTIFACTS)))
        .thenReturn(Boolean.FALSE);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Feature Flag CDS_NG_TRIGGER_MULTI_ARTIFACTS must be enabled for creation of multi-region artifact triggers.");

    when(pmsFeatureFlagService.isEnabled(eq(ACCOUNT_ID), eq(FeatureName.CDS_NG_TRIGGER_MULTI_ARTIFACTS)))
        .thenReturn(Boolean.TRUE);

    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Multi-Artifact triggers are only supported with Service V2.\n"
            + "Multi-region Artifact trigger source type must have a valid artifact source type value.\n"
            + "Multi-region Artifact trigger sources list must have at least one element.\n");

    List<ArtifactTypeSpecWrapper> artifactTypeSpecWrapperList = new ArrayList<>();
    ArtifactTypeSpecWrapper artifactTypeSpecWrapper = new ArtifactTypeSpecWrapper();
    artifactTypeSpecWrapper.setSpec(DockerRegistrySpec.builder().build());
    artifactTypeSpecWrapperList.add(artifactTypeSpecWrapper);
    ArtifactTypeSpecWrapper artifactTypeSpecWrapper2 = new ArtifactTypeSpecWrapper();
    artifactTypeSpecWrapper2.setSpec(GcrSpec.builder().build());
    artifactTypeSpecWrapperList.add(artifactTypeSpecWrapper2);
    multiRegionArtifactTriggerConfig.setSource(NGTriggerSourceV2.builder()
                                                   .type(NGTriggerType.MULTI_REGION_ARTIFACT)
                                                   .spec(MultiRegionArtifactTriggerConfig.builder()
                                                             .sources(artifactTypeSpecWrapperList)
                                                             .type(ArtifactType.GCR)
                                                             .build())
                                                   .build());
    triggerDetails.setNgTriggerConfigV2(multiRegionArtifactTriggerConfig);

    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Multi-Artifact triggers are only supported with Service V2.\n"
            + "Multi-region Artifact sources must all be of type DockerRegistry.\n");

    artifactTypeSpecWrapperList = new ArrayList<>();
    artifactTypeSpecWrapper = new ArtifactTypeSpecWrapper();
    artifactTypeSpecWrapper.setSpec(DockerRegistrySpec.builder().build());
    artifactTypeSpecWrapperList.add(artifactTypeSpecWrapper);
    multiRegionArtifactTriggerConfig.setSource(NGTriggerSourceV2.builder()
                                                   .type(NGTriggerType.MULTI_REGION_ARTIFACT)
                                                   .spec(MultiRegionArtifactTriggerConfig.builder()
                                                             .sources(artifactTypeSpecWrapperList)
                                                             .type(ArtifactType.GCR)
                                                             .build())
                                                   .build());
    triggerDetails.setNgTriggerConfigV2(multiRegionArtifactTriggerConfig);
    ngTriggerEntity.setWithServiceV2(true);
    assertThatCode(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFetchTriggerEventHistory() {
    when(triggerEventHistoryRepository.findByAccountIdAndEventCorrelationId(ACCOUNT_ID, "eventId"))
        .thenReturn(Collections.emptyList());
    assertThatThrownBy(() -> ngTriggerServiceImpl.fetchTriggerEventHistory(ACCOUNT_ID, "eventId"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Trigger event history eventId does not exist");
    List<TriggerEventHistory> triggerEventHistories = new ArrayList<>();
    TriggerEventHistory triggerEventHistory1 = TriggerEventHistory.builder()
                                                   .accountId(ACCOUNT_ID)
                                                   .orgIdentifier(ORG_IDENTIFIER)
                                                   .projectIdentifier(PROJ_IDENTIFIER)
                                                   .triggerIdentifier(IDENTIFIER)
                                                   .targetIdentifier(PIPELINE_IDENTIFIER)
                                                   .targetExecutionSummary(TargetExecutionSummary.builder()
                                                                               .planExecutionId("planExecutionId2")
                                                                               .runtimeInput("runtimeInput2")
                                                                               .build())
                                                   .build();
    TriggerEventHistory triggerEventHistory2 = TriggerEventHistory.builder()
                                                   .accountId(ACCOUNT_ID)
                                                   .orgIdentifier(ORG_IDENTIFIER)
                                                   .projectIdentifier(PROJ_IDENTIFIER)
                                                   .triggerIdentifier(IDENTIFIER)
                                                   .targetIdentifier(PIPELINE_IDENTIFIER)
                                                   .targetExecutionSummary(TargetExecutionSummary.builder()
                                                                               .planExecutionId("planExecutionId")
                                                                               .runtimeInput("runtimeInput")
                                                                               .build())
                                                   .build();
    triggerEventHistories.add(triggerEventHistory2);
    triggerEventHistories.add(triggerEventHistory1);
    WebhookEventProcessingDetailsBuilder builder =
        WebhookEventProcessingDetails.builder()
            .eventId("eventId")
            .accountIdentifier(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .triggerIdentifier(IDENTIFIER)
            .pipelineIdentifier(PIPELINE_IDENTIFIER)
            .pipelineExecutionId("planExecutionId")
            .runtimeInput("runtimeInput")
            .eventFound(true)
            .warningMsg(
                "There are multiple trigger events generated from this eventId. This response contains only one of them.");
    when(triggerEventHistoryRepository.findByAccountIdAndEventCorrelationId(ACCOUNT_ID, "eventId"))
        .thenReturn(triggerEventHistories);
    assertThat(ngTriggerServiceImpl.fetchTriggerEventHistory(ACCOUNT_ID, "eventId")).isEqualTo(builder.build());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testListEnabledTriggersForCurrentProject() {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder().build();
    when(ngTriggerRepository.findByAccountIdAndEnabledAndDeletedNot(ACCOUNT_ID, true, true))
        .thenReturn(Optional.empty());
    assertThat(ngTriggerServiceImpl.listEnabledTriggersForCurrentProject(ACCOUNT_ID, null, null))
        .isEqualTo(Collections.emptyList());

    when(ngTriggerRepository.findByAccountIdAndEnabledAndDeletedNot(ACCOUNT_ID, true, true))
        .thenReturn(Optional.of(Collections.singletonList(ngTriggerEntity)));
    assertThat(ngTriggerServiceImpl.listEnabledTriggersForCurrentProject(ACCOUNT_ID, null, null))
        .isEqualTo(Collections.singletonList(ngTriggerEntity));

    NGTriggerEntity ngTriggerEntity2 = NGTriggerEntity.builder().orgIdentifier(ORG_IDENTIFIER).build();
    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndEnabledAndDeletedNot(
             ACCOUNT_ID, ORG_IDENTIFIER, true, true))
        .thenReturn(Optional.of(Collections.singletonList(ngTriggerEntity2)));
    assertThat(ngTriggerServiceImpl.listEnabledTriggersForCurrentProject(ACCOUNT_ID, ORG_IDENTIFIER, null))
        .isEqualTo(Collections.singletonList(ngTriggerEntity2));

    NGTriggerEntity ngTriggerEntity3 =
        NGTriggerEntity.builder().orgIdentifier(ORG_IDENTIFIER).projectIdentifier(PROJ_IDENTIFIER).build();
    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnabledAndDeletedNot(
             ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, true, true))
        .thenReturn(Optional.of(Collections.singletonList(ngTriggerEntity2)));
    assertThat(ngTriggerServiceImpl.listEnabledTriggersForCurrentProject(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .isEqualTo(Collections.singletonList(ngTriggerEntity2));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testList() {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .type(NGTriggerType.WEBHOOK)
            .build();
    when(ngTriggerRepository.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(ngTrigger)));
    Page<NGTriggerEntity> ngTriggerEntityPage = ngTriggerServiceImpl.list(any(), any());
    assertThat(ngTriggerEntityPage.getContent().get(0)).isEqualTo(ngTrigger);
    assertThat(ngTriggerEntityPage.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFindTriggersForCustomWebhook() {
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
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .triggerIdentifier(IDENTIFIER)
                                                  .accountId(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                  .payload("payload")
                                                  .build();
    when(ngTriggerRepository.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(ngTrigger)));
    assertThat(ngTriggerServiceImpl.findTriggersForCustomWehbook(triggerWebhookEvent, false, true))
        .isEqualTo(Collections.singletonList(ngTrigger));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFindTriggersForCustomWebhookViaCustomWebhookToken() {
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
    when(ngTriggerRepository.findByCustomWebhookToken(any())).thenReturn(Optional.ofNullable(ngTrigger));
    assertThat(ngTriggerServiceImpl.findTriggersForCustomWebhookViaCustomWebhookToken("webhook"))
        .isEqualTo(Optional.ofNullable(ngTrigger));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFindTriggersForWehbookBySourceRepoType() {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder().type(String.valueOf(GITHUB)).build())
                          .build())
            .build();
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .triggerIdentifier(IDENTIFIER)
                                                  .accountId(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                  .payload("payload")
                                                  .sourceRepoType("Github")
                                                  .build();
    when(ngTriggerRepository.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(ngTrigger)));
    assertThat(ngTriggerServiceImpl.findTriggersForWehbookBySourceRepoType(triggerWebhookEvent, false, true))
        .isEqualTo(Collections.singletonList(ngTrigger));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFindBuildTriggersByAccountIdAndSignature() {
    NGTriggerEntity ngTrigger = NGTriggerEntity.builder()
                                    .accountId(ACCOUNT_ID)
                                    .enabled(Boolean.TRUE)
                                    .deleted(Boolean.FALSE)
                                    .identifier(IDENTIFIER)
                                    .projectIdentifier(PROJ_IDENTIFIER)
                                    .targetIdentifier(PIPELINE_IDENTIFIER)
                                    .orgIdentifier(ORG_IDENTIFIER)
                                    .type(NGTriggerType.ARTIFACT)
                                    .build();

    when(ngTriggerRepository.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(ngTrigger)));
    assertThat(ngTriggerServiceImpl.findBuildTriggersByAccountIdAndSignature(
                   ACCOUNT_ID, Collections.singletonList("signature")))
        .isEqualTo(Collections.singletonList(ngTrigger));
  }
  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testDeleteTriggerWebhookEvent() {
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder().build();
    doNothing().when(webhookEventQueueRepository).delete(triggerWebhookEvent);
    ngTriggerServiceImpl.deleteTriggerWebhookEvent(triggerWebhookEvent);
    verify(webhookEventQueueRepository, times(1)).delete(triggerWebhookEvent);
  }
  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testUpdateTriggerWebhookEvent() {
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .triggerIdentifier(IDENTIFIER)
                                                  .accountId(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                  .payload("payload")
                                                  .sourceRepoType("Github")
                                                  .build();
    when(webhookEventQueueRepository.update(any(), any())).thenReturn(triggerWebhookEvent);
    assertThat(ngTriggerServiceImpl.updateTriggerWebhookEvent(triggerWebhookEvent)).isEqualTo(triggerWebhookEvent);

    // Exception
    when(webhookEventQueueRepository.update(any(), any())).thenReturn(null);
    assertThatThrownBy(() -> ngTriggerServiceImpl.updateTriggerWebhookEvent(triggerWebhookEvent))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testAddEventToQueue() {
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .triggerIdentifier(IDENTIFIER)
                                                  .accountId(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                  .payload("payload")
                                                  .sourceRepoType("Github")
                                                  .build();
    when(webhookEventQueueRepository.save(any())).thenReturn(triggerWebhookEvent);
    assertThat(ngTriggerServiceImpl.addEventToQueue(triggerWebhookEvent)).isEqualTo(triggerWebhookEvent);

    // exception
    when(webhookEventQueueRepository.save(any())).thenThrow(new InvalidRequestException("message"));
    assertThatThrownBy(() -> ngTriggerServiceImpl.addEventToQueue(triggerWebhookEvent))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Webhook event could not be received");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIsBranchExpr() {
    assertThat(ngTriggerServiceImpl.isBranchExpr("<+trigger.branch>")).isEqualTo(true);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testHardDelete() {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .type(NGTriggerType.WEBHOOK)
            .build();

    Optional<NGTriggerEntity> optionalNGTrigger = Optional.of(ngTrigger);

    when(ngTriggerRepository
             .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot(
                 eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER),
                 eq(Boolean.TRUE)))
        .thenReturn(optionalNGTrigger);
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.acknowledged(1));

    Boolean res =
        ngTriggerServiceImpl.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null);
    assertTrue(res);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testDeleteException() {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .build();

    Optional<NGTriggerEntity> optionalNGTrigger = Optional.of(ngTrigger);

    when(ngTriggerRepository
             .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot(
                 eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER),
                 eq(Boolean.TRUE)))
        .thenReturn(optionalNGTrigger);
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.unacknowledged());

    ngTriggerServiceImpl.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null);
  }
  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testHardDeleteWebhookPolling() throws IOException {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .pollInterval("2m")
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .triggerStatus(TriggerStatus.builder()
                               .webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                                  .registrationResult(WebhookRegistrationStatus.SUCCESS)
                                                                  .build())
                               .webhookInfo(WebhookInfo.builder().webhookId(WEBHOOK_ID).build())
                               .build())
            .build();

    PollingItem pollingItem = createPollingItem(ngTrigger);
    Call<Boolean> call = mock(Call.class);
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    Optional<NGTriggerEntity> optionalNGTrigger = Optional.of(ngTrigger);
    byte[] bytes = {70};

    when(pmsFeatureFlagService.isEnabled(eq(ACCOUNT_ID), eq(FeatureName.CD_GIT_WEBHOOK_POLLING)))
        .thenReturn(Boolean.TRUE);
    when(ngTriggerRepository
             .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot(
                 eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER),
                 eq(Boolean.TRUE)))
        .thenReturn(optionalNGTrigger);
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.acknowledged(1));
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(pollingSubscriptionHelper.generatePollingItem(eq(ngTrigger))).thenReturn(pollingItem);
    when(pollingResourceClient.unsubscribe(any())).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(Boolean.TRUE));
    when(kryoSerializer.asBytes(any(PollingItem.class))).thenReturn(bytes);

    Boolean res =
        ngTriggerServiceImpl.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null);
    assertTrue(res);
  }

  private PollingItem createPollingItem(NGTriggerEntity ngTrigger) {
    return PollingItem.newBuilder()
        .setPollingDocId(POLLING_DOC_ID)
        .setPollingPayloadData(
            PollingPayloadData.newBuilder()
                .setConnectorRef(CONNECTOR_REF)
                .setGitPollPayload(GitPollingPayload.newBuilder()
                                       .setWebhookId(ngTrigger.getTriggerStatus().getWebhookInfo().getWebhookId())
                                       .setPollInterval(NGTimeConversionHelper.convertTimeStringToMinutesZeroAllowed(
                                           ngTrigger.getPollInterval()))
                                       .buildPartial())
                .build())
        .build();
  }

  private static Answer executeRunnable(ArgumentCaptor<Runnable> runnableCaptor) {
    return invocation -> {
      runnableCaptor.getValue().run();
      return null;
    };
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testDeleteSetupUsage() {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .triggerStatus(TriggerStatus.builder()
                               .webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                                  .registrationResult(WebhookRegistrationStatus.SUCCESS)
                                                                  .build())
                               .webhookInfo(WebhookInfo.builder().webhookId(WEBHOOK_ID).build())
                               .build())
            .build();
    when(ngTriggerRepository
             .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot(
                 ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, true))
        .thenReturn(Optional.ofNullable(ngTrigger));
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.acknowledged(1));
    ngTriggerServiceImpl.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, 2L);
    verify(triggerSetupUsageHelper, times(1)).deleteExistingSetupUsages(ngTrigger);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerCatalog() {
    when(triggerCatalogHelper.getTriggerTypeToCategoryMapping(ACCOUNT_ID))
        .thenReturn(
            Arrays.asList(TriggerCatalogItem.builder()
                              .category(NGTriggerType.ARTIFACT)
                              .triggerCatalogType(new ArrayList<>(Collections.singleton(TriggerCatalogType.ACR)))
                              .build(),
                TriggerCatalogItem.builder()
                    .category(NGTriggerType.WEBHOOK)
                    .triggerCatalogType(new ArrayList<>(Collections.singleton(TriggerCatalogType.GITHUB)))
                    .build(),
                TriggerCatalogItem.builder()
                    .category(NGTriggerType.SCHEDULED)
                    .triggerCatalogType(new ArrayList<>(Collections.singleton(TriggerCatalogType.CRON)))
                    .build(),
                TriggerCatalogItem.builder()
                    .category(NGTriggerType.MANIFEST)
                    .triggerCatalogType(new ArrayList<>(Collections.singleton(TriggerCatalogType.HELM_CHART)))
                    .build()));
    List<TriggerCatalogItem> lst = ngTriggerServiceImpl.getTriggerCatalog(ACCOUNT_ID);
    assertThat(lst).isNotNull();
    assertThat(lst.size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCheckAuthorizationSuccessWhenXApiKeyIsPresent() {
    List<HeaderConfig> headerConfigs = Collections.singletonList(
        HeaderConfig.builder().key(X_API_KEY).values(Collections.singletonList(API_KEY)).build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    assertThatCode(()
                       -> ngTriggerServiceImpl.checkAuthorization(
                           ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, IDENTIFIER, headerConfigs))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testCheckAuthorizationWhenXApiKeyIsAbsent() throws IOException {
    List<HeaderConfig> headerConfigs = Collections.emptyList();
    Call<ResponseDTO<SettingValueResponseDTO>> settingValueResponseDTOCall = mock(Call.class);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    when(settingsClient.getSetting(any(), any(), any(), any())).thenReturn(settingValueResponseDTOCall);
    when(settingValueResponseDTOCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    assertThatThrownBy(()
                           -> ngTriggerServiceImpl.checkAuthorization(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, headerConfigs))
        .isInstanceOf(InvalidRequestException.class);

    settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    when(settingValueResponseDTOCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    assertThatCode(()
                       -> ngTriggerServiceImpl.checkAuthorization(
                           ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, IDENTIFIER, headerConfigs))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFetchTriggerEntity() {
    NGTriggerConfigV2 ngTriggerConfigV2 = NGTriggerConfigV2.builder()
                                              .identifier(IDENTIFIER)
                                              .orgIdentifier(ORG_IDENTIFIER)
                                              .projectIdentifier(PROJ_IDENTIFIER)
                                              .inputYaml("inputYaml")
                                              .build();

    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .pollInterval("2m")
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .triggerStatus(TriggerStatus.builder()
                               .webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                                  .registrationResult(WebhookRegistrationStatus.SUCCESS)
                                                                  .build())
                               .webhookInfo(WebhookInfo.builder().webhookId(WEBHOOK_ID).build())
                               .build())
            .build();

    NGTriggerEntity ngTrigger2 =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .pollInterval("2m")
            .yaml("yaml")
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .triggerStatus(TriggerStatus.builder()
                               .webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                                  .registrationResult(WebhookRegistrationStatus.SUCCESS)
                                                                  .build())
                               .webhookInfo(WebhookInfo.builder().webhookId(WEBHOOK_ID).build())
                               .build())
            .build();
    when(ngTriggerElementMapper.toTriggerConfigV2(anyString())).thenReturn(ngTriggerConfigV2);
    when(ngTriggerElementMapper.toTriggerEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, IDENTIFIER, "yaml", true))
        .thenReturn(ngTrigger2);
    doNothing().when(ngTriggerElementMapper).copyEntityFieldsOutsideOfYml(ngTrigger, ngTrigger2);
    when(ngTriggerRepository
             .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot(
                 any(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(Optional.ofNullable(ngTrigger));
    assertThat(ngTriggerServiceImpl.fetchTriggerEntity(
                   ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, "yaml", true))
        .isEqualTo(TriggerDetails.builder().ngTriggerConfigV2(ngTriggerConfigV2).ngTriggerEntity(ngTrigger2).build());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFetchExecutionSummaryV2() throws IOException {
    Call<ResponseDTO<Object>> call = mock(Call.class);
    when(pipelineServiceClient.getExecutionDetailV2("planExecutionId", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .thenReturn(call);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse("object")));
    assertThat(
        ngTriggerServiceImpl.fetchExecutionSummaryV2("planExecutionId", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .isEqualTo("object");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCheckAuthorizationFailureWhenXApiKeyIsPresent() {
    List<HeaderConfig> headerConfigs = Collections.singletonList(
        HeaderConfig.builder().key(X_API_KEY).values(Collections.singletonList(API_KEY)).build());
    doThrow(new AccessDeniedException("Error msg", USER))
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    assertThatThrownBy(()
                           -> ngTriggerServiceImpl.checkAuthorization(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, IDENTIFIER, headerConfigs))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testCreate() {
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.CDS_ENABLE_TRIGGER_YAML_VALIDATION)))
        .thenReturn(false);
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.SPG_DISABLE_CUSTOM_WEBHOOK_V3_URL)))
        .thenReturn(true);
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .identifier(IDENTIFIER)
            .name(NAME)
            .targetType(TargetType.PIPELINE)
            .type(NGTriggerType.WEBHOOK)
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder().git(GitMetadata.builder().build()).build())
                          .build())
            .yaml(ngTriggerYamlWithGitSync)
            .version(0L)
            .triggerStatus(TriggerStatus.builder()
                               .validationStatus(ValidationStatus.builder().statusResult(StatusResult.SUCCESS).build())
                               .build())
            .build();
    doReturn(ngTriggerEntity).when(ngTriggerRepository).save(any());
    when(ngTriggerElementMapper.toTriggerConfigV2(ngTriggerEntity)).thenReturn(null);
    doThrow(new InvalidRequestException("message")).when(triggerReferenceHelper).getReferences(any(), any());

    // when we get exception while publishing setupUsages
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(ngTriggerWebhookRegistrationService.registerWebhook(any()))
        .thenReturn(WebhookRegistrationStatusData.builder()
                        .webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                           .registrationResult(WebhookRegistrationStatus.SUCCESS)
                                                           .build())
                        .build());
    when(ngTriggerRepository.update(any(), any())).thenReturn(ngTriggerEntity);
    ngTriggerServiceImpl.create(ngTriggerEntity);
    verify(ngTriggerWebhookRegistrationService, times(1)).registerWebhook(any());
    verify(triggerSetupUsageHelper, times(0)).publishSetupUsageEvent(any(), any());

    // when updateWebhookRegistrationStatus throws error
    when(ngTriggerRepository.update(any(), any())).thenReturn(null);
    assertThatThrownBy(() -> ngTriggerServiceImpl.create(ngTriggerEntity)).isInstanceOf(InvalidRequestException.class);

    // Exception on saving duplicate entity
    doThrow(new DuplicateKeyException("message")).when(ngTriggerRepository).save(any());
    assertThatThrownBy(() -> ngTriggerServiceImpl.create(ngTriggerEntity)).isInstanceOf(DuplicateFieldException.class);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testUpdate() {
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .identifier(IDENTIFIER)
            .name(NAME)
            .targetType(TargetType.PIPELINE)
            .type(NGTriggerType.WEBHOOK)
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder().git(GitMetadata.builder().build()).build())
                          .build())
            .yaml(ngTriggerYamlWithGitSync)
            .version(0L)
            .triggerStatus(TriggerStatus.builder()
                               .validationStatus(ValidationStatus.builder().statusResult(StatusResult.SUCCESS).build())
                               .build())
            .build();
    NGTriggerEntity oldTriggerEntity = ngTriggerEntity;
    oldTriggerEntity.setName("name2");
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.CDS_ENABLE_TRIGGER_YAML_VALIDATION)))
        .thenReturn(true);
    doNothing().when(ngTriggerYamlSchemaService).validateTriggerYaml(any(), any(), any(), any());

    // when db update returns null
    when(ngTriggerRepository.update(any(), any())).thenReturn(null);
    assertThatThrownBy(() -> ngTriggerServiceImpl.update(ngTriggerEntity, oldTriggerEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("NGTrigger [" + IDENTIFIER + "] couldn't be updated or doesn't exist");

    // when getReferences throws exception
    when(ngTriggerRepository.update(any(), any())).thenReturn(ngTriggerEntity);
    when(ngTriggerElementMapper.toTriggerConfigV2(ngTriggerEntity)).thenReturn(NGTriggerConfigV2.builder().build());
    doThrow(new InvalidRequestException("message")).when(triggerReferenceHelper).getReferences(any(), any());
    ValidationResult validationResult = ValidationResult.builder().success(true).build();
    TriggerDetails triggerDetails = TriggerDetails.builder().build();
    when(ngTriggerElementMapper.toTriggerDetails(any(), any(), any(), any(), anyBoolean())).thenReturn(triggerDetails);
    when(triggerValidationHandler.applyValidations(any())).thenReturn(validationResult);
    ngTriggerServiceImpl.update(ngTriggerEntity, oldTriggerEntity);
    verify(triggerSetupUsageHelper, times(0)).deleteExistingSetupUsages(any());

    List<EntityDetailProtoDTO> referredEntities = Collections.singletonList(EntityDetailProtoDTO.newBuilder().build());
    doReturn(referredEntities).when(triggerReferenceHelper).getReferences(any(), any());
    doNothing().when(triggerSetupUsageHelper).publishSetupUsageEvent(ngTriggerEntity, referredEntities);
    assertThat(ngTriggerServiceImpl.update(ngTriggerEntity, oldTriggerEntity)).isEqualTo(ngTriggerEntity);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testDeleteAllForPipeline() throws IOException {
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .identifier(IDENTIFIER)
            .name(NAME)
            .targetType(TargetType.PIPELINE)
            .type(NGTriggerType.WEBHOOK)
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder().git(GitMetadata.builder().build()).build())
                          .build())
            .yaml(ngTriggerYamlWithGitSync)
            .version(0L)
            .pollInterval("0")
            .triggerStatus(TriggerStatus.builder()
                               .webhookInfo(WebhookInfo.builder().webhookId("webhookId").build())
                               .validationStatus(ValidationStatus.builder().statusResult(StatusResult.SUCCESS).build())
                               .build())
            .build();
    List<NGTriggerEntity> ngTriggerEntities = Collections.singletonList(ngTriggerEntity);
    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndDeletedNot(
             any(), any(), any(), any(), anyBoolean()))
        .thenReturn(Optional.of(ngTriggerEntities));
    PollingItem pollingItem = createPollingItem(ngTriggerEntity);
    Call<Boolean> call = mock(Call.class);
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    Optional<NGTriggerEntity> optionalNGTrigger = Optional.of(ngTriggerEntity);
    byte[] bytes = {70};

    when(pmsFeatureFlagService.isEnabled(eq(ACCOUNT_ID), eq(FeatureName.CD_GIT_WEBHOOK_POLLING)))
        .thenReturn(Boolean.TRUE);
    when(ngTriggerRepository
             .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot(
                 eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER),
                 eq(Boolean.TRUE)))
        .thenReturn(optionalNGTrigger);
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.acknowledged(1));
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(pollingSubscriptionHelper.generatePollingItem(eq(ngTriggerEntity))).thenReturn(pollingItem);
    when(pollingResourceClient.unsubscribe(any())).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(Boolean.TRUE));
    when(kryoSerializer.asBytes(any(PollingItem.class))).thenReturn(bytes);

    assertThat(
        ngTriggerServiceImpl.deleteAllForPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER))
        .isEqualTo(true);

    // Exception occurs while deleting
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.unacknowledged());
    assertThat(
        ngTriggerServiceImpl.deleteAllForPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER))
        .isEqualTo(false);

    // Exception occured while getting ngTriggerEntity from db
    doThrow(new InvalidRequestException("message"))
        .when(ngTriggerRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndDeletedNot(
            any(), any(), any(), any(), anyBoolean());
    assertThat(
        ngTriggerServiceImpl.deleteAllForPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER))
        .isEqualTo(false);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testSubscribePolling() throws IOException {
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .identifier(IDENTIFIER)
            .name(NAME)
            .enabled(false)
            .targetType(TargetType.PIPELINE)
            .type(NGTriggerType.WEBHOOK)
            .webhookId("webhookId")
            .pollInterval("1")
            .metadata(
                NGTriggerMetadata.builder()
                    .buildMetadata(BuildMetadata.builder()
                                       .pollingConfig(PollingConfig.builder().pollingDocId("pollingDocId").build())
                                       .build())
                    .webhook(WebhookMetadata.builder()
                                 .type(GITHUB.getEntityMetadataName())
                                 .git(GitMetadata.builder().build())
                                 .build())
                    .build())
            .yaml(ngTriggerYamlWithGitSync)
            .version(0L)
            .triggerStatus(TriggerStatus.builder()
                               .webhookInfo(WebhookInfo.builder().webhookId("webhookId").build())
                               .validationStatus(ValidationStatus.builder().statusResult(StatusResult.SUCCESS).build())
                               .build())
            .build();
    PollingItem pollingItem = createPollingItem(ngTriggerEntity);
    byte[] bytes = {70};
    when(pollingSubscriptionHelper.generatePollingItem(ngTriggerEntity)).thenReturn(pollingItem);
    when(kryoSerializer.asBytes(pollingItem)).thenReturn(bytes);
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.CD_GIT_WEBHOOK_POLLING))).thenReturn(true);
    Call<Boolean> call = mock(Call.class);
    when(pollingResourceClient.unsubscribe(any())).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(Boolean.TRUE));
    when(ngTriggerRepository.updateValidationStatusAndMetadata(any(), any())).thenReturn(ngTriggerEntity);
    ngTriggerServiceImpl.subscribePolling(ngTriggerEntity, true);
    verify(ngTriggerRepository, times(1)).updateValidationStatusAndMetadata(any(), any());

    // WebhookGitPollingEnabled
    ngTriggerEntity.setEnabled(true);
    ngTriggerEntity.setPollInterval("0");
    ngTriggerServiceImpl.subscribePolling(ngTriggerEntity, true);
    verify(ngTriggerRepository, times(2)).updateValidationStatusAndMetadata(any(), any());

    ngTriggerEntity.setType(NGTriggerType.ARTIFACT);
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(PollingDocument.newBuilder().build());
    Call<ResponseDTO<PollingResponseDTO>> call1 = mock(Call.class);
    when(pollingResourceClient.subscribe(any())).thenReturn(call1);
    when(call1.execute()).thenReturn(Response.success(ResponseDTO.newResponse(PollingResponseDTO.builder().build())));
    ngTriggerServiceImpl.subscribePolling(ngTriggerEntity, true);
    verify(ngTriggerRepository, times(3)).updateValidationStatusAndMetadata(any(), any());

    doThrow(new InvalidRequestException("message")).when(call).execute();
    assertThatThrownBy(() -> ngTriggerServiceImpl.subscribePolling(ngTriggerEntity, true))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCreateCustomWebhookTrigger() {
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(CDS_ENABLE_TRIGGER_YAML_VALIDATION))).thenReturn(false);
    ngTriggerMetadata = NGTriggerMetadata.builder()
                            .webhook(WebhookMetadata.builder().custom(CustomMetadata.builder().build()).build())
                            .build();
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
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
    doReturn(ngTriggerEntity).when(ngTriggerRepository).save(any());
    doReturn(ngTriggerEntity).when(ngTriggerRepository).updateValidationStatus(any(), any());
    doReturn(Collections.emptyList()).when(triggerReferenceHelper).getReferences(any(), any());
    doNothing().when(triggerSetupUsageHelper).publishSetupUsageEvent(any(), any());
    ArgumentCaptor<NGTriggerEntity> entityAfterSave = ArgumentCaptor.forClass(NGTriggerEntity.class);
    NGTriggerEntity createdEntity = ngTriggerServiceImpl.create(ngTriggerEntity);
    verify(ngTriggerRepository, times(1)).save(entityAfterSave.capture());
    assertThat(createdEntity).isNotNull();
    assertThat(entityAfterSave.getValue().getCustomWebhookToken()).isNotNull();
    ngTriggerEntity.setMetadata(NGTriggerMetadata.builder().buildMetadata(BuildMetadata.builder().build()).build());
    ngTriggerEntity.setType(NGTriggerType.ARTIFACT);
    ngTriggerEntity.setCustomWebhookToken(null);
    ArgumentCaptor<NGTriggerEntity> entityAfterSave1 = ArgumentCaptor.forClass(NGTriggerEntity.class);
    NGTriggerEntity createdEntity1 = ngTriggerServiceImpl.create(ngTriggerEntity);
    verify(ngTriggerRepository, times(2)).save(entityAfterSave1.capture());
    assertThat(createdEntity1).isNotNull();
    assertThat(entityAfterSave1.getValue().getCustomWebhookToken()).isNull();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffTriggerWithExtraInput() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml", "trigger-yaml-diff-trigger-extra-input.yaml",
        "trigger-yaml-diff-expected-new-trigger-with-input.yaml", true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffTriggerWithMissingInput() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml", "trigger-yaml-diff-trigger-missing-input.yaml",
        "trigger-yaml-diff-expected-new-trigger-with-input.yaml", true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffTriggerWithNoInput() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml", "trigger-yaml-diff-trigger-no-input.yaml",
        "trigger-yaml-diff-expected-new-trigger-with-input.yaml", true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffTriggerWithRightInput() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml", "trigger-yaml-diff-trigger-right-input.yaml",
        "trigger-yaml-diff-expected-new-trigger-with-input.yaml", true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffTriggerWithWrongInputFormat() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml",
        "trigger-yaml-diff-trigger-wrong-input-format.yaml", "trigger-yaml-diff-expected-new-trigger-with-input.yaml",
        true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffWhenPipelineHasNoInput() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-no-input.yaml", "trigger-yaml-diff-trigger-extra-input.yaml",
        "trigger-yaml-diff-expected-new-trigger-no-input.yaml", true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffWhenTriggerForRemotePipeline() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml", "trigger-yaml-diff-trigger-with-input-set.yaml",
        "trigger-yaml-diff-trigger-with-input-set.yaml", false);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testUpdateBranchName() throws IOException {
    String pipelineBranchName = "pipelineBranchName";
    TriggerUpdateCount triggerUpdateCount = TriggerUpdateCount.builder().failureCount(0).successCount(1).build();
    String filenamePipelineBranchName = "ng-trigger-pipeline-branch-name.yaml";
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .yaml(Resources.toString(
                Objects.requireNonNull(classLoader.getResource(filenamePipelineBranchName)), StandardCharsets.UTF_8))
            .build();
    Optional<List<NGTriggerEntity>> optionalNGTriggerList = Optional.of(Collections.singletonList(ngTrigger));

    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndDeletedNot(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(true)))
        .thenReturn(optionalNGTriggerList);

    when(ngTriggerRepository.updateTriggerYaml(any())).thenReturn(triggerUpdateCount);

    ngTriggerServiceImpl.updateBranchName(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        GitMoveOperationType.REMOTE_TO_INLINE, pipelineBranchName);

    YamlField yamlField = YamlUtils.readTree(ngTrigger.getYaml());
    YamlNode triggerNode = yamlField.getNode().getField("trigger").getNode();
    assertThat((ObjectNode) triggerNode.getCurrJsonNode().get(PIPELINE_BRANCH_NAME)).isNull();

    ngTriggerServiceImpl.updateBranchName(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        GitMoveOperationType.INLINE_TO_REMOTE, pipelineBranchName);

    YamlField yamlField1 = YamlUtils.readTree(ngTrigger.getYaml());
    YamlNode triggerNode1 = yamlField1.getNode().getField("trigger").getNode();
    assertThat(triggerNode1.getCurrJsonNode().get(PIPELINE_BRANCH_NAME)).isEqualTo(new TextNode(pipelineBranchName));

    filenamePipelineBranchName = "pipeline-v1.yaml";
    ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .yaml(Resources.toString(
                Objects.requireNonNull(classLoader.getResource(filenamePipelineBranchName)), StandardCharsets.UTF_8))
            .build();
    optionalNGTriggerList = Optional.of(Collections.singletonList(ngTrigger));
    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndDeletedNot(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(true)))
        .thenReturn(optionalNGTriggerList);

    when(ngTriggerRepository.updateTriggerYaml(any()))
        .thenReturn(TriggerUpdateCount.builder().failureCount(0).successCount(0).build());

    assertThat(ngTriggerServiceImpl.updateBranchName(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
                   GitMoveOperationType.REMOTE_TO_INLINE, pipelineBranchName))
        .isEqualTo(TriggerUpdateCount.builder().failureCount(1L).successCount(0L).build());

    when(ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndDeletedNot(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(true)))
        .thenReturn(Optional.empty());
    assertThat(ngTriggerServiceImpl.updateBranchName(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
                   GitMoveOperationType.REMOTE_TO_INLINE, pipelineBranchName))
        .isEqualTo(TriggerUpdateCount.builder().failureCount(0L).successCount(0L).build());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetInvalidFQNsInTrigger() throws IOException {
    String pipelineFilename = "ng-trigger-pipeline.yaml";
    String triggerFileName = "ng-trigger-input.yaml";
    String templateYaml = createRuntimeInputFormForTrigger(
        Resources.toString(Objects.requireNonNull(classLoader.getResource(pipelineFilename)), StandardCharsets.UTF_8));
    JsonNode node = YamlUtils
                        .readTree(Resources.toString(
                            Objects.requireNonNull(classLoader.getResource(triggerFileName)), StandardCharsets.UTF_8))
                        .getNode()
                        .getCurrJsonNode();
    ObjectNode innerMap = (ObjectNode) node.get("trigger");
    JsonNode inputYaml = innerMap.get("inputYaml");
    JsonNode pipelineNode = YamlUtils.readTree(inputYaml.asText()).getNode().getCurrJsonNode();
    String triggerPipelineYaml = YamlUtils.writeYamlString(pipelineNode);

    assertThat(ngTriggerServiceImpl.getInvalidFQNsInTrigger(templateYaml, triggerPipelineYaml, ACCOUNT_ID).size())
        .isEqualTo(0);

    String triggerExtraInputFileName = "ng-trigger-extra-input.yaml";
    node = YamlUtils
               .readTree(Resources.toString(
                   Objects.requireNonNull(classLoader.getResource(triggerExtraInputFileName)), StandardCharsets.UTF_8))
               .getNode()
               .getCurrJsonNode();
    innerMap = (ObjectNode) node.get("trigger");
    inputYaml = innerMap.get("inputYaml");
    pipelineNode = YamlUtils.readTree(inputYaml.asText()).getNode().getCurrJsonNode();
    String extraInputTriggerPipelineYaml = YamlUtils.writeYamlString(pipelineNode);

    Map<FQN, String> extraInputResult =
        ngTriggerServiceImpl.getInvalidFQNsInTrigger(templateYaml, extraInputTriggerPipelineYaml, ACCOUNT_ID);
    assertThat(extraInputResult.size()).isEqualTo(3);
    assertThat(extraInputResult.containsValue("Field either not present in pipeline or not a runtime input"))
        .isEqualTo(true);
  }

  private void checkTriggerYamlDiff(String filenamePipeline, String filenameTrigger, String filenameNewTrigger,
      Boolean useNullPipelineBranchName) throws IOException {
    String newTriggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filenameNewTrigger)), StandardCharsets.UTF_8);
    String triggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filenameTrigger)), StandardCharsets.UTF_8);
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .accountId(ACCOUNT_ID)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJ_IDENTIFIER)
                                          .targetIdentifier(PIPELINE_IDENTIFIER)
                                          .identifier(IDENTIFIER)
                                          .name(NAME)
                                          .targetType(TargetType.PIPELINE)
                                          .type(NGTriggerType.WEBHOOK)
                                          .metadata(ngTriggerMetadata)
                                          .yaml(triggerYaml)
                                          .version(0L)
                                          .build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntity)
            .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                   .inputSetRefs(Collections.emptyList())
                                   .pipelineBranchName(useNullPipelineBranchName ? null : "pipelineBranchName")
                                   .build())
            .build();
    String pipelineYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filenamePipeline)), StandardCharsets.UTF_8);
    when(validationHelper.fetchPipelineYamlForTrigger(any())).thenReturn(Optional.ofNullable(pipelineYaml));
    TriggerYamlDiffDTO yamlDiffResponse = ngTriggerServiceImpl.getTriggerYamlDiff(triggerDetails);
    assertThat(yamlDiffResponse.getNewYAML().replace("<+input>", "1")).isEqualTo(newTriggerYaml);
  }

  private String createRuntimeInputFormForTrigger(String yaml) {
    YamlConfig yamlConfig = new YamlConfig(yaml);
    Map<FQN, Object> fullMap = yamlConfig.getFqnToValueMap();
    Map<FQN, Object> templateMap = new LinkedHashMap<>();
    fullMap.keySet().forEach(key -> {
      String value = fullMap.get(key).toString().replace("\"", "");
      if (NGExpressionUtils.matchesInputSetPattern(value)) {
        templateMap.put(key, fullMap.get(key));
      }
    });
    return (new YamlConfig(templateMap, yamlConfig.getYamlMap())).getYaml();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testCronTriggerInterval() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(NGTriggerEntity.builder().identifier("id").name("name").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .type(NGTriggerType.SCHEDULED)
                                .spec(ScheduledTriggerConfig.builder()
                                          .type("Cron")
                                          .spec(CronTriggerSpec.builder().expression("0/2 * * * *").build())
                                          .build())
                                .build())
                    .build())
            .build();

    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testUpdateTriggerStatus() {
    NGTriggerEntity ngTrigger = NGTriggerEntity.builder().build();
    when(ngTriggerRepository.update(any(), any())).thenReturn(ngTrigger);
    assertThat(ngTriggerServiceImpl.updateTriggerStatus(ngTrigger, false)).isEqualTo(false);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testInvalidPipelineBranchName() {
    PMSPipelineResponseDTO pipelineResponse =
        PMSPipelineResponseDTO.builder().storeType(StoreType.REMOTE).yamlPipeline("yamlPipeline").build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder().ngTriggerConfigV2(NGTriggerConfigV2.builder().build()).build();

    when(validationHelper.fetchPipelineForTrigger(triggerDetails)).thenReturn(pipelineResponse);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validatePipelineRef(triggerDetails))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("pipelineBranchName is missing or is empty.");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateTriggerWithValidationStatusWhileRuntime() {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .identifier("id")
                                          .orgIdentifier("orgId")
                                          .projectIdentifier("projId")
                                          .targetIdentifier("targetId")
                                          .enabled(true)
                                          .name("name")
                                          .build();
    doNothing().when(ngTriggerElementMapper).updateEntityYmlWithEnabledValue(ngTriggerEntity);
    doReturn(ngTriggerEntity).when(ngTriggerRepository).updateValidationStatus(any(), any());
    ArgumentCaptor<NGTriggerEntity> entityAfterUpdate = ArgumentCaptor.forClass(NGTriggerEntity.class);
    ngTriggerServiceImpl.updateTriggerWithValidationStatus(
        ngTriggerEntity, ValidationResult.builder().success(false).message("message").build(), true);
    verify(ngTriggerRepository, times(1)).updateValidationStatus(any(), entityAfterUpdate.capture());
    assertThat(entityAfterUpdate.getValue().getEnabled()).isTrue();
    assertThat(entityAfterUpdate.getValue().getTriggerStatus().getValidationStatus().getStatusResult())
        .isEqualTo(StatusResult.FAILED);
    assertThat(entityAfterUpdate.getValue().getTriggerStatus().getValidationStatus().getDetailedMessage())
        .isEqualTo("message");

    ArgumentCaptor<NGTriggerEntity> entityAfterUpdate1 = ArgumentCaptor.forClass(NGTriggerEntity.class);
    ngTriggerServiceImpl.updateTriggerWithValidationStatus(
        ngTriggerEntity, ValidationResult.builder().success(false).message("message").build(), false);
    verify(ngTriggerRepository, times(2)).updateValidationStatus(any(), entityAfterUpdate1.capture());
    assertThat(entityAfterUpdate1.getValue().getEnabled()).isFalse();
    assertThat(entityAfterUpdate1.getValue().getTriggerStatus().getValidationStatus().getStatusResult())
        .isEqualTo(StatusResult.FAILED);
    assertThat(entityAfterUpdate1.getValue().getTriggerStatus().getValidationStatus().getDetailedMessage())
        .isEqualTo("message");

    when(ngTriggerRepository.updateValidationStatus(any(), any())).thenReturn(null);
    assertThatThrownBy(()
                           -> ngTriggerServiceImpl.updateTriggerWithValidationStatus(ngTriggerEntity,
                               ValidationResult.builder().success(false).message("message").build(), true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("NGTrigger [id] couldn't be updated or doesn't exist");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testIncorrectNameInYaml() {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .accountId(ACCOUNT_ID)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJ_IDENTIFIER)
                                          .identifier(IDENTIFIER)
                                          .yaml("yaml")
                                          .build();
    NGTriggerEntity oldNgTriggerEntity = NGTriggerEntity.builder()
                                             .accountId(ACCOUNT_ID)
                                             .orgIdentifier(ORG_IDENTIFIER)
                                             .projectIdentifier(PROJ_IDENTIFIER)
                                             .identifier(IDENTIFIER)
                                             .yaml("yaml")
                                             .build();
    when(pmsFeatureFlagService.isEnabled(any(), eq(CDS_ENABLE_TRIGGER_YAML_VALIDATION))).thenReturn(Boolean.TRUE);
    doThrow(new InvalidYamlException("message", null, null))
        .when(ngTriggerYamlSchemaService)
        .validateTriggerYaml("yaml", PROJ_IDENTIFIER, ORG_IDENTIFIER, IDENTIFIER);
    assertThatThrownBy(() -> ngTriggerServiceImpl.create(ngTriggerEntity))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("message");

    assertThatThrownBy(() -> ngTriggerServiceImpl.update(ngTriggerEntity, oldNgTriggerEntity))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("message");
  }
}
