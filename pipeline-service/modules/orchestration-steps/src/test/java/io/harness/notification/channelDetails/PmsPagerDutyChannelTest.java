/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.channelDetails;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.channeldetails.PagerDutyChannel;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsPagerDutyChannelTest extends CategoryTest {
  List<String> userGroups = new ArrayList<>();
  String integrationKey = "key1";
  PmsPagerDutyChannel pmsPagerDutyChannel;
  @Before
  public void setUp() {
    userGroups.add("user");
    userGroups.add("org.user");
    pmsPagerDutyChannel = spy(new PmsPagerDutyChannel(userGroups, integrationKey));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testToNotificationChannel() {
    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";
    String templateId = "temolateId";
    Map<String, String> templateData = new HashMap<>();
    NotificationChannel notificationChannel = pmsPagerDutyChannel.toNotificationChannel(accountId, orgId, projectId,
        templateId, templateData, Ambiance.newBuilder().setExpressionFunctorToken(123L).build());
    assertEquals(notificationChannel.getAccountId(), accountId);
    assertEquals(((PagerDutyChannel) notificationChannel).getIntegrationKeys().get(0), integrationKey);
    assertEquals(notificationChannel.getUserGroups().get(0).getOrgIdentifier(), orgId);
    assertEquals(notificationChannel.getUserGroups().get(0).getProjectIdentifier(), projectId);

    assertEquals(notificationChannel.getUserGroups().get(1).getOrgIdentifier(), orgId);
    assertEquals(notificationChannel.getUserGroups().get(1).getProjectIdentifier(), "");
  }
}
