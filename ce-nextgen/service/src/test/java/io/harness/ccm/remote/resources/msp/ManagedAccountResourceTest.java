/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.msp;

import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.ccm.msp.dto.ManagedAccount;
import io.harness.ccm.msp.service.intf.ManagedAccountService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
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
public class ManagedAccountResourceTest {
  @Mock private ManagedAccountService managedAccountService;
  @InjectMocks @Inject private ManagedAccountResource managedAccountResource;

  private static final String MANAGED_ACCOUNT_ID = "account_id";
  private static final String MANAGED_ACCOUNT_NAME = "account_name";
  private static final String UPDATED_MANAGED_ACCOUNT_NAME = "updated_account_name";
  private static final String MSP_ACCOUNT_ID = "msp_account_id";
  private static final String UUID = "uuid";

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    when(managedAccountService.save(getDummyManagedAccount())).thenReturn(UUID);
    when(managedAccountService.get(MSP_ACCOUNT_ID, MANAGED_ACCOUNT_ID)).thenReturn(getDummyManagedAccount());
    when(managedAccountService.list(MSP_ACCOUNT_ID)).thenReturn(Collections.singletonList(getDummyManagedAccount()));
    when(managedAccountService.update(getUpdatedDummyManagedAccount())).thenReturn(getUpdatedDummyManagedAccount());
    when(managedAccountService.delete(MANAGED_ACCOUNT_ID)).thenReturn(true);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testSave() {
    ResponseDTO<String> response = managedAccountResource.save(MSP_ACCOUNT_ID, getDummyManagedAccount());
    assertThat(response.getData()).isEqualTo(UUID);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGet() {
    ResponseDTO<ManagedAccount> response = managedAccountResource.get(MSP_ACCOUNT_ID, MANAGED_ACCOUNT_ID);
    assertThat(response.getData()).isEqualTo(getDummyManagedAccount());
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testList() {
    ResponseDTO<List<ManagedAccount>> response = managedAccountResource.list(MSP_ACCOUNT_ID);
    assertThat(response.getData()).isEqualTo(Collections.singletonList(getDummyManagedAccount()));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testUpdate() {
    ResponseDTO<ManagedAccount> response =
        managedAccountResource.update(MSP_ACCOUNT_ID, getUpdatedDummyManagedAccount());
    assertThat(response.getData()).isEqualTo(getUpdatedDummyManagedAccount());
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testDelete() {
    ResponseDTO<Boolean> response = managedAccountResource.delete(MSP_ACCOUNT_ID, MANAGED_ACCOUNT_ID);
    assertThat(response.getData()).isEqualTo(true);
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
