/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.VALIDATION_FAILED_FOR_TRIGGER;
import static io.harness.rule.OwnerRule.MEET;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.eventmapper.filters.impl.buildtrigger.BuildTriggerEventConditionsFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.buildtrigger.BuildTriggerValidationFilter;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.validations.TriggerValidationHandler;
import io.harness.polling.contracts.BuildInfo;
import io.harness.polling.contracts.Metadata;
import io.harness.polling.contracts.PollingResponse;
import io.harness.rule.Owner;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;

public class BuildTriggerValidationFilterTest extends CategoryTest {
  @Inject @InjectMocks private BuildTriggerValidationFilter buildTriggerValidationFilter;
  private ListAppender<ILoggingEvent> listAppender;
  @InjectMocks @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  @Mock BuildTriggerHelper buildTriggerHelper;
  @Mock TriggerValidationHandler triggerValidationHandler;
  private Logger logger;
  private String ngTriggerYaml_artifact_ecr;
  private String ngTriggerYaml_artifact_gcs_manifest;

  @Before
  public void setUp() throws IOException, IllegalAccessException {
    initMocks(this);
    ClassLoader classLoader = getClass().getClassLoader();
    logger = (Logger) LoggerFactory.getLogger(BuildTriggerEventConditionsFilter.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
    ngTriggerYaml_artifact_ecr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-ecr.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_artifact_gcs_manifest = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-manifest-helm-gcs.yaml")), StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void applyRepoUrlFilterNoMatchTest() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_artifact_ecr);
    Long createdAt = 12L;
    TriggerDetails details1 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .identifier("first_trigger")
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())

                    .originalEvent(TriggerWebhookEvent.builder()
                                       .sourceRepoType(WebhookTriggerType.GITHUB.getEntityMetadataName())
                                       .createdAt(createdAt)
                                       .build())
                    .build())
            .pollingResponse(PollingResponse.newBuilder()
                                 .setBuildInfo(BuildInfo.newBuilder()
                                                   .addAllVersions(Collections.singletonList("v0"))
                                                   .addAllMetadata(Collections.singletonList(
                                                       Metadata.newBuilder().putAllMetadata(metadata).build()))
                                                   .build())
                                 .build())
            .details(asList(details1))
            .build();
    doReturn("BuildTrigger").when(buildTriggerHelper).generatePollingDescriptor(filterRequestData.getPollingResponse());
    doThrow(new InvalidRequestException("message")).when(triggerValidationHandler).applyValidations(details1);
    WebhookEventMappingResponse webhookEventMappingResponse =
        buildTriggerValidationFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getMessage())
        .isEqualTo("All Mapped Triggers failed validation for Event: BuildTrigger");
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
    assertThat(
        webhookEventMappingResponse.getUnMatchedTriggerInfoList().get(0).getUnMatchedTriggers().getNgTriggerConfigV2())
        .isEqualTo(ngTriggerConfigV2);
    assertThat(webhookEventMappingResponse.getUnMatchedTriggerInfoList().get(0).getFinalStatus())
        .isEqualTo(VALIDATION_FAILED_FOR_TRIGGER);
    assertThat(webhookEventMappingResponse.getUnMatchedTriggerInfoList().get(0).getMessage())
        .isEqualTo("first_trigger didn't match polling event after event condition evaluation");
  }
}
