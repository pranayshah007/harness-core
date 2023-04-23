/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.datahandler.services;

import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static software.wings.beans.AccountStatus.MARKED_FOR_DELETION;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.service.intfc.AccountService;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AdminAccountServiceImplTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";

  @Mock AccountService accountService;
  @InjectMocks AdminAccountServiceImpl adminAccountService;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldEnableOrDisableCeK8sEventCollection() {
    adminAccountService.enableOrDisableCeK8sEventCollection(accountId, true);
    verify(accountService).updateCeAutoCollectK8sEvents(eq(accountId), eq(true));
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void deleteAccountShouldMarkTheAccountStatusDeleted() {
    adminAccountService.delete(accountId);
    verify(accountService).updateAccountStatus(eq(accountId), eq(MARKED_FOR_DELETION));
  }
}
