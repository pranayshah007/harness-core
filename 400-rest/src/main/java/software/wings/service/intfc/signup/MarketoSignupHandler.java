/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.signup;

import static io.harness.annotations.dev.HarnessModule._950_NG_SIGNUP;
import static io.harness.annotations.dev.HarnessTeam.GTM;

import static org.springframework.security.crypto.bcrypt.BCrypt.hashpw;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.event.handler.impl.EventPublishHelper;

import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.beans.UserInviteSource;
import software.wings.beans.UserInviteSource.SourceType;
import software.wings.resources.UserResource.UpdatePasswordRequest;
import software.wings.service.intfc.SignupHandler;
import software.wings.service.intfc.SignupService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.security.crypto.bcrypt.BCrypt;

@OwnedBy(GTM)
@TargetModule(_950_NG_SIGNUP)
@Slf4j
public class MarketoSignupHandler implements SignupHandler {
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject private SignupSpamChecker spamChecker;
  @Inject private SignupService signupService;
  @Inject private UserService userService;

  @Override
  public boolean handle(UserInvite userInvite) {
    final String emailAddress = userInvite.getEmail().toLowerCase();
    signupService.validateCluster();
    signupService.validateName(userInvite.getName());
    signupService.validateEmail(emailAddress);

    UserInvite userInviteInDB = signupService.getUserInviteByEmail(emailAddress);

    if (userInviteInDB == null) {
      userInvite.setSource(UserInviteSource.builder().type(SourceType.MARKETO_LINKEDIN).build());
      userInvite.setCompleted(false);
      String inviteId = userService.saveUserInvite(userInvite);
      userInvite.setUuid(inviteId);

      // Send an email invitation for the trial user to confirm the email.
      signupService.sendLinkedInSignupVerificationEmail(userInvite);
      eventPublishHelper.publishTrialUserSignupEvent(
          emailAddress, userInvite.getName(), inviteId, userInvite.getCompanyName());
    } else if (userInviteInDB.isCompleted()) {
      if (spamChecker.isSpam(userInviteInDB)) {
        return false;
      }
      // HAR-7590: If user invite has completed. Send an email saying so and ask the user to login directly.
      signupService.sendTrialSignupCompletedEmail(userInviteInDB);
    } else {
      if (spamChecker.isSpam(userInviteInDB)) {
        return false;
      }
      // HAR-7250: If the user invite was not completed. Resend the verification/invitation email.
      signupService.sendLinkedInSignupVerificationEmail(userInvite);
    }
    return true;
  }

  private String generateRandomPassword() {
    return RandomStringUtils.randomAlphanumeric(15);
  }

  @Override
  public User completeSignup(UpdatePasswordRequest passwordRequest, String token) {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @Override
  public User completeSignup(String token) {
    String email = signupService.getEmail(token);
    UserInvite userInvite = signupService.getUserInviteByEmail(email);
    signupService.checkIfUserInviteIsValid(userInvite, email);
    return setPasswordAndCompleteSignup(userInvite);
  }

  private User setPasswordAndCompleteSignup(UserInvite userInvite) {
    String generatedPassword = generateRandomPassword();
    char[] password = generatedPassword.toCharArray();
    userInvite.setPassword(password);
    userInvite.setPasswordHash(hashpw(generatedPassword, BCrypt.gensalt()));
    signupService.validateName(userInvite.getName());
    userService.saveUserInvite(userInvite);

    // No user and account is created till here. Once this call is made, only then the account and user's are created.
    // This call returns a user object setting bearer token in it and directly logs in the user.
    User user = userService.completeTrialSignupAndSignIn(userInvite, false);
    signupService.sendLinkedInTrialSignupCompletedEmail(userInvite, generatedPassword);
    return user;
  }
}
