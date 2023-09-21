/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.generator;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.govern.Switch.unhandled;

import static software.wings.beans.Account.Builder;
import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.User.Builder.anUser;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.delegate.beans.DelegateToken;
import io.harness.delegate.beans.DelegateToken.DelegateTokenKeys;
import io.harness.exception.WingsException;
import io.harness.generator.LicenseGenerator.Licenses;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.limits.ActionType;
import io.harness.limits.configuration.LimitConfigurationService;
import io.harness.limits.impl.model.RateLimit;
import io.harness.limits.impl.model.StaticLimit;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;
import io.harness.testframework.framework.utils.TestUtils;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.beans.AccountType;
import software.wings.beans.LicenseInfo;
import software.wings.beans.Role;
import software.wings.beans.RoleType;
import software.wings.beans.User;
import software.wings.beans.account.AccountStatus;
import software.wings.beans.security.HarnessUserGroup;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.instance.licensing.InstanceLimitProvider;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.UpdateOperations;
import io.github.benas.randombeans.api.EnhancedRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class AccountGenerator {
  private static final String adminUserUuid = "lv0euRhKRCyiXWzS7pOg6g";
  private static final String adminUserName = "Admin";
  public static final String adminUserEmail = "admin@harness.io";
  private static final SecretName adminPassword = new SecretName("user_admin_password");

  private static final String readOnlyUserUuid = "nhLgdGgxS_iqa0KP5edC-w";
  private static final String readOnlyUserName = "readonlyuser";
  public static final String readOnlyEmail = "readonlyuser@harness.io";
  private static final SecretName readOnlyPassword = new SecretName("user_readonly_password");

  private static final String rbac1UserUuid = "BnTbQTIJS4SkadzYv0BcbA";
  private static final String rbac1UserName = "rbac1";
  private static final String rbac1Email = "rbac1@harness.io";
  private static final SecretName rbac1Password = new SecretName("user_rbac1_password");

  private static final String rbac2UserUuid = "19bYA-ooQZOTZQxf2N-VPA";
  private static final String rbac2UserName = "rbac2";
  private static final String rbac2Email = "rbac2@harness.io";
  private static final SecretName rbac2Password = new SecretName("user_rbac2_password");

  private static final String defaultUserUuid = "0osgWsTZRsSZ8RWfjLRkEg";
  private static final String defaultUserName = "default";
  private static final String defaultEmail = "default@harness.io";
  private static final SecretName defaultPassword = new SecretName("user_default_password");

  private static final String testUserUuid = "12345-123456789012-123";
  private static final String testUserName = "testadmin";
  private static final String testEmail = "testadmin@harness.io";
  private static final SecretName testPassword = new SecretName("user_default_password");

  private static final String default2faUserUuid = "ZqXNvYmURnO46PX7HwgEtQ";
  private static final String default2faUserName = "default2fa";
  private static final String default2faEmail = "default2fa@harness.io";
  private static final SecretName default2faPassword = new SecretName("user_default_password");

  @Inject AccountService accountService;
  @Inject AuthHandler authHandler;
  @Inject HarnessUserGroupService harnessUserGroupService;
  @Inject LicenseGenerator licenseGenerator;
  @Inject ScmSecret scmSecret;
  @Inject UserGroupService userGroupService;
  @Inject UserService userService;
  @Inject WingsPersistence wingsPersistence;
  @Inject private LimitConfigurationService limitConfigurationService;

  public static final String ACCOUNT_ID = "kmpySmUISimoRrJL6NL73w";

  public enum Accounts { GENERIC_TEST, HARNESS_TEST, RBAC_TEST }

  public Account ensurePredefined(Randomizer.Seed seed, Owners owners, Accounts predefined) {
    switch (predefined) {
      case GENERIC_TEST:
        return owners.obtainAccount(() -> ensureGenericTest());
      case HARNESS_TEST:
        return owners.obtainAccount(() -> ensureHarnessTest());
      case RBAC_TEST:
        return owners.obtainAccount(() -> ensureRbacTest());
      default:
        unhandled(predefined);
    }

    return null;
  }

  public Account exists(Account account) {
    return wingsPersistence.createQuery(Account.class).filter(AccountKeys.accountName, account.getAccountName()).get();
  }

  private Account ensureGenericTest() {
    Account account = ensureAccount(Builder.anAccount()
                                        .withUuid(ACCOUNT_ID)
                                        .withAccountName("Harness")
                                        .withCompanyName("Harness")
                                        .withDefaultExperience(DefaultExperience.CG)
                                        .withLicenseInfo(LicenseInfo.builder()
                                                             .accountType(AccountType.PAID)
                                                             .accountStatus(AccountStatus.ACTIVE)
                                                             .expiryTime(-1)
                                                             .build())
                                        .build());

    ensureUserGroup(account, UserGroup.DEFAULT_READ_ONLY_USER_GROUP_NAME);
    ensureDefaultUsers(account);
    return account;
  }

  /*
   * This function has to be cleaned up to provide only the
   * necessary entities. This gonna evolve over the time and hence
   * we are not reusing `ensureGenericTest`.
   */
  private Account ensureHarnessTest() {
    Account account =
        ensureAccount(getOrCreateAccount("1234567890123456789012", "Harness Test", "Harness", AccountType.PAID));
    account = ensureAccount(account);
    ensureTestUser(account);
    return account;
  }

  private Account ensureRbacTest() {
    Account account =
        ensureAccount(getOrCreateAccount("BAC4567890123456789012", "Rbac Test", "Harness", AccountType.PAID));
    ensureTestUser(account);
    return account;
  }

  // TODO: this needs serious refactoring
  private Account ensureAccount(Account account) {
    Account current = exists(account);
    if (current != null) {
      return current;
    }

    try {
      updateLicenseInfo(account);

      account = accountService.save(account, true);

      updateAccountKey("harness_account_secret", account);
      updateLimitConfiguration(account.getUuid());

    } catch (WingsException wEx) {
      log.error(wEx.getMessage());
    }

    return account;
  }

  private Account getOrCreateAccount(String accountId, String accountName, String companyName, String accountType) {
    Account account = anAccount().withAccountName(accountName).withCompanyName(companyName).build();

    account = exists(account);

    if (account == null) {
      log.info("Account does not exist with accountName = {}", accountName);
      account = Builder.anAccount()
                    .withUuid(accountId)
                    .withAccountName(accountName)
                    .withCompanyName(companyName)
                    .withDefaultExperience(DefaultExperience.CG)
                    .withLicenseInfo(LicenseInfo.builder()
                                         .accountType(accountType)
                                         .accountStatus(AccountStatus.ACTIVE)
                                         .licenseUnits(InstanceLimitProvider.defaults(accountType))
                                         .expiryTime(-1)
                                         .build())
                    .build();
    }

    return account;
  }

  public Account ensureAccount(String accountName, String companyName, String accountType) {
    Account account = getOrCreateAccount(TestUtils.generateRandomUUID(), accountName, companyName, accountType);
    if (exists(account) == null) {
      accountService.save(account, true);
      updateAccountKey("harness_account_secret", account);
    }

    return accountService.get(account.getUuid());
  }

  private void updateLicenseInfo(Account account) {
    final Seed seed = new Seed(0);
    licenseGenerator.ensurePredefined(seed, Licenses.TRIAL);

    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.PAID);
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setLicenseUnits(InstanceLimitProvider.defaults(AccountType.PAID));
    account.setLicenseInfo(licenseInfo);
  }

  private void updateAccountKey(String secretName, Account account) {
    String accountKey = scmSecret.decryptToString(new SecretName(secretName));

    // Update account key to make it work with delegate
    UpdateOperations<Account> accountUpdateOperations = wingsPersistence.createUpdateOperations(Account.class);
    accountUpdateOperations.set("accountKey", accountKey);
    wingsPersistence.update(
        wingsPersistence.createQuery(Account.class).filter(AccountKeys.accountName, account.getAccountName()),
        accountUpdateOperations);

    // Update account key value in delegate tokens to make it work with delegates
    UpdateOperations<DelegateToken> tokenUpdateOperations =
        wingsPersistence.createUpdateOperations(DelegateToken.class);
    tokenUpdateOperations.set(DelegateTokenKeys.value, accountKey);
    wingsPersistence.update(wingsPersistence.createQuery(DelegateToken.class)
                                .filter(DelegateTokenKeys.accountId, account.getUuid())
                                .filter(DelegateTokenKeys.name, "default"),
        tokenUpdateOperations);
  }

  private void updateLimitConfiguration(String accountId) {
    limitConfigurationService.configure(accountId, ActionType.CREATE_PIPELINE, new StaticLimit(1000));
    limitConfigurationService.configure(accountId, ActionType.CREATE_USER, new StaticLimit(1000));
    limitConfigurationService.configure(accountId, ActionType.CREATE_APPLICATION, new StaticLimit(1000));
    limitConfigurationService.configure(accountId, ActionType.DEPLOY, new RateLimit(1000, 1, TimeUnit.HOURS));
    limitConfigurationService.configure(
        Account.GLOBAL_ACCOUNT_ID, ActionType.LOGIN_REQUEST_TASK, new RateLimit(300, 1, TimeUnit.MINUTES));
    limitConfigurationService.configure(Account.GLOBAL_ACCOUNT_ID, ActionType.MAX_QPM_PER_MANAGER, new StaticLimit(50));
  }

  private Account ensureDefaultUsers(Account account) {
    UpdateOperations<Role> roleUpdateOperations = wingsPersistence.createUpdateOperations(Role.class);
    roleUpdateOperations.set("accountId", ACCOUNT_ID);
    wingsPersistence.update(
        wingsPersistence.createQuery(Role.class).filter(Role.ACCOUNT_ID_KEY2, account.getUuid()), roleUpdateOperations);

    User adminUser =
        ensureUser(adminUserUuid, adminUserName, adminUserEmail, scmSecret.decryptToCharArray(adminPassword), account);
    ensureUser(defaultUserUuid, defaultUserName, defaultEmail, scmSecret.decryptToCharArray(defaultPassword), account);
    User default2faUser = ensureUser(default2faUserUuid, default2faUserName, default2faEmail,
        scmSecret.decryptToCharArray(default2faPassword), account);
    User readOnlyUser = ensureUser(
        readOnlyUserUuid, readOnlyUserName, readOnlyEmail, scmSecret.decryptToCharArray(readOnlyPassword), account);
    ensureUser(rbac1UserUuid, rbac1UserName, rbac1Email, scmSecret.decryptToCharArray(rbac1Password), account);
    ensureUser(rbac2UserUuid, rbac2UserName, rbac2Email, scmSecret.decryptToCharArray(rbac2Password), account);
    ArrayList<User> users = new ArrayList<>();
    users.add(adminUser);
    users.add(default2faUser);
    addUsersToUserGroup(users, account.getUuid(), UserGroup.DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME);
    addUserToUserGroup(readOnlyUser, account.getUuid(), UserGroup.DEFAULT_READ_ONLY_USER_GROUP_NAME);
    addUserToHarnessUserGroup(account.getUuid(), adminUser);

    return account;
  }

  private Account ensureUserGroup(Account account, String userGroupName) {
    if (userGroupService.fetchUserGroupByName(account.getUuid(), userGroupName) == null) {
      User readOnlyUser = ensureUser(
          readOnlyUserUuid, readOnlyUserName, readOnlyEmail, scmSecret.decryptToCharArray(readOnlyPassword), account);
      UserGroup readOnlyUserGroup = authHandler.buildReadOnlyUserGroup(
          account.getUuid(), readOnlyUser, UserGroup.DEFAULT_READ_ONLY_USER_GROUP_NAME);
      wingsPersistence.save(readOnlyUserGroup);
    }
    return account;
  }

  private Account ensureTestUser(Account account) {
    User testUser =
        ensureUser(testUserUuid, testUserName, testEmail, scmSecret.decryptToCharArray(defaultPassword), account);
    addUserToUserGroup(testUser, account.getUuid(), UserGroup.DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME);

    return account;
  }

  public void addUserToUserGroup(User user, String accountId, String userGroupName) {
    PageRequest<UserGroup> pageRequest =
        aPageRequest().addFilter("accountId", EQ, accountId).addFilter("name", EQ, userGroupName).build();
    PageResponse<UserGroup> pageResponse = userGroupService.list(accountId, pageRequest, true, null, null);
    UserGroup userGroup = pageResponse.get(0);
    userService.addUserToUserGroups(accountId, user, Collections.singletonList(userGroup), false, false);
  }

  private void addUsersToUserGroup(List<User> users, String accountId, String userGroupName) {
    PageRequest<UserGroup> pageRequest =
        aPageRequest().addFilter("accountId", EQ, accountId).addFilter("name", EQ, userGroupName).build();
    PageResponse<UserGroup> pageResponse = userGroupService.list(accountId, pageRequest, true, null, null);
    UserGroup userGroup = pageResponse.get(0);
    for (User user : users) {
      userService.addUserToUserGroups(accountId, user, Collections.singletonList(userGroup), false, false);
    }
  }

  public void addUserToHarnessUserGroup(String accountId, User user) {
    HarnessUserGroup harnessUserGroup = HarnessUserGroup.builder()
                                            .memberIds(Sets.newHashSet(user.getUuid()))
                                            .accountIds(Sets.newHashSet(accountId))
                                            .name("harnessUserGroup")
                                            .groupType(HarnessUserGroup.GroupType.DEFAULT)
                                            .build();
    harnessUserGroupService.save(harnessUserGroup);
  }

  public User ensureUser(String uuid, String userName, String email, char[] password, Account account) {
    User user = anUser()
                    .uuid(uuid)
                    .name(userName)
                    .email(email)
                    .password(password)
                    .roles(wingsPersistence
                               .query(Role.class,
                                   aPageRequest()
                                       .addFilter("accountId", EQ, account.getUuid())
                                       .addFilter("roleType", EQ, RoleType.ACCOUNT_ADMIN)
                                       .build())
                               .getResponse())
                    .accountName(account.getAccountName())
                    .companyName(account.getCompanyName())
                    .build();

    User newUser = userService.registerNewUser(user, account);
    wingsPersistence.updateFields(User.class, newUser.getUuid(), ImmutableMap.of("emailVerified", true));

    return wingsPersistence.get(User.class, newUser.getUuid());
  }

  Account ensureRandom(Randomizer.Seed seed, Owners owners) {
    EnhancedRandom random = Randomizer.instance(seed);
    Accounts predefined = random.nextObject(Accounts.class);
    return ensurePredefined(seed, owners, predefined);
  }
}
