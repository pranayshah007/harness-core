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
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;

@OwnedBy(GTM)
@TargetModule(_950_NG_SIGNUP)
@Slf4j
public class AzureMarketplaceSignupHandler implements SignupHandler {
  private static final String EMAIL = "email";
  private static final String SOURCE = "source";
  private static final String ACTIVATION_URL = "/activation.html";
  private static final String USER_INVITE_ID = "userInviteId";
  private static final String TOKEN = "token";
  private static final String AZURE_MARKETPLACE = "azureMarketplace";
  @Inject private SignupService signupService;
  @Inject private UserService userService;
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject private SignupSpamChecker spamChecker;
  @Inject private AzureMarketplaceIntegrationService azureMarketplaceIntegrationService;

  @Override
  public User completeSignup(UpdatePasswordRequest updatePasswordRequest, String token) {
    String email = signupService.getEmail(token);
    UserInvite userInvite = signupService.getUserInviteByEmail(email);
    signupService.checkIfUserInviteIsValid(userInvite, email);
    User user = userService.completePaidSignupAndSignIn(userInvite);
    azureMarketplaceIntegrationService.activateSubscription(userInvite, user);
    return user;
  }

  @Override
  public User completeSignup(String token) {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @Override
  public boolean handle(UserInvite userInvite) {
    final String emailAddress = userInvite.getEmail().toLowerCase().trim();

    signupService.validateEmail(emailAddress);
    UserInvite userInviteInDB = signupService.getUserInviteByEmail(emailAddress);

    Map<String, String> params = new HashMap<>();
    params.put(EMAIL, userInvite.getEmail());
    params.put(SOURCE, AZURE_MARKETPLACE);
    String url = ACTIVATION_URL;

    if (userInviteInDB == null) {
      userInvite.setSource(UserInviteSource.builder().type(SourceType.AZURE_MARKETPLACE).build());
      userInvite.setCompleted(false);
      String hashed = hashpw(new String(userInvite.getPassword()), BCrypt.gensalt());
      userInvite.setPasswordHash(hashed);
      String inviteId = userService.saveUserInvite(userInvite);
      userInvite.setUuid(inviteId);
      String signupSecretToken = userService.createSignupSecretToken(userInvite.getEmail(), 100);
      params.put(USER_INVITE_ID, inviteId);
      params.put(TOKEN, signupSecretToken);

      // Send an email invitation for the trial user to finish up the sign-up with asking password.
      userService.sendVerificationEmail(userInvite, url, params);
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
      String signupSecretToken = userService.createSignupSecretToken(userInvite.getEmail(), 100);
      params.put(USER_INVITE_ID, userInvite.getUuid());
      params.put(TOKEN, signupSecretToken);
      userService.sendVerificationEmail(userInvite, url, params);
    }
    return true;
  }
}
