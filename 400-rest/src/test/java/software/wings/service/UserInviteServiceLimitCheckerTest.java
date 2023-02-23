/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.rule.OwnerRule.NANDAN;

import static software.wings.beans.User.Builder.anUser;
import static software.wings.beans.UserInvite.UserInviteBuilder.anUserInvite;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_NAME;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.COMPANY_NAME;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.USER_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.crypto.bcrypt.BCrypt.hashpw;

import io.harness.category.element.UnitTests;
import io.harness.limits.ActionType;
import io.harness.limits.ConfiguredLimit;
import io.harness.limits.configuration.LimitConfigurationService;
import io.harness.limits.impl.model.StaticLimit;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.service.impl.UserServiceLimitChecker;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.bcrypt.BCrypt;

public class UserInviteServiceLimitCheckerTest extends WingsBaseTest {
  private final User.Builder userBuilder = anUser().appId(APP_ID).email(USER_EMAIL).name(USER_NAME).password(PASSWORD);

  @Mock private AccountService accountService;
  @Mock private LimitConfigurationService limits;
  @Inject @InjectMocks private UserServiceLimitChecker userServiceLimitChecker;

  @Before
  public void setupTests() throws Exception {
    Account account = Account.Builder.anAccount()
                          .withAccountName(ACCOUNT_NAME)
                          .withCompanyName(COMPANY_NAME)
                          .withUuid(ACCOUNT_ID)
                          .build();

    when(accountService.get(ACCOUNT_ID)).thenReturn(account);

    StaticLimit userLimit = new StaticLimit(4);
    ConfiguredLimit limit = new ConfiguredLimit<>(ACCOUNT_ID, userLimit, ActionType.CREATE_USER);
    when(limits.getOrDefault(ACCOUNT_ID, ActionType.CREATE_USER)).thenReturn(limit);
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void checkUserLimit_below() {
    List<User> existingUsersAndInvites = new ArrayList<>();

    User existingUser = userBuilder.uuid(USER_ID)
                            .emailVerified(true)
                            .companyName(COMPANY_NAME)
                            .accountName(ACCOUNT_NAME)
                            .passwordHash(hashpw(new String(PASSWORD), BCrypt.gensalt()))
                            .build();

    User existingUserYetToBeVerified = userBuilder.uuid(USER_ID)
                                           .emailVerified(false)
                                           .companyName(COMPANY_NAME)
                                           .accountName(ACCOUNT_NAME)
                                           .passwordHash(hashpw(new String(PASSWORD), BCrypt.gensalt()))
                                           .build();

    existingUsersAndInvites.add(existingUser);
    existingUsersAndInvites.add(existingUserYetToBeVerified);

    UserInvite newUserInvite =
        anUserInvite().withAccountName(ACCOUNT_NAME).withCompanyName(COMPANY_NAME).withName(USER_NAME).build();

    ConfiguredLimit limit = limits.getOrDefault(ACCOUNT_ID, ActionType.CREATE_USER);
    StaticLimit userLimit = (StaticLimit) limit.getLimit();
    List<String> emails = new ArrayList<>();
    for (int i = 0; i < userLimit.getCount() - existingUsersAndInvites.size(); i++) {
      emails.add(UUID.randomUUID().toString() + USER_EMAIL);
    }
    newUserInvite.setEmails(emails);
    userServiceLimitChecker.limitCheck(ACCOUNT_ID, existingUsersAndInvites, new HashSet<>(newUserInvite.getEmails()));
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void checkUserLimit_above() {
    List<User> existingUsersAndInvites = new ArrayList<>();

    User existingUser = userBuilder.uuid(USER_ID)
                            .emailVerified(true)
                            .companyName(COMPANY_NAME)
                            .accountName(ACCOUNT_NAME)
                            .passwordHash(hashpw(new String(PASSWORD), BCrypt.gensalt()))
                            .build();

    User existingUserYetToBeVerified = userBuilder.uuid(USER_ID)
                                           .emailVerified(false)
                                           .companyName(COMPANY_NAME)
                                           .accountName(ACCOUNT_NAME)
                                           .passwordHash(hashpw(new String(PASSWORD), BCrypt.gensalt()))
                                           .build();

    existingUsersAndInvites.add(existingUser);
    existingUsersAndInvites.add(existingUserYetToBeVerified);

    UserInvite newUserInvite =
        anUserInvite().withAccountName(ACCOUNT_NAME).withCompanyName(COMPANY_NAME).withName(USER_NAME).build();

    ConfiguredLimit limit = limits.getOrDefault(ACCOUNT_ID, ActionType.CREATE_USER);
    StaticLimit userLimit = (StaticLimit) limit.getLimit();
    List<String> emails = new ArrayList<>();
    for (int i = 0; i < userLimit.getCount(); i++) {
      emails.add(UUID.randomUUID().toString() + USER_EMAIL);
    }
    newUserInvite.setEmails(emails);
    try {
      userServiceLimitChecker.limitCheck(ACCOUNT_ID, existingUsersAndInvites, new HashSet<>(newUserInvite.getEmails()));
    } catch (Exception e) {
      assertThat(e.getMessage()).isEqualTo(String.format("You can only add upto %s users.", userLimit.getCount()));
    }
  }
}
