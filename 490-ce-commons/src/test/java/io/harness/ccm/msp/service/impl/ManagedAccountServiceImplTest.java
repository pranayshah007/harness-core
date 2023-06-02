/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.msp.service.impl;

import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.msp.dao.ManagedAccountDao;
import io.harness.ccm.msp.dto.ManagedAccount;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ManagedAccountServiceImplTest extends CategoryTest {
  @Mock private ManagedAccountDao managedAccountDao;
  @InjectMocks @Inject private ManagedAccountServiceImpl managedAccountService;

  private static final String MANAGED_ACCOUNT_ID = "account_id";
  private static final String MANAGED_ACCOUNT_NAME = "account_name";
  private static final String UPDATED_MANAGED_ACCOUNT_NAME = "updated_account_name";
  private static final String MSP_ACCOUNT_ID = "msp_account_id";
  private static final String UUID = "uuid";

  @Before
  public void setUp() throws Exception {
    when(managedAccountDao.save(any())).thenReturn(UUID);
    when(managedAccountDao.getDetailsForAccount(MSP_ACCOUNT_ID, MANAGED_ACCOUNT_ID))
        .thenReturn(getDummyManagedAccount());
    when(managedAccountDao.list(MSP_ACCOUNT_ID)).thenReturn(Collections.singletonList(getDummyManagedAccount()));
    when(managedAccountDao.update(getUpdatedDummyManagedAccount())).thenReturn(getUpdatedDummyManagedAccount());
    when(managedAccountDao.delete(MANAGED_ACCOUNT_ID)).thenReturn(true);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testSave() {
    String uuid = managedAccountService.save(getDummyManagedAccount());
    assertThat(uuid).isEqualTo(UUID);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetPerManagedAccountId() {
    ManagedAccount managedAccount = managedAccountService.get(MSP_ACCOUNT_ID, MANAGED_ACCOUNT_ID);
    assertThat(managedAccount.getUuid()).isEqualTo(UUID);
    assertThat(managedAccount.getAccountId()).isEqualTo(MANAGED_ACCOUNT_ID);
    assertThat(managedAccount.getMspAccountId()).isEqualTo(MSP_ACCOUNT_ID);
    assertThat(managedAccount.getAccountName()).isEqualTo(MANAGED_ACCOUNT_NAME);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testList() {
    List<ManagedAccount> managedAccounts = managedAccountService.list(MSP_ACCOUNT_ID);
    assertThat(managedAccounts.size()).isEqualTo(1);
    ManagedAccount managedAccount = managedAccounts.get(0);
    assertThat(managedAccount.getUuid()).isEqualTo(UUID);
    assertThat(managedAccount.getAccountId()).isEqualTo(MANAGED_ACCOUNT_ID);
    assertThat(managedAccount.getMspAccountId()).isEqualTo(MSP_ACCOUNT_ID);
    assertThat(managedAccount.getAccountName()).isEqualTo(MANAGED_ACCOUNT_NAME);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testUpdate() {
    ManagedAccount managedAccount = managedAccountService.update(getUpdatedDummyManagedAccount());
    assertThat(managedAccount.getUuid()).isEqualTo(UUID);
    assertThat(managedAccount.getAccountId()).isEqualTo(MANAGED_ACCOUNT_ID);
    assertThat(managedAccount.getMspAccountId()).isEqualTo(MSP_ACCOUNT_ID);
    assertThat(managedAccount.getAccountName()).isEqualTo(UPDATED_MANAGED_ACCOUNT_NAME);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testDelete() {
    Boolean deleted = managedAccountService.delete(MANAGED_ACCOUNT_ID);
    assertThat(deleted).isEqualTo(true);
  }

  private ManagedAccount getDummyManagedAccount() {
    return ManagedAccount.builder()
        .uuid(UUID)
        .accountId(MANAGED_ACCOUNT_ID)
        .accountName(MANAGED_ACCOUNT_NAME)
        .mspAccountId(MSP_ACCOUNT_ID)
        .build();
  }

  private ManagedAccount getUpdatedDummyManagedAccount() {
    return ManagedAccount.builder()
        .uuid(UUID)
        .accountId(MANAGED_ACCOUNT_ID)
        .accountName(UPDATED_MANAGED_ACCOUNT_NAME)
        .mspAccountId(MSP_ACCOUNT_ID)
        .build();
  }
}
