/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.ng.core.common.beans.Generation.CG;
import static io.harness.ng.core.common.beans.Generation.NG;
import static io.harness.ng.core.common.beans.UserSource.MANUAL;
import static io.harness.ng.core.user.NGRemoveUserFilter.ACCOUNT_LAST_ADMIN_CHECK;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.SAHIBA;
import static io.harness.rule.OwnerRule.SHASHANK;

import static software.wings.beans.Account.Builder.anAccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.common.beans.Generation;
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
  @Mock private FeatureFlagService featureFlagService;

  @Mock AccountService accountService;
  @Inject private WingsPersistence wingsPersistence;
  private static String ACCOUNT_ID = "ACCOUNT_ID";
  private static String ACCOUNT_ID_2 = "ACCOUNT_ID_2";

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testIfUserPartOfDeletedAccount() {
    Account account = getAccountWithUUID(ACCOUNT_ID);
    User user = getUserWithAccount(account);
    boolean result = userServiceHelper.isUserPartOfDeletedAccount(user, ACCOUNT_ID);
    assertThat(result).isEqualTo(true);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testIfUserPartOfPendingDeletedAccount() {
    Account account = getAccountWithUUID(ACCOUNT_ID);
    User user = User.Builder.anUser()
                    .uuid(UUIDGenerator.generateUuid())
                    .pendingAccounts(Collections.singletonList(account))
                    .email("abc@harness.io")
                    .name("abc")
                    .build();
    wingsPersistence.save(user);
    boolean result = userServiceHelper.isUserPartOfDeletedAccount(user, ACCOUNT_ID);
    assertThat(result).isEqualTo(true);
  }

  @Test
  @Owner(developers = {BOOPESH, SHASHANK})
  @Category(UnitTests.class)
  public void testIsUserActiveInNG() {
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    // With FF On
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(true);
    Account account = getAccountWithUUID(ACCOUNT_ID);
    User user = getUserWithAccount(account);
    boolean result = userServiceHelper.isUserActiveInNG(user, ACCOUNT_ID);
    assertThat(result).isEqualTo(true);
    // With FF Off
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(false);
    result = userServiceHelper.isUserActiveInNG(user, ACCOUNT_ID);
    assertThat(result).isEqualTo(false);

    mockRestStatic.close();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testCGUserIsActiveInNGWithFFOn() {
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(false);
    Account account = getAccountWithUUID(ACCOUNT_ID);
    User user = getUserWithAccount(account);

    when(featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, ACCOUNT_ID)).thenReturn(true);
    userServiceHelper.populateAccountToUserMapping(user, account.getUuid(), CG, MANUAL);
    boolean result = userServiceHelper.isUserActiveInNG(user, ACCOUNT_ID);
    assertThat(result).isEqualTo(false);
    mockRestStatic.close();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testCGUserIsActiveInNGWithFFOff() {
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(true);
    Account account = getAccountWithUUID(ACCOUNT_ID);
    User user = getUserWithAccount(account);

    when(featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, ACCOUNT_ID)).thenReturn(false);
    boolean result = userServiceHelper.isUserActiveInNG(user, ACCOUNT_ID);
    assertThat(result).isEqualTo(true); // Because when there is no data, older method will be used
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(false);
    result = userServiceHelper.isUserActiveInNG(user, ACCOUNT_ID);
    assertThat(result).isEqualTo(false);

    mockRestStatic.close();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testNGUserIsActiveInNGWithFFOn() {
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(true);
    Account account = getAccountWithUUID(ACCOUNT_ID);
    User user = getUserWithAccount(account);

    when(featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, ACCOUNT_ID)).thenReturn(true);
    userServiceHelper.populateAccountToUserMapping(user, account.getUuid(), Generation.NG, MANUAL);
    boolean result = userServiceHelper.isUserActiveInNG(user, ACCOUNT_ID);
    assertThat(result).isEqualTo(true);

    mockRestStatic.close();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testNGUserIsActiveInNGWithFFOff() {
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(false);
    Account account = getAccountWithUUID(ACCOUNT_ID);
    User user = getUserWithAccount(account);

    when(featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, ACCOUNT_ID)).thenReturn(false);
    boolean result = userServiceHelper.isUserActiveInNG(user, ACCOUNT_ID);
    assertThat(result).isEqualTo(false);

    mockRestStatic.close();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testIsUserProvisionedInThisGenerationInThisAccountWithNoData() {
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(true);
    Account account = getAccountWithUUID(ACCOUNT_ID);
    User user = getUserWithAccount(account);

    when(featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, ACCOUNT_ID)).thenReturn(true);
    boolean result = userServiceHelper.isUserProvisionedInThisGenerationInThisAccount(user, account.getUuid(), CG);
    assertThat(result).isEqualTo(false);

    mockRestStatic.close();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testIsUserProvisionedWithDifferentAccount() {
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(true);
    Account account = getAccountWithUUID(ACCOUNT_ID);
    User user = getUserWithAccount(account);

    when(featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, ACCOUNT_ID_2)).thenReturn(true);
    userServiceHelper.populateAccountToUserMapping(user, ACCOUNT_ID_2, CG, MANUAL);
    boolean result = userServiceHelper.isUserProvisionedInThisGenerationInThisAccount(user, account.getUuid(), CG);
    assertThat(result).isEqualTo(false);

    mockRestStatic.close();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testIsUserProvisionedWithCorrectAccountInCG() {
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(true);
    Account account = getAccountWithUUID(ACCOUNT_ID);
    User user = getUserWithAccount(account);

    when(featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, ACCOUNT_ID)).thenReturn(true);
    userServiceHelper.populateAccountToUserMapping(user, ACCOUNT_ID, CG, MANUAL);
    boolean result = userServiceHelper.isUserProvisionedInThisGenerationInThisAccount(user, account.getUuid(), CG);
    assertThat(result).isEqualTo(true);
    result = userServiceHelper.isUserProvisionedInThisGenerationInThisAccount(user, account.getUuid(), NG);
    assertThat(result).isEqualTo(false);

    mockRestStatic.close();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testIsUserProvisionedWithCorrectAccountInCGButFFIsOff() {
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(true);
    Account account = getAccountWithUUID(ACCOUNT_ID);
    User user = getUserWithAccount(account);

    when(featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, ACCOUNT_ID)).thenReturn(false);
    userServiceHelper.populateAccountToUserMapping(user, ACCOUNT_ID, NG, MANUAL);
    boolean result = userServiceHelper.isUserProvisionedInThisGenerationInThisAccount(user, account.getUuid(), CG);
    assertThat(result).isEqualTo(false);

    mockRestStatic.close();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testIsUserProvisionedInBothGen() {
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(true);
    Account account = getAccountWithUUID(ACCOUNT_ID);
    User user = getUserWithAccount(account);

    when(featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, ACCOUNT_ID)).thenReturn(true);
    userServiceHelper.populateAccountToUserMapping(user, ACCOUNT_ID, NG, MANUAL);
    userServiceHelper.populateAccountToUserMapping(user, ACCOUNT_ID, CG, MANUAL);
    boolean result = userServiceHelper.isUserProvisionedInThisGenerationInThisAccount(user, account.getUuid(), NG);
    assertThat(result).isEqualTo(true);
    result = userServiceHelper.isUserProvisionedInThisGenerationInThisAccount(user, account.getUuid(), CG);
    assertThat(result).isEqualTo(true);

    mockRestStatic.close();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testIsUserProvisionedInBothGenThenRemovedFromCG() {
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(true);
    Account account = getAccountWithUUID(ACCOUNT_ID);
    User user = getUserWithAccount(account);

    when(featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, ACCOUNT_ID)).thenReturn(true);
    userServiceHelper.populateAccountToUserMapping(user, ACCOUNT_ID, NG, MANUAL);
    userServiceHelper.populateAccountToUserMapping(user, ACCOUNT_ID, CG, MANUAL);
    user.getUserAccountLevelDataMap().get(ACCOUNT_ID).getUserProvisionedTo().remove(CG);
    boolean result = userServiceHelper.isUserProvisionedInThisGenerationInThisAccount(user, account.getUuid(), CG);
    assertThat(result).isEqualTo(false);
    result = userServiceHelper.isUserProvisionedInThisGenerationInThisAccount(user, account.getUuid(), NG);
    assertThat(result).isEqualTo(true);

    mockRestStatic.close();
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
    User user = User.Builder.anUser()
                    .uuid(UUIDGenerator.generateUuid())
                    .accounts(accountList)
                    .email("abc@harness.io")
                    .name("abc")
                    .build();
    wingsPersistence.save(user);
    List<Account> updatedAccounts = userServiceHelper.updatedActiveAccounts(user, ACCOUNT_ID);
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
    User user = getUserWithAccount(account);
    user.setPendingAccounts(Collections.singletonList(account2));

    List<Account> updatedPendingAccounts = userServiceHelper.updatedPendingAccount(user, ACCOUNT_ID_2);
    assertThat(updatedPendingAccounts).doesNotContain(account2);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testDeleteUserFromNG() {
    Account account = getAccountWithUUID(ACCOUNT_ID);
    User user = getUserWithAccount(account);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(false);
    userServiceHelper.deleteUserFromNG(user.getUuid(), ACCOUNT_ID, ACCOUNT_LAST_ADMIN_CHECK);
    mockRestStatic.close();
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testDeleteUserMetaDataFromNG() {
    Account account = getAccountWithUUID(ACCOUNT_ID);
    User user = getUserWithAccount(account);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(false);
    boolean result = userServiceHelper.deleteUserMetadata(user.getUuid());
    assertThat(result).isEqualTo(false);
    mockRestStatic.close();
  }

  private User getUserWithAccount(Account account) {
    User user = User.Builder.anUser()
                    .uuid(UUIDGenerator.generateUuid())
                    .accounts(Collections.singletonList(account))
                    .email("abc@harness.io")
                    .name("abc")
                    .build();
    wingsPersistence.save(user);
    return user;
  }

  private Account getAccountWithUUID(String uuid) {
    Account account = anAccount().withUuid(uuid).build();
    wingsPersistence.save(account);
    return account;
  }
}