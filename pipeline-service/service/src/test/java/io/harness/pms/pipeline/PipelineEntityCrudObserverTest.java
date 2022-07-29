/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.SAHIL;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.pms.events.PipelineDeleteEvent;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.StringValue;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(PIPELINE)
public class PipelineEntityCrudObserverTest extends PipelineServiceTestBase {
  private static final String ACCOUNT_ID = "accountId";
  private static final String PROJECT_ID = "projectId";
  private static final String ORG_ID = "orgId";
  private static final String PIPELINE_ID = "pipelineId";

  @Mock private Producer eventProducer;
  @InjectMocks private PipelineEntityCrudObserver pipelineRBACObserver;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testOnDelete() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .projectIdentifier(PROJECT_ID)
                                        .identifier(PIPELINE_ID)
                                        .build();
    pipelineRBACObserver.onDelete(new PipelineDeleteEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntity));
    EntityChangeDTO.Builder pipelineEntityChangeDTOBuilder =
        EntityChangeDTO.newBuilder()
            .setAccountIdentifier(StringValue.of(pipelineEntity.getAccountId()))
            .setOrgIdentifier(StringValue.of(pipelineEntity.getOrgIdentifier()))
            .setProjectIdentifier(StringValue.of(pipelineEntity.getProjectIdentifier()))
            .setIdentifier(StringValue.of(pipelineEntity.getIdentifier()));

    Mockito.verify(eventProducer)
        .send(Message.newBuilder()
                  .putAllMetadata(ImmutableMap.of("accountId", pipelineEntity.getAccountId(),
                      EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.PIPELINE_ENTITY,
                      EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
                  .setData(pipelineEntityChangeDTOBuilder.build().toByteString())
                  .build());
  }
}
