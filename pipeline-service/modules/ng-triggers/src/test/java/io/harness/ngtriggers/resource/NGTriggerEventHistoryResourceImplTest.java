/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.resource;

import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.SRIDHAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.dto.PolledResponse;
import io.harness.dto.PollingInfoForTriggers;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.NGTriggerEventHistoryBaseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerEventHistoryDTO;
import io.harness.ngtriggers.beans.dto.PollingConfig;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;
import io.harness.ngtriggers.beans.entity.metadata.BuildMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.response.TriggerEventStatus;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.rule.Owner;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

public class NGTriggerEventHistoryResourceImplTest extends CategoryTest {
  private final String IDENTIFIER = "first_trigger";
  private final String NAME = "first trigger";
  private final String PIPELINE_IDENTIFIER = "myPipeline";
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";

  private final String EVENT_CORRELATION_ID = "event_correlationId";

  @Mock NGTriggerService ngTriggerService;
  @Mock NGTriggerEventsService ngTriggerEventsService;

  @InjectMocks NGTriggerEventHistoryResourceImpl ngTriggerEventHistoryResource;

  private NGTriggerEntity ngTriggerEntity;

  private String ngTriggerYaml;

  private NGTriggerConfigV2 ngTriggerConfig;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    ClassLoader classLoader = getClass().getClassLoader();
    String filename = "ng-trigger-github-pr-v2.yaml";
    ngTriggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    ngTriggerConfig = YamlPipelineUtils.read(ngTriggerYaml, NGTriggerConfigV2.class);
    WebhookTriggerConfigV2 webhookTriggerConfig = (WebhookTriggerConfigV2) ngTriggerConfig.getSource().getSpec();
    WebhookMetadata metadata = WebhookMetadata.builder().type(webhookTriggerConfig.getType().getValue()).build();
    NGTriggerMetadata ngTriggerMetadata = NGTriggerMetadata.builder().webhook(metadata).build();

    ngTriggerEntity = NGTriggerEntity.builder()
                          .accountId(ACCOUNT_ID)
                          .orgIdentifier(ORG_IDENTIFIER)
                          .projectIdentifier(PROJ_IDENTIFIER)
                          .targetIdentifier(PIPELINE_IDENTIFIER)
                          .identifier(IDENTIFIER)
                          .name(NAME)
                          .targetType(TargetType.PIPELINE)
                          .type(NGTriggerType.WEBHOOK)
                          .metadata(ngTriggerMetadata)
                          .yaml(ngTriggerYaml)
                          .version(0L)
                          .build();
  }

  @Test(expected = EntityNotFoundException.class)
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerEventHistoryException() {
    doReturn(Optional.empty())
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);

    ngTriggerEventHistoryResource.getTriggerEventHistory(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, "", 0, 10, new ArrayList<>());
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerEventHistory() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TriggerEventHistoryKeys.createdAt));

    TriggerEventHistory eventHistory = TriggerEventHistory.builder()
                                           .triggerIdentifier(IDENTIFIER)
                                           .accountId(ACCOUNT_ID)
                                           .orgIdentifier(ORG_IDENTIFIER)
                                           .projectIdentifier(PROJ_IDENTIFIER)
                                           .targetIdentifier(PIPELINE_IDENTIFIER)
                                           .eventCorrelationId("event_correlation_id")
                                           .finalStatus("NO_MATCHING_TRIGGER_FOR_REPO")
                                           .build();

    Page<TriggerEventHistory> eventHistoryPage = new PageImpl<>(Collections.singletonList(eventHistory), pageable, 1);

    Criteria criteria = Criteria.where("a").is("b");
    doReturn(criteria)
        .when(ngTriggerEventsService)
        .formTriggerEventCriteria(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER),
            eq(IDENTIFIER), anyString(), anyList());

    doReturn(Optional.of(ngTriggerEntity))
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    doReturn(eventHistoryPage).when(ngTriggerEventsService).getEventHistory(criteria, pageable);

    Page<NGTriggerEventHistoryDTO> content = ngTriggerEventHistoryResource
                                                 .getTriggerEventHistory(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                                     PIPELINE_IDENTIFIER, IDENTIFIER, "", 0, 10, new ArrayList<>())
                                                 .getData();

    assertThat(content).isNotEmpty();
    assertThat(content.getNumberOfElements()).isEqualTo(1);

    NGTriggerEventHistoryDTO responseDto = content.toList().get(0);
    assertThat(responseDto.getTriggerIdentifier()).isEqualTo(IDENTIFIER);
    assertThat(responseDto.getFinalStatus())
        .isEqualTo(TriggerEventResponse.FinalStatus.valueOf(eventHistory.getFinalStatus()));
    assertThat(responseDto.getTriggerEventStatus().getMessage()).isEqualTo("No matching trigger for repo");
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerEventHistoryWrongFinalStatus() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TriggerEventHistoryKeys.createdAt));

    TriggerEventHistory eventHistory = TriggerEventHistory.builder()
                                           .triggerIdentifier(IDENTIFIER)
                                           .accountId(ACCOUNT_ID)
                                           .orgIdentifier(ORG_IDENTIFIER)
                                           .projectIdentifier(PROJ_IDENTIFIER)
                                           .targetIdentifier(PIPELINE_IDENTIFIER)
                                           .eventCorrelationId("event_correlation_id")
                                           .finalStatus("NOT_AVAILABLE")
                                           .build();

    Page<TriggerEventHistory> eventHistoryPage = new PageImpl<>(Collections.singletonList(eventHistory), pageable, 1);

    Criteria criteria = Criteria.where("a").is("b");
    doReturn(criteria)
        .when(ngTriggerEventsService)
        .formTriggerEventCriteria(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER),
            eq(IDENTIFIER), anyString(), anyList());

    doReturn(Optional.of(ngTriggerEntity))
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    doReturn(eventHistoryPage).when(ngTriggerEventsService).getEventHistory(criteria, pageable);

    Page<NGTriggerEventHistoryDTO> content = ngTriggerEventHistoryResource
                                                 .getTriggerEventHistory(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                                     PIPELINE_IDENTIFIER, IDENTIFIER, "", 0, 10, new ArrayList<>())
                                                 .getData();

    assertThat(content).isNotEmpty();
    assertThat(content.getNumberOfElements()).isEqualTo(1);

    NGTriggerEventHistoryDTO responseDto = content.toList().get(0);
    assertThat(responseDto.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(responseDto.getTargetIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(responseDto.getFinalStatus()).isNull();
    assertThat(responseDto.getTriggerEventStatus().getStatus()).isEqualTo(TriggerEventStatus.FinalResponse.FAILED);
    assertThat(responseDto.getTriggerEventStatus().getMessage()).isEqualTo("Unknown status");
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerHistoryEventCorrelation() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TriggerEventHistoryKeys.createdAt));

    TriggerEventHistory eventHistory = TriggerEventHistory.builder()
                                           .accountId(ACCOUNT_ID)
                                           .triggerIdentifier(IDENTIFIER)
                                           .eventCorrelationId(EVENT_CORRELATION_ID)
                                           .finalStatus("NO_MATCHING_TRIGGER_FOR_REPO")
                                           .build();

    Page<TriggerEventHistory> eventHistoryPage = new PageImpl<>(Collections.singletonList(eventHistory), pageable, 1);

    Criteria criteria = Criteria.where("a").is("b");
    doReturn(criteria)
        .when(ngTriggerEventsService)
        .formEventCriteria(eq(ACCOUNT_ID), eq(EVENT_CORRELATION_ID), anyList());
    doReturn(eventHistoryPage).when(ngTriggerEventsService).getEventHistory(criteria, pageable);

    Page<NGTriggerEventHistoryDTO> content =
        ngTriggerEventHistoryResource
            .getTriggerHistoryEventCorrelation(ACCOUNT_ID, EVENT_CORRELATION_ID, 0, 10, new ArrayList<>())
            .getData();

    assertThat(content).isNotEmpty();
    assertThat(content.getNumberOfElements()).isEqualTo(1);
    assertThat(content.getContent().get(0).getTriggerIdentifier()).isEqualTo(IDENTIFIER);

    NGTriggerEventHistoryBaseDTO responseDto = content.toList().get(0);
    assertThat(responseDto.getEventCorrelationId()).isEqualTo(EVENT_CORRELATION_ID);
    assertThat(responseDto.getFinalStatus())
        .isEqualTo(TriggerEventResponse.FinalStatus.valueOf(eventHistory.getFinalStatus()));
    assertThat(responseDto.getTriggerEventStatus().getMessage()).isEqualTo("No matching trigger for repo");
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerHistoryEventCorrelationStatus() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TriggerEventHistoryKeys.createdAt));

    TriggerEventHistory eventHistory = TriggerEventHistory.builder()
                                           .accountId(ACCOUNT_ID)
                                           .eventCorrelationId("event_correlation_id")
                                           .finalStatus("NOT_AVAILABLE")
                                           .build();

    Page<TriggerEventHistory> eventHistoryPage = new PageImpl<>(Collections.singletonList(eventHistory), pageable, 1);

    Criteria criteria = Criteria.where("a").is("b");
    doReturn(criteria)
        .when(ngTriggerEventsService)
        .formEventCriteria(eq(ACCOUNT_ID), eq(EVENT_CORRELATION_ID), anyList());
    doReturn(eventHistoryPage).when(ngTriggerEventsService).getEventHistory(criteria, pageable);

    Page<NGTriggerEventHistoryDTO> content =
        ngTriggerEventHistoryResource
            .getTriggerHistoryEventCorrelation(ACCOUNT_ID, EVENT_CORRELATION_ID, 0, 10, new ArrayList<>())
            .getData();

    assertThat(content).isNotEmpty();
    assertThat(content.getNumberOfElements()).isEqualTo(1);

    NGTriggerEventHistoryBaseDTO responseDto = content.toList().get(0);
    assertThat(responseDto.getFinalStatus()).isNull();
    assertThat(responseDto.getTriggerEventStatus().getStatus()).isEqualTo(TriggerEventStatus.FinalResponse.FAILED);
    assertThat(responseDto.getTriggerEventStatus().getMessage()).isEqualTo("Unknown status");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void getPolledResponseForTriggerTest() {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .identifier(IDENTIFIER)
            .name(NAME)
            .targetType(TargetType.PIPELINE)
            .type(NGTriggerType.ARTIFACT)
            .metadata(
                NGTriggerMetadata.builder()
                    .buildMetadata(BuildMetadata.builder()
                                       .pollingConfig(PollingConfig.builder().pollingDocId("pollingDocId").build())
                                       .build())
                    .build())
            .yaml(ngTriggerYaml)
            .version(0L)
            .build();
    doReturn(Optional.empty())
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    assertThatThrownBy(()
                           -> ngTriggerEventHistoryResource.getPolledResponseForTrigger(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Trigger " + IDENTIFIER + " does not exist");

    doReturn(Optional.of(ngTriggerEntity))
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    assertThatThrownBy(()
                           -> ngTriggerEventHistoryResource.getPolledResponseForTrigger(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Trigger " + IDENTIFIER + " is not of Artifact or Manifest type");

    doReturn(Optional.of(ngTrigger))
        .when(ngTriggerService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, false);
    Set<String> allPolledKeys = new HashSet<>();
    allPolledKeys.add("key1");
    allPolledKeys.add("key2");
    doReturn(ResponseDTO.newResponse(PollingInfoForTriggers.builder()
                                         .polledResponse(PolledResponse.builder().allPolledKeys(allPolledKeys).build())
                                         .build()))
        .when(ngTriggerEventsService)
        .getPollingInfo(ACCOUNT_ID, "pollingDocId");
    assertThat(
        ngTriggerEventHistoryResource
            .getPolledResponseForTrigger(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER)
            .getData()
            .getPolledResponse()
            .getAllPolledKeys())
        .isEqualTo(allPolledKeys);
  }
}
