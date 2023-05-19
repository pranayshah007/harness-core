/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.rule.OwnerRule.HANTANG;

import static java.lang.String.format;
import static javax.ws.rs.client.Entity.entity;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.communication.CESlackWebhookService;
import io.harness.ccm.communication.entities.CESlackWebhook;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.utils.ResourceTestRule;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CESlackWebhookResourceTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";
  private CESlackWebhook ceSlackWebhook;
  private boolean sendCostReport = true;
  private boolean sendAnomalyAlerts = true;

  private static CESlackWebhookService ceSlackWebhookService = mock(CESlackWebhookService.class);

  @ClassRule
  public static ResourceTestRule RESOURCES =
      ResourceTestRule.builder().instance(new CESlackWebhookResource(ceSlackWebhookService)).build();
  @Before
  public void setUp() {
    ceSlackWebhook = CESlackWebhook.builder()
                         .accountId(accountId)
                         .webhookUrl("WEBHOOK_URL")
                         .sendCostReport(sendCostReport)
                         .sendAnomalyAlerts(sendAnomalyAlerts)
                         .build();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testGet() {
    RESOURCES.client()
        .target(format("/ceSlackWebhooks/?accountId=%s", accountId))
        .request()
        .get(new GenericType<RestResponse<CESlackWebhook>>() {});
    verify(ceSlackWebhookService).getByAccountId(eq(accountId));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testSave() {
    RESOURCES.client()
        .target(format("/ceSlackWebhooks/?accountId=%s", accountId))
        .request()
        .post(entity(ceSlackWebhook, MediaType.APPLICATION_JSON), new GenericType<RestResponse<CESlackWebhook>>() {});
    verify(ceSlackWebhookService).upsert(eq(ceSlackWebhook));
  }
}
