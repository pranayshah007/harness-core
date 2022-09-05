/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.rule.OwnerRule.MEENAKSHI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.accountsetting.services.NGAccountSettingService;
import io.harness.ng.core.services.OrganizationService;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class NGAccountSettingsMigrationTest extends NgManagerTestBase {
  @Mock OrganizationService organizationService;
  @Mock NGAccountSettingService accountSettingService;
  @InjectMocks NGAccountSettingsMigration accountSettingsMigration;

  String accountId1 = "accountId1";
  String accountId2 = "accountId2";
  String accountId3 = "accountId3";
  ArrayList<String> accountIds = new ArrayList<>(Arrays.asList(accountId1, accountId2, accountId3));

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testMigrateAccountSettingsForExistingAccounts() {
    when(organizationService.getDistinctAccounts()).thenReturn(accountIds);
    accountSettingsMigration.migrate();
    verify(accountSettingService, times(3)).setUpDefaultAccountSettings(any());
  }
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testMigrateAccountSettingsForNoAccounts() {
    when(organizationService.getDistinctAccounts()).thenReturn(new ArrayList<>());
    accountSettingsMigration.migrate();
    verify(accountSettingService, times(0)).setUpDefaultAccountSettings(any());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testMigrateAccountSettingsForNoAccounts_withNull() {
    when(organizationService.getDistinctAccounts()).thenReturn(null);
    accountSettingsMigration.migrate();
    verify(accountSettingService, times(0)).setUpDefaultAccountSettings(any());
  }
}
