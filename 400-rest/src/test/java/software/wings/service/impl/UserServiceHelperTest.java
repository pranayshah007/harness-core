/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.ng.core.user.NGRemoveUserFilter.ACCOUNT_LAST_ADMIN_CHECK;
import static io.harness.rule.OwnerRule.BOOPESH;

import static software.wings.beans.Account.Builder.anAccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.PL)
public class UserServiceHelperTest extends WingsBaseTest {
  @Inject @InjectMocks UserServiceHelper userServiceHelper;

  @Mock AccountService accountService;
  @Inject private WingsPersistence wingsPersistence;
  private static String ACCOUNT_ID = "ACCOUNT_ID";
  private static String ACCOUNT_ID_2 = "ACCOUNT_ID_2";

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testIfUserPartOfDeletedAccount() {
    Account account = anAccount().withUuid(ACCOUNT_ID).build();
    wingsPersistence.save(account);
    User user1 = User.Builder.anUser()
                     .uuid(UUIDGenerator.generateUuid())
                     .accounts(Collections.singletonList(account))
                     .email("abc@harness.io")
                     .name("abc")
                     .build();
    wingsPersistence.save(user1);
    boolean result = userServiceHelper.isUserPartOfDeletedAccount(user1, ACCOUNT_ID);
    assertThat(result).isEqualTo(true);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testIsUserActiveInNG() {
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(true);
    Account account = anAccount().withUuid(ACCOUNT_ID).build();
    wingsPersistence.save(account);
    User user1 = User.Builder.anUser()
                     .uuid(UUIDGenerator.generateUuid())
                     .accounts(Collections.singletonList(account))
                     .email("aBc@harness.io")
                     .name("pqr")
                     .build();
    wingsPersistence.save(user1);
    boolean result = userServiceHelper.isUserActiveInNG(user1, ACCOUNT_ID);
    assertThat(result).isEqualTo(true);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testUpdatedActiveAccounts() {
    Account account = anAccount().withUuid(ACCOUNT_ID).build();
    Account account2 = anAccount().withUuid(ACCOUNT_ID_2).build();
    List<Account> accountList = new ArrayList<>();
    accountList.add(account);
    accountList.add(account2);
    wingsPersistence.save(account);
    User user1 = User.Builder.anUser()
                     .uuid(UUIDGenerator.generateUuid())
                     .accounts(accountList)
                     .email("abc@harness.io")
                     .name("abc")
                     .build();
    wingsPersistence.save(user1);
    List<Account> updatedAccounts = userServiceHelper.updatedActiveAccounts(user1, ACCOUNT_ID);
    assertThat(updatedAccounts).doesNotContain(account);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testUpdatedActivePendingAccounts() {
    Account account = anAccount().withUuid(ACCOUNT_ID).build();
    Account account2 = anAccount().withUuid(ACCOUNT_ID_2).build();
    List<Account> accountList = new ArrayList<>();
    accountList.add(account2);
    wingsPersistence.save(account);
    User user1 = User.Builder.anUser()
                     .uuid(UUIDGenerator.generateUuid())
                     .pendingAccounts(Collections.singletonList(account2))
                     .accounts(Collections.singletonList(account))
                     .email("abc@harness.io")
                     .name("abc")
                     .build();
    wingsPersistence.save(user1);
    List<Account> updatedPendingAccounts = userServiceHelper.updatedPendingAccount(user1, ACCOUNT_ID_2);
    assertThat(updatedPendingAccounts).doesNotContain(account2);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testDeleteUserFromNG() {
    Account account = anAccount().withUuid(ACCOUNT_ID).build();
    wingsPersistence.save(account);
    User user1 = User.Builder.anUser()
                     .uuid(UUIDGenerator.generateUuid())
                     .accounts(Collections.singletonList(account))
                     .email("abc@harness.io")
                     .name("abc")
                     .build();
    wingsPersistence.save(user1);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(false);
    userServiceHelper.deleteUserFromNG(user1.getUuid(), ACCOUNT_ID, ACCOUNT_LAST_ADMIN_CHECK);
  }
}