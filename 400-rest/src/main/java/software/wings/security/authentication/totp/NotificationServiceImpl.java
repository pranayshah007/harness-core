/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication.totp;

import static io.harness.ng.core.account.DefaultExperience.NG;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.HPersistence;
import io.harness.sanitizer.HtmlInputSanitizer;
import io.harness.security.JWTTokenServiceUtils;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.persistence.mail.EmailData;
import software.wings.service.intfc.EmailNotificationService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;

@OwnedBy(HarnessTeam.PL)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NotificationServiceImpl implements NotificationService {
  private static final long TOKEN_VALIDITY_MILLIS = Duration.ofDays(2L).toMillis();
  private static final String USER_ALERT_TEMPLATE = "mfa_alert_user";

  private final HPersistence persistence;
  private final SubdomainUrlHelperIntfc subdomainUrlHelper;
  private final EmailNotificationService emailNotificationService;
  private final HtmlInputSanitizer userNameSanitizer;
  private final MainConfiguration configuration;

  @Override
  public void notifyUser(String email) {
    try {
      sendPasswordResetNotification(email);
    } catch (Exception e) {
      // not mission critical, so do not rethrow
      log.error("Could not send email notification.", e);
    }
  }

  @Override
  public void notifySecOps(String userUuid) {
    String secOpsEmail = configuration.getTotpConfig().getSecOpsEmail();
    EmailData emailData = EmailData.builder()
                              .to(Lists.newArrayList(secOpsEmail))
                              .templateName("mfa_alert_secops")
                              .templateModel(ImmutableMap.of("userUuid", userUuid))
                              .build();

    emailData.setRetries(10);
    emailNotificationService.send(emailData);
  }

  private void sendPasswordResetNotification(String email) throws URISyntaxException {
    User user = getUserByEmail(email);
    String token = generateJwtToken(email);
    Account defaultAccount = getDefaultAccount(user);

    EmailData emailData = EmailData.builder()
                              .to(Lists.newArrayList(email))
                              .templateName(USER_ALERT_TEMPLATE)
                              .templateModel(getTemplateModel(user.getName(), token, defaultAccount))
                              .accountId(getPrimaryAccountId(user))
                              .build();

    emailData.setRetries(10);
    emailNotificationService.send(emailData);
  }

  private boolean isAccountNg(Account account) {
    return NG.equals(account.getDefaultExperience());
  }

  private String generateJwtToken(String email) {
    String jwtPasswordSecret = configuration.getPortal().getJwtPasswordSecret();
    ImmutableMap<String, String> claims = ImmutableMap.of("email", email);
    return JWTTokenServiceUtils.generateJWTToken(claims, TOKEN_VALIDITY_MILLIS, jwtPasswordSecret);
  }

  private User getUserByEmail(String email) {
    return persistence.createQuery(User.class).filter(UserKeys.email, email.trim().toLowerCase()).get();
  }

  private Account getDefaultAccount(User user) {
    return persistence.get(Account.class, user.getDefaultAccountId());
  }

  private String getPrimaryAccountId(User user) {
    return user.getAccounts().get(0).getUuid();
  }

  private String getResetPasswordUrl(String token, String defaultAccountId, boolean isNGRequest)
      throws URISyntaxException {
    URIBuilder uriBuilder = new URIBuilder(subdomainUrlHelper.getPortalBaseUrl(defaultAccountId));
    if (isNGRequest) {
      uriBuilder.setPath("auth/");
    }
    uriBuilder.setFragment("/reset-password/" + token + "?accountId=" + defaultAccountId);
    return uriBuilder.toString();
  }

  private Map<String, String> getTemplateModel(String userName, String token, Account defaultAccount)
      throws URISyntaxException {
    Map<String, String> templateModel = new HashMap<>();
    templateModel.put("name", sanitizeUserName(userName));
    templateModel.put("url", getResetPasswordUrl(token, defaultAccount.getUuid(), isAccountNg(defaultAccount)));
    return templateModel;
  }

  private String sanitizeUserName(String name) {
    return userNameSanitizer.sanitizeInput(name);
  }
}
