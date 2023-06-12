package io.harness.ngtriggers.mapper;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.ArtifactTriggerEventInfo;
import io.harness.ngtriggers.beans.dto.ManifestTriggerEventInfo;
import io.harness.ngtriggers.beans.dto.NGTriggerEventHistoryDTO;
import io.harness.ngtriggers.beans.dto.PollingDocumentInfo;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NGTriggerEventHistoryMapperTest extends CategoryTest {
  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String pipelineId = "pipelineId";
  String triggerIdentifier = "triggerIdentifier";
  String pollingDocId = "pollingDocId";
  String message = "message";
  String uuid = "uuid";
  String payload = "payload";
  Long createdAt = 12L;

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testToTriggerEventHistoryDto() {
    TriggerEventHistory triggerEventHistory = TriggerEventHistory.builder()
                                                  .triggerIdentifier(triggerIdentifier)
                                                  .accountId(accountId)
                                                  .pollingDocId(pollingDocId)
                                                  .orgIdentifier(orgId)
                                                  .projectIdentifier(projectId)
                                                  .build();
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .accountId(accountId)
                                          .orgIdentifier(orgId)
                                          .identifier(triggerIdentifier)
                                          .projectIdentifier(projectId)
                                          .type(NGTriggerType.ARTIFACT)
                                          .build();
    NGTriggerEventHistoryDTO ngTriggerEventHistoryDTO =
        NGTriggerEventHistoryDTO.builder()
            .accountId(accountId)
            .projectIdentifier(projectId)
            .type(NGTriggerType.ARTIFACT)
            .orgIdentifier(orgId)
            .triggerIdentifier(triggerIdentifier)
            .ngTriggerEventInfo(
                ArtifactTriggerEventInfo.builder()
                    .pollingDocumentInfo(PollingDocumentInfo.builder().pollingDocumentId(pollingDocId).build())
                    .build())
            .build();

    NGTriggerEventHistoryDTO response =
        NGTriggerEventHistoryMapper.toTriggerEventHistoryDto(triggerEventHistory, ngTriggerEntity);
    assertThat(ngTriggerEventHistoryDTO).isEqualTo(response);

    // Manifest triggers
    ngTriggerEntity.setType(NGTriggerType.MANIFEST);
    ngTriggerEventHistoryDTO.setType(NGTriggerType.MANIFEST);
    ngTriggerEventHistoryDTO.setNgTriggerEventInfo(
        ManifestTriggerEventInfo.builder()
            .pollingDocumentInfo(PollingDocumentInfo.builder().pollingDocumentId(pollingDocId).build())
            .build());
    response = NGTriggerEventHistoryMapper.toTriggerEventHistoryDto(triggerEventHistory, ngTriggerEntity);
    assertThat(ngTriggerEventHistoryDTO).isEqualTo(response);
  }
}
