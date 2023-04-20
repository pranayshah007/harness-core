/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.rule.OwnerRule.HANTANG;

import static java.lang.String.format;
import static javax.ws.rs.client.Entity.entity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.config.GcpBillingAccount;
import io.harness.ccm.config.GcpBillingAccountService;
import io.harness.ccm.setup.service.intfc.AWSAccountService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.utils.ResourceTestRule;

import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GcpBillingAccountResourceTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";
  private String billingAccountId = "BILLING_ACCOUNT_ID";
  private String organizationSettingId = "ORGANIZATION_SETTING_ID";
  private GcpBillingAccount gcpBillingAccount;

  private static GcpBillingAccountService gcpBillingAccountService = mock(GcpBillingAccountService.class);
  private static AWSAccountService awsAccountService = mock(AWSAccountService.class);
  @ClassRule
  public static ResourceTestRule RESOURCES =
      ResourceTestRule.builder()
          .instance(new GcpBillingAccountResource(gcpBillingAccountService, awsAccountService))
          .build();

  @Before
  public void setUp() {
    gcpBillingAccount = GcpBillingAccount.builder().accountId(accountId).build();
    when(gcpBillingAccountService.get(eq(billingAccountId))).thenReturn(gcpBillingAccount);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testSave() {
    RESOURCES.client()
        .target(format("/billing-accounts/?accountId=%s", accountId))
        .request()
        .post(entity(gcpBillingAccount, MediaType.APPLICATION_JSON),
            new GenericType<RestResponse<GcpBillingAccount>>() {});
    verify(gcpBillingAccountService).create(gcpBillingAccount);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testGet() {
    RESOURCES.client()
        .target(format("/billing-accounts/%s/?accountId=%s", billingAccountId, accountId))
        .request()
        .get(new GenericType<RestResponse<GcpBillingAccount>>() {});
    verify(gcpBillingAccountService).get(eq(billingAccountId));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testList() {
    RESOURCES.client()
        .target(format("/billing-accounts?accountId=%s&organizationSettingId=%s", accountId, organizationSettingId))
        .request()
        .get(new GenericType<RestResponse<List<GcpBillingAccount>>>() {});
    verify(gcpBillingAccountService).list(eq(accountId), eq(organizationSettingId));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testUpdate() {
    RESOURCES.client()
        .target(format("/billing-accounts/%s/?accountId=%s", billingAccountId, accountId))
        .request()
        .put(entity(gcpBillingAccount, MediaType.APPLICATION_JSON),
            new GenericType<RestResponse<GcpBillingAccount>>() {});
    verify(gcpBillingAccountService).update(eq(billingAccountId), isA(GcpBillingAccount.class));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testDelete() {
    RESOURCES.client()
        .target(format("/billing-accounts/%s/?accountId=%s", billingAccountId, accountId))
        .request()
        .delete();
    verify(gcpBillingAccountService).delete(eq(accountId), any(), eq(billingAccountId));
  }
}
