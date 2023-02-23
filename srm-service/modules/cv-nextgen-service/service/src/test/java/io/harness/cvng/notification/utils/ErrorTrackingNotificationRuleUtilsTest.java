/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils;

import static io.harness.rule.OwnerRule.JAMES_RICKS;

import static junit.framework.TestCase.assertEquals;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ErrorTrackingNotificationRuleUtilsTest {
  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void getSlackEventStringTest() {
    final ErrorTrackingNotificationRuleUtils.ErrorTrackingEvent event =
        ErrorTrackingNotificationRuleUtils.ErrorTrackingEvent.builder()
            .version("V1.0")
            .newCount("12")
            .url("http://www.invalidurl.com")
            .build();
    final String expectedEmailEvent =
        "<div style=\"margin-bottom: 16px\"><span>Events appeared on the deployment version <span style=\"font-weight: bold;\">V1.0</span></span><div style =\"margin-top: 4px;\"><span><a style=\"text-decoration: none; color: #0278D5;\" href=\"http://www.invalidurl.com\">New Events (12)</a></span></div></div>";
    assertEquals(expectedEmailEvent, event.toEmailString());
    final String expectedSlackEvent =
        "Events appeared on the deployment version *V1.0*\n<http://www.invalidurl.com|New Events (12)>";
    assertEquals(expectedSlackEvent, event.toSlackString());
  }
}
