/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ServiceNowException;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class ServiceNowUtilsTest extends CategoryTest {
  public static final String TARGET_TABLE_NAME = "incident";
  public static final String TICKET_SYS_ID = "a639e9ccdb4651909e7c2a5913961911";
  public static final String TICKET_LINK =
      "https://harness.service-now.com/nav_to.do?uri=/incident.do?sys_id=a639e9ccdb4651909e7c2a5913961911";
  public static final String BASE_URL = "https://harness.service-now.com/";
  public static final String BLANK_STRING = "    ";

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testPrepareTicketUrlFromTicketIdV() {
    assertThat(ServiceNowUtils.prepareTicketUrlFromTicketIdV2(BASE_URL, TICKET_SYS_ID, TARGET_TABLE_NAME))
        .isEqualTo(TICKET_LINK);
    assertThat(ServiceNowUtils.prepareTicketUrlFromTicketIdV2(BASE_URL, BLANK_STRING, TARGET_TABLE_NAME))
        .isEqualTo(null);
    assertThat(ServiceNowUtils.prepareTicketUrlFromTicketIdV2(BASE_URL, TICKET_SYS_ID, BLANK_STRING)).isEqualTo(null);
    assertThat(ServiceNowUtils.prepareTicketUrlFromTicketIdV2(BASE_URL, BLANK_STRING, BLANK_STRING)).isEqualTo(null);

    try {
      ServiceNowUtils.prepareTicketUrlFromTicketIdV2(BLANK_STRING, TICKET_SYS_ID, TARGET_TABLE_NAME);
      fail("Expected failure as mal informed base url");
    } catch (ServiceNowException ex) {
      assertThat(ex.getMessage()).isEqualTo(String.format("Invalid serviceNow base url: %s", BLANK_STRING));
    }
  }
}
