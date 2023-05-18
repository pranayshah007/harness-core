/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.config;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.HANTANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.billing.GcpServiceAccountServiceImpl;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.service.intfc.AccountService;

import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CE)
public class CEGcpServiceAccountServiceImplTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";
  private String accountName = "ACCOUNT_NAME";
  private Account account;
  private String serviceAccountId = "harness-ce-accountname-accou"; // truncation is expected.
  private GcpServiceAccount gcpServiceAccount;

  @Mock AccountService accountService;
  @Mock GcpServiceAccountDao gcpServiceAccountDao;
  @Mock GcpServiceAccountServiceImpl gcpServiceAccountService;
  @InjectMocks CEGcpServiceAccountServiceImpl ceGcpServiceAccountService;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() {
    account = new Account();
    account.setUuid(accountId);
    account.setAccountName(accountName);
    when(accountService.get(eq(accountId))).thenReturn(account);

    gcpServiceAccount = GcpServiceAccount.builder().build();
    when(gcpServiceAccountDao.getByAccountId(eq(accountId))).thenReturn(gcpServiceAccount);
    when(gcpServiceAccountDao.getByServiceAccountId(eq(serviceAccountId))).thenReturn(gcpServiceAccount);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldGetDefaultServiceAccountIfAlreadyCreate() throws IOException {
    GcpServiceAccount gcpServiceAccount = ceGcpServiceAccountService.getDefaultServiceAccount(accountId);
    assertThat(gcpServiceAccount).isEqualToComparingFieldByField(this.gcpServiceAccount);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldGetDefaultServiceAccountIfNotYetCreated() throws IOException {
    when(gcpServiceAccountDao.getByAccountId(eq(accountId))).thenReturn(null).thenReturn(gcpServiceAccount);
    GcpServiceAccount gcpServiceAccount = ceGcpServiceAccountService.getDefaultServiceAccount(accountId);
    assertThat(gcpServiceAccount).isEqualToComparingFieldByField(this.gcpServiceAccount);
  }
}
