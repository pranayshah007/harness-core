/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.license;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.license.CeLicenseInfo.CE_TRIAL_GRACE_PERIOD_DAYS;
import static io.harness.rule.OwnerRule.HANTANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.service.intfc.AccountService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CE)
public class CeLicenseExpiryHandlerTest extends CategoryTest {
  private Account account;

  @Mock AccountService accountService;
  @InjectMocks CeLicenseExpiryHandler ceLicenseExpiryHandler;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Captor ArgumentCaptor<Account> accountArgumentCaptor;

  @Before
  public void setUp() {
    CeLicenseInfo ceLicenseInfo = CeLicenseInfo.builder().expiryTime(1L).build();
    account = Account.Builder.anAccount().withCloudCostEnabled(Boolean.TRUE).withCeLicenseInfo(ceLicenseInfo).build();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldDisableCeAfterGracePeriod() {
    CeLicenseInfo ceLicenseInfo =
        CeLicenseInfo.builder()
            .expiryTime(Instant.now().minus(CE_TRIAL_GRACE_PERIOD_DAYS + 1, ChronoUnit.DAYS).toEpochMilli())
            .build();
    account = Account.Builder.anAccount().withCloudCostEnabled(Boolean.TRUE).withCeLicenseInfo(ceLicenseInfo).build();

    ceLicenseExpiryHandler.handle(account);
    verify(accountService).update(accountArgumentCaptor.capture());
    assertThat(accountArgumentCaptor.getValue()).isEqualToIgnoringGivenFields(account, "cloudCostEnabled");
    assertThat(accountArgumentCaptor.getValue().isCloudCostEnabled()).isFalse();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldNotDisableCeDuringGracePeriod() {
    CeLicenseInfo ceLicenseInfo =
        CeLicenseInfo.builder().expiryTime(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli()).build();
    account = Account.Builder.anAccount().withCloudCostEnabled(Boolean.TRUE).withCeLicenseInfo(ceLicenseInfo).build();

    ceLicenseExpiryHandler.handle(account);
    verifyZeroInteractions(accountService);
  }
}
