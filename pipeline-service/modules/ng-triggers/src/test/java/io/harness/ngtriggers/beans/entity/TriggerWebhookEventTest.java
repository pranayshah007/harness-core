/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.entity;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;

import io.harness.category.element.UnitTests;

import java.io.IOException;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class TriggerWebhookEventTest {
  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Category(UnitTests.class)
  public void testBuilder() {
    // Create an instance using the builder
    TriggerWebhookEvent event = TriggerWebhookEvent.builder()
                                    .accountId("acc_id")
                                    .uuid("uuid")
                                    .headers(new ArrayList<>())
                                    .pipelineIdentifier("pipeline_id")
                                    .triggerIdentifier("trigger_id")
                                    .accountId("acc_id")
                                    .orgIdentifier("org_id")
                                    .projectIdentifier("proj_id")
                                    .sourceRepoType("repo")
                                    .isSubscriptionConfirmation(false)
                                    .nextIteration(1L)
                                    .build();

    assertEquals("acc_id", event.getAccountId());
    assertEquals("uuid", event.getUuid());
    assertEquals("pipeline_id", event.getPipelineIdentifier());
    assertEquals("org_id", event.getOrgIdentifier());

    assertEquals("trigger_id", event.getTriggerIdentifier());
    assertEquals("proj_id", event.getProjectIdentifier());
    assertFalse(event.isSubscriptionConfirmation());
  }
}
