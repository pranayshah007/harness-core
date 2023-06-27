/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.CustomChangeWebhookPayload;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.cv.CustomChangeEventDTO;
import io.harness.eventsframework.schemas.cv.CustomChangeEventDetails;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomChangeEventPublisherServiceImplTest extends CvNextGenTestBase {
  private BuilderFactory builderFactory;
  private CustomChangeEventPublisherServiceImpl customChangeEventPublisherServiceImpl;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    customChangeEventPublisherServiceImpl = new CustomChangeEventPublisherServiceImpl();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetCustomChangeEventMessage() {
    ProjectParams projectParams = builderFactory.getProjectParams();
    CustomChangeWebhookPayload customChangeWebhookPayload =
        builderFactory.getCustomChangeWebhookPayloadBuilder().build();
    Message message = customChangeEventPublisherServiceImpl.getCustomChangeEventMessage(
        projectParams, "monitoredServiceId", "changeSourceId", customChangeWebhookPayload);
    CustomChangeEventDTO.Builder customChangeEventDTOBuilder =
        CustomChangeEventDTO.newBuilder()
            .setAccountId(projectParams.getAccountIdentifier())
            .setOrgIdentifier(projectParams.getOrgIdentifier())
            .setProjectIdentifier(projectParams.getProjectIdentifier())
            .setMonitoredServiceIdentifier("monitoredServiceId")
            .setChangeSourceIdentifier("changeSourceId")
            .setEventDetails(CustomChangeEventDetails.newBuilder()
                                 .setChangeEventDetailsLink("testLink")
                                 .setExternalLinkToEntity("externalLink")
                                 .setDescription("desc")
                                 .setName("name")
                                 .setWebhookUrl("webhookUrl")
                                 .build())
            .setStartTime(customChangeWebhookPayload.getStartTime())
            .setEndTime(customChangeWebhookPayload.getEndTime())
            .setUser(customChangeWebhookPayload.getUser());
    assertThat(message).isEqualTo(
        Message.newBuilder().setData(customChangeEventDTOBuilder.build().toByteString()).build());
  }
}
