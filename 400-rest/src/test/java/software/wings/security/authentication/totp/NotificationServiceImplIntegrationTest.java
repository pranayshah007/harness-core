/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication.totp;

import static io.harness.rule.OwnerRule.BOGDAN;

import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.sanitizer.HtmlInputSanitizer;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.persistence.mail.EmailData;
import software.wings.service.intfc.EmailNotificationService;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class NotificationServiceImplIntegrationTest extends WingsBaseTest {
  private static final String SANITIZED_NAME = "sanitized_name";
  private static final String APP_BASE_URL = "https://app.harness.io/";

  @Inject private HPersistence persistence;
  @Inject private MainConfiguration mainConfiguration;

  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock private HtmlInputSanitizer htmlInputSanitizer;
  @Mock private EmailNotificationService emailNotificationService;

  private NotificationServiceImpl notificationService;

  private Account cgAccount;
  private Account ngAccount;
  private User user1;
  private User user2;

  @Before
  public void setUp() {
    cgAccount = buildCgAccount();
    persistence.save(cgAccount);
    ngAccount = buildNgAccount();
    persistence.save(ngAccount);

    user1 = user1();
    persistence.save(user1);
    user2 = buildUser2();
    persistence.save(user2);

    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn(APP_BASE_URL);
    when(htmlInputSanitizer.sanitizeInput(anyString())).thenReturn(SANITIZED_NAME);

    notificationService = new NotificationServiceImpl(
        persistence, subdomainUrlHelper, emailNotificationService, htmlInputSanitizer, mainConfiguration);
  }

  @After
  public void tearDown() {
    persistence.delete(user1);
    persistence.delete(user2);
    persistence.delete(cgAccount);
    persistence.delete(ngAccount);
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldSendEmailNotificationToUserCg() throws UnsupportedEncodingException {
    // when
    notificationService.notifyUser(user1.getEmail());

    // then
    ArgumentCaptor<EmailData> captor = ArgumentCaptor.forClass(EmailData.class);
    verify(emailNotificationService).send(captor.capture());
    EmailData actualEmailData = captor.getValue();
    assertThat(actualEmailData.getTo()).containsOnly(user1.getEmail());
    assertThat(actualEmailData.getTemplateName()).isEqualTo("mfa_alert_user");
    assertThat(actualEmailData.getRetries()).isEqualTo(10);
    assertThat(actualEmailData.getAccountId()).isEqualTo(user1.getAccountIds().get(0));

    Map<String, String> templateModel = (Map<String, String>) actualEmailData.getTemplateModel();
    assertThat(templateModel.get("name")).isEqualTo(SANITIZED_NAME);

    String url = templateModel.get("url");
    assertCgUrlValid(url);
    assertTokenValid(extractToken(url), user1.getEmail());
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldSendEmailNotificationToUserNg() throws UnsupportedEncodingException {
    // when
    notificationService.notifyUser(user2.getEmail());
    ArgumentCaptor<EmailData> captor = ArgumentCaptor.forClass(EmailData.class);
    verify(emailNotificationService).send(captor.capture());

    // then
    EmailData actualEmailData = captor.getValue();
    assertThat(actualEmailData.getTo()).containsOnly(user2.getEmail());
    assertThat(actualEmailData.getTemplateName()).isEqualTo("mfa_alert_user");
    assertThat(actualEmailData.getRetries()).isEqualTo(10);
    assertThat(actualEmailData.getAccountId()).isEqualTo(user2.getAccountIds().get(0));

    Map<String, String> templateModel = (Map<String, String>) actualEmailData.getTemplateModel();
    assertThat(templateModel.get("name")).isEqualTo(SANITIZED_NAME);

    String url = templateModel.get("url");
    assertNgUrlValid(url);
    assertTokenValid(extractToken(url), user2.getEmail());
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldSwallowExceptionIfWeCannotSendMailToUser() {
    // given
    doThrow(new RuntimeException()).when(emailNotificationService).send(any(EmailData.class));

    // when
    notificationService.notifyUser("anyEmail");

    // then no exception
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldSendMailToSecOps() {
    // when
    notificationService.notifySecOps(user1.getUuid());

    // then
    String expectedMailAddress = mainConfiguration.getTotpConfig().getSecOpsEmail();
    ArgumentCaptor<EmailData> captor = ArgumentCaptor.forClass(EmailData.class);
    verify(emailNotificationService).send(captor.capture());
    EmailData actualEmailData = captor.getValue();
    assertThat(actualEmailData.getTo()).containsOnly(expectedMailAddress);
    assertThat(actualEmailData.getTemplateName()).isEqualTo("mfa_alert_secops");
    assertThat(actualEmailData.getRetries()).isEqualTo(10);
    assertThat(actualEmailData.getAccountId()).isNull();

    Map<String, String> templateModel = (Map<String, String>) actualEmailData.getTemplateModel();
    assertThat(templateModel.get("userUuid")).isEqualTo(user1.getUuid());
  }

  @NotNull
  private User buildUser2() {
    return User.Builder.anUser()
        .name("NgUserName")
        .accounts(Lists.newArrayList(ngAccount, cgAccount))
        .defaultAccountId(ngAccount.getUuid())
        .email("user2@mail.com")
        .build();
  }

  @NotNull
  private Account buildNgAccount() {
    return Account.Builder.anAccount()
        .withCompanyName("NG Company inc")
        .withAccountKey("ngAccountKey")
        .withDefaultExperience(DefaultExperience.NG)
        .build();
  }

  @NotNull
  private User user1() {
    return User.Builder.anUser()
        .name("SomeUserName")
        .accounts(Lists.newArrayList(cgAccount, ngAccount))
        .defaultAccountId(cgAccount.getUuid())
        .email("user1@mail.com")
        .build();
  }

  @NotNull
  private Account buildCgAccount() {
    return Account.Builder.anAccount()
        .withCompanyName("CG Company inc")
        .withAccountKey("cgAccountKey")
        .withDefaultExperience(DefaultExperience.CG)
        .build();
  }

  private void assertNgUrlValid(String url) {
    String urlRegex = Pattern.quote(APP_BASE_URL + "auth/#/reset-password/") + tokenRegex()
        + Pattern.quote("?accountId=" + user2.getDefaultAccountId());
    assertThat(url).matches(Pattern.compile(urlRegex));
  }

  private void assertCgUrlValid(String url) {
    String urlRegex = Pattern.quote(APP_BASE_URL + "#/reset-password/") + tokenRegex()
        + Pattern.quote("?accountId=" + user1.getDefaultAccountId());
    assertThat(url).matches(Pattern.compile(urlRegex));
  }

  private void assertTokenValid(String token, String userEmail) throws UnsupportedEncodingException {
    DecodedJWT decoded =
        JWT.require(Algorithm.HMAC256(mainConfiguration.getPortal().getJwtPasswordSecret())).build().verify(token);

    assertThat(decoded.getIssuer()).isEqualTo("Harness Inc");
    assertThat(decoded.getClaim("email").asString()).isEqualTo(userEmail);

    Instant expiresAt = decoded.getExpiresAt().toInstant();
    assertThat(expiresAt).isCloseTo(Instant.now().plus(Duration.ofDays(2)), within(5, MINUTES));
  }

  private String tokenRegex() {
    String tokenStart = "ey";
    String allExceptDot = "[a-zA-Z0-9-_]+";
    String dot = "\\.";
    return tokenStart + allExceptDot + dot + allExceptDot + dot + allExceptDot;
  }

  private String extractToken(String url) {
    Matcher matcher = Pattern.compile(tokenRegex()).matcher(url);
    matcher.find();
    return matcher.group();
  }
}
