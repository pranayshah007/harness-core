/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;
import static io.harness.ngtriggers.beans.target.TargetType.PIPELINE;
import static io.harness.rule.OwnerRule.MEET;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.rule.Owner;

import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TriggerSetupUsageHelperTest extends CategoryTest {
  @InjectMocks TriggerSetupUsageHelper triggerSetupUsageHelper;
  @Mock Producer producer;
  private String accountId = "accountId";
  private String orgId = "orgId";
  private String projectId = "projectId";
  private String pipelineId = "pipelineId";
  private String triggerId = "triggerId";
  private String name = "name";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testPublishSetupUsageEvent() {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .accountId(accountId)
                                          .projectIdentifier(projectId)
                                          .orgIdentifier(orgId)
                                          .identifier(triggerId)
                                          .targetIdentifier(pipelineId)
                                          .targetType(PIPELINE)
                                          .name(name)
                                          .build();

    List<EntityDetailProtoDTO> referredEntities = new ArrayList<>();
    EntityDetailProtoDTO secretDetails = EntityDetailProtoDTO.newBuilder()
                                             .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                                                   .setAccountIdentifier(StringValue.of(accountId))
                                                                   .setOrgIdentifier(StringValue.of(orgId))
                                                                   .setProjectIdentifier(StringValue.of(projectId))
                                                                   .setIdentifier(StringValue.of("secretId"))
                                                                   .build())
                                             .setType(EntityTypeProtoEnum.SECRETS)
                                             .build();
    referredEntities.add(secretDetails);

    when(producer.send(any())).thenReturn("");
    triggerSetupUsageHelper.publishSetupUsageEvent(ngTriggerEntity, referredEntities);
    verify(producer, times(2)).send(any());
  }
}
