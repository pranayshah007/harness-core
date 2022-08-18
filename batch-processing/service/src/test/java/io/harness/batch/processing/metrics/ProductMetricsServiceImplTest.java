/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.metrics;

import static io.harness.rule.OwnerRule.HANTANG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.batch.processing.BatchProcessingTestBase;
import io.harness.category.element.UnitTests;
import io.harness.ccm.config.GcpBillingAccount;
import io.harness.ccm.config.GcpBillingAccountDao;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProductMetricsServiceImplTest extends BatchProcessingTestBase {
  private String accountId = "ACCOUNT_ID";
  private String gcpOrganizationUuid = "1";

  @Inject ProductMetricsServiceImpl productMetricsService;
  @Inject GcpBillingAccountDao gcpBillingAccountDao;

  @Mock TimeScaleDBService timeScaleDBService;

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldCountGcpBillingAccounts() {
    GcpBillingAccount gcpBillingAccount =
        GcpBillingAccount.builder().accountId(accountId).organizationSettingId(gcpOrganizationUuid).build();
    gcpBillingAccountDao.upsert(gcpBillingAccount);
    long count = productMetricsService.countGcpBillingAccounts(accountId);
    assertThat(count).isEqualTo(1);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldCountAwsBillingAccounts() {
    long count = productMetricsService.countAwsBillingAccounts(accountId);
    assertThat(count).isEqualTo(0);
  }
}
