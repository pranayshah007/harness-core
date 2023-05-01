/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_METADATA_CONDITIONS;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.BuildMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.DockerRegistrySpec;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.conditionchecker.ConditionOperator;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.polling.contracts.BuildInfo;
import io.harness.polling.contracts.Metadata;
import io.harness.polling.contracts.PollingResponse;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class TriggerMetaDataConditionFilterTest extends CategoryTest {
  @Inject @InjectMocks private MetadataConditionsTriggerFilter filter;

  @Before
  public void setUp() throws IOException {
    initMocks(this);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void applyFilterTest() {
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder()
            .originalEvent(TriggerWebhookEvent.builder().accountId("acc").createdAt(0l).build())
            .build();

    NGTriggerEntity triggerEntity =
        NGTriggerEntity.builder()
            .metadata(NGTriggerMetadata.builder().buildMetadata(BuildMetadata.builder().type(ARTIFACT).build()).build())
            .yaml("yaml")
            .enabled(true)
            .build();

    List<TriggerEventDataCondition> triggerMetaDataConditions = new ArrayList<>();
    triggerMetaDataConditions.add(TriggerEventDataCondition.builder()
                                      .key("<+trigger.artifact.metadata.key1>")
                                      .operator(ConditionOperator.EQUALS)
                                      .value("value1")
                                      .build());
    triggerMetaDataConditions.add(TriggerEventDataCondition.builder()
                                      .key("<+trigger.artifact.metadata.key2>")
                                      .operator(ConditionOperator.CONTAINS)
                                      .value("value2")
                                      .build());
    NGTriggerConfigV2 ngTriggerConfig =
        NGTriggerConfigV2.builder()
            .source(NGTriggerSourceV2.builder()
                        .type(ARTIFACT)
                        .spec(ArtifactTriggerConfig.builder()
                                  .type(ArtifactType.DOCKER_REGISTRY)
                                  .spec(DockerRegistrySpec.builder()
                                            .connectorRef("connector")
                                            .metaDataConditions(triggerMetaDataConditions)
                                            .build())
                                  .build())
                        .build())
            .build();

    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    WebhookEventMappingResponse webhookEventMappingResponse = filter.applyFilter(
        FilterRequestData.builder()
            .accountId("accountId")
            .isCustomTrigger(false)
            .details(List.of(
                TriggerDetails.builder().ngTriggerConfigV2(ngTriggerConfig).ngTriggerEntity(triggerEntity).build()))
            .webhookPayloadData(webhookPayloadData)
            .pollingResponse(PollingResponse.newBuilder()
                                 .setBuildInfo(BuildInfo.newBuilder()
                                                   .addAllVersions(Collections.singletonList("v0"))
                                                   .addAllMetadata(Collections.singletonList(
                                                       Metadata.newBuilder().putAllMetadata(metadata).build()))
                                                   .build())

                                 .build())
            .build());
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    assertThat(webhookEventMappingResponse.getTriggers().size()).isEqualTo(1);
    assertThat(webhookEventMappingResponse.getTriggers().get(0).getNgTriggerConfigV2()).isEqualTo(ngTriggerConfig);

    Map<String, String> metadata1 = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value3");
    WebhookEventMappingResponse webhookEventMappingResponse1 = filter.applyFilter(
        FilterRequestData.builder()
            .accountId("accountId")
            .isCustomTrigger(false)
            .details(List.of(
                TriggerDetails.builder().ngTriggerConfigV2(ngTriggerConfig).ngTriggerEntity(triggerEntity).build()))
            .webhookPayloadData(webhookPayloadData)
            .pollingResponse(PollingResponse.newBuilder()
                                 .setBuildInfo(BuildInfo.newBuilder()
                                                   .addAllVersions(Collections.singletonList("v0"))
                                                   .addAllMetadata(Collections.singletonList(
                                                       Metadata.newBuilder().putAllMetadata(metadata1).build()))
                                                   .build())

                                 .build())
            .build());
    assertThat(webhookEventMappingResponse1.isFailedToFindTrigger()).isTrue();
    assertThat(webhookEventMappingResponse1.getWebhookEventResponse()).isNotNull();
    assertThat(webhookEventMappingResponse1.getWebhookEventResponse().getFinalStatus())
        .isEqualTo(NO_MATCHING_TRIGGER_FOR_METADATA_CONDITIONS);
  }
}
