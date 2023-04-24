/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.signup;

import static io.harness.annotations.dev.HarnessModule._950_NG_SIGNUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.common.beans.Generation.CG;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static org.springframework.security.crypto.bcrypt.BCrypt.hashpw;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.configuration.DeployMode;
import io.harness.exception.SignupException;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.dl.WingsPersistence;
import software.wings.resources.UserResource.UpdatePasswordRequest;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.SignupHandler;
import software.wings.service.intfc.SignupService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;

@OwnedBy(PL)
@TargetModule(_950_NG_SIGNUP)
@Slf4j
@Singleton
public class OnpremSignupHandler implements SignupHandler {
  @Inject private SignupService signupService;
  @Inject private UserService userService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private MainConfiguration mainConfiguration;
  @Inject private AccountService accountService;

  @Override
  public boolean handle(UserInvite userInvite) {
    validate(userInvite);
    userInvite.setPasswordHash(hashpw(new String(userInvite.getPassword()), BCrypt.gensalt()));
    Optional<Account> defaultAccount = fetchDefaultAccount();
    if (defaultAccount.isPresent()) {
      userService.createNewUserAndSignIn(
          mapUserInviteToUser(userInvite, defaultAccount.get()), defaultAccount.get().getUuid(), CG);
    } else {
      userService.saveUserInvite(userInvite);
      userService.completeTrialSignupAndSignIn(userInvite, true);
    }
    accountService.updateFeatureFlagsForOnPremAccount();
    return true;
  }

  private User mapUserInviteToUser(UserInvite userInvite, Account account) {
    User user = User.Builder.anUser()
                    .appId(GLOBAL_APP_ID)
                    .email(userInvite.getEmail())
                    .name(userInvite.getName())
                    .passwordHash(userInvite.getPasswordHash())
                    .emailVerified(true)
                    .utmInfo(userInvite.getUtmInfo())
                    .build();
    user.getAccounts().add(account);
    return user;
  }

  private Optional<Account> fetchDefaultAccount() {
    return accountService.getOnPremAccount();
  }

  private void validate(UserInvite userInvite) {
    if (!mainConfiguration.isTrialRegistrationAllowedForBugathon()) {
      validateDeployMode();
      validateCountOfAccount();
      validateCountOfUsers();
    }
    signupService.validateEmail(userInvite.getEmail());
    signupService.validateName(userInvite.getName());
    signupService.validatePassword(userInvite.getPassword());
  }

  private void validateCountOfUsers() {
    if (getUserCount() > 0) {
      throw new SignupException(
          "One or more users already exist. Onprem environment does not allow multiple user signup");
    }
  }

  private void validateCountOfAccount() {
    if (getAccountCount() > 1) {
      throw new SignupException(
          "Multiple account already exists in the database. This API should only be used to sign-up on SMP");
    }
  }

  private void validateDeployMode() {
    if (!DeployMode.isOnPrem(mainConfiguration.getDeployMode().toString())) {
      throw new SignupException("Method only allowed in case of Onprem deployments");
    }
  }

  @Override
  public User completeSignup(UpdatePasswordRequest updatePasswordRequest, String token) {
    throw new SignupException("This method should not be called in case if Onprem installations.");
  }

  @Override
  public User completeSignup(String token) {
    throw new UnsupportedOperationException("Operation not supported");
  }

  private long getAccountCount() {
    return wingsPersistence.createQuery(Account.class).field(AccountKeys.uuid).notEqual(GLOBAL_ACCOUNT_ID).count();
  }

  private long getUserCount() {
    return wingsPersistence.createQuery(User.class).count();
  }
}
