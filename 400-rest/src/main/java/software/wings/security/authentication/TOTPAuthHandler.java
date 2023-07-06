/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.logging.AutoLogContext;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.logcontext.UserLogContext;
import software.wings.persistence.mail.EmailData;
import software.wings.security.authentication.totp.FeatureFlaggedTotpChecker;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Slf4j
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class TOTPAuthHandler implements TwoFactorAuthHandler {
  private UserService userService;
  private AuthenticationUtils authenticationUtils;
  private EmailNotificationService emailNotificationService;
  private TotpChecker<? super FeatureFlaggedTotpChecker.Request> totpChecker;

  @Inject
  public TOTPAuthHandler(UserService userService, AuthenticationUtils authenticationUtils,
      EmailNotificationService emailNotificationService,
      @Named("featureFlagged") TotpChecker<? super FeatureFlaggedTotpChecker.Request> totpChecker) {
    this.userService = userService;
    this.authenticationUtils = authenticationUtils;
    this.emailNotificationService = emailNotificationService;
    this.totpChecker = totpChecker;
  }

  @Override
  public User authenticate(User user, String... credentials) {
    String accountId = user.getDefaultAccountId();
    String uuid = user.getUuid();
    try (AutoLogContext ignore = new UserLogContext(accountId, uuid, OVERRIDE_ERROR)) {
      log.info("Authenticating via Two Factor Authentication for account {} and user {}", accountId, uuid);
      String passcode = credentials[0];
      String totpSecret = user.getTotpSecretKey();
      if (isBlank(totpSecret)) {
        throw new WingsException(ErrorCode.INVALID_TWO_FACTOR_AUTHENTICATION_CONFIGURATION);
      }
      final int code = Integer.parseInt(passcode);
      FeatureFlaggedTotpChecker.Request totpRequest =
          new FeatureFlaggedTotpChecker.Request(totpSecret, code, uuid, user.getEmail(), accountId);
      if (!totpChecker.check(totpRequest)) {
        throw new WingsException(ErrorCode.INVALID_TOTP_TOKEN, USER);
      }

      return user;

    } catch (NumberFormatException e) {
      throw new WingsException(ErrorCode.UNKNOWN_ERROR, e);
    }
  }

  @Override
  public TwoFactorAuthenticationMechanism getAuthenticationMechanism() {
    return TwoFactorAuthenticationMechanism.TOTP;
  }

  @Override
  public TwoFactorAuthenticationSettings createTwoFactorAuthenticationSettings(User user, Account account) {
    String secretKey = generateTotpSecret();
    String otpUrl = generateOtpUrl(account.getCompanyName(), user.getEmail(), secretKey);
    return TwoFactorAuthenticationSettings.builder()
        .mechanism(getAuthenticationMechanism())
        .totpqrurl(otpUrl)
        .twoFactorAuthenticationEnabled(user.isTwoFactorAuthenticationEnabled())
        .email(user.getEmail())
        .userId(user.getUuid())
        .totpSecretKey(secretKey)
        .build();
  }

  @Override
  public User applyTwoFactorAuthenticationSettings(User user, TwoFactorAuthenticationSettings settings) {
    return userService.updateTwoFactorAuthenticationSettings(user, settings);
  }

  @Override
  public User disableTwoFactorAuthentication(User user) {
    user.setTwoFactorAuthenticationEnabled(false);
    return userService.update(user);
  }

  private String generateTotpSecret() {
    return TimeBasedOneTimePasswordUtil.generateBase32Secret();
  }

  public String generateOtpUrl(String companyName, String userEmailAddress, String secret) {
    return format("otpauth://totp/%s:%s?secret=%s&issuer=Harness-Inc", "Harness_" + companyName.replace(" ", "-"),
        userEmailAddress, secret);
  }

  @Override
  public boolean resetAndSendEmail(User user) {
    TwoFactorAuthenticationSettings settings =
        createTwoFactorAuthenticationSettings(user, authenticationUtils.getDefaultAccount(user));
    user = applyTwoFactorAuthenticationSettings(user, settings);
    return sendTwoFactorAuthenticationResetEmail(user);
  }

  /**
   * Send 2FA Reset email to the user
   * @param user
   * @return
   */
  public boolean sendTwoFactorAuthenticationResetEmail(User user) {
    Map<String, String> templateModel = new HashMap<>();
    Account defaultAccount = authenticationUtils.getDefaultAccount(user);
    templateModel.put("name", userService.sanitizeUserName(user.getName()));
    templateModel.put("totpSecret", user.getTotpSecretKey());
    String totpUrl = generateOtpUrl(defaultAccount.getCompanyName(), user.getEmail(), user.getTotpSecretKey());
    templateModel.put("totpUrl", totpUrl);
    List<String> toList = new ArrayList();
    toList.add(user.getEmail());
    EmailData emailData = EmailData.builder()
                              .to(toList)
                              .templateName("reset_2fa")
                              .templateModel(templateModel)
                              .accountId(defaultAccount.getUuid())
                              .build();
    emailData.setCc(Collections.emptyList());
    emailData.setRetries(2);
    emailNotificationService.send(emailData);
    log.info("Sent TwoFactorAuthenticationResetEmail for user {} with totpUrl {}", user.getUuid(), totpUrl);
    return true;
  }
}
