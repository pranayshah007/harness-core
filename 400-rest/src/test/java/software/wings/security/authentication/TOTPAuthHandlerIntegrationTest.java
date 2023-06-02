/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication;

import static io.harness.eraro.ErrorCode.INVALID_TOTP_TOKEN;
import static io.harness.rule.OwnerRule.BOGDAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.limits.impl.model.RateLimit;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.rule.Owner;
import io.harness.sanitizer.HtmlInputSanitizer;
import io.harness.time.FakeClock;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.persistence.mail.EmailData;
import software.wings.security.authentication.totp.NotificationServiceImpl;
import software.wings.security.authentication.totp.RateLimitExceededException;
import software.wings.security.authentication.totp.RateLimitProtectionMongoRepository;
import software.wings.security.authentication.totp.RateLimitedTotpChecker;
import software.wings.security.authentication.totp.RateLimitedTotpChecker.Request;
import software.wings.service.intfc.EmailNotificationService;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import jersey.repackaged.com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TOTPAuthHandlerIntegrationTest extends WingsBaseTest {
  private static final RateLimit RATE_LIMIT = new RateLimit(10, 5, TimeUnit.MINUTES);
  private static final Duration WINDOW =
      Duration.ofMillis(RATE_LIMIT.getDurationUnit().toMillis(RATE_LIMIT.getDuration()));

  private static final User USER = User.Builder.anUser().name("name").email("testemail1@test.com").build();
  private static final String CREDS = "000000";

  @Inject private WingsPersistence wingsPersistence;
  @Inject private MainConfiguration configuration;
  @Inject private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Inject private HtmlInputSanitizer htmlInputSanitizer;

  @Mock private EmailNotificationService emailNotificationService;

  private TOTPAuthHandler handler;
  private FakeClock fakeClock;
  private TotpChecker<Object> mockChecker;
  private RateLimitProtectionMongoRepository repository;
  private int attemptsUntilSecops;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    fakeClock = new FakeClock();
    mockChecker = mock(TotpChecker.class);
    repository = new RateLimitProtectionMongoRepository(wingsPersistence);
    attemptsUntilSecops = configuration.getTotpConfig().getIncorrectAttemptsUntilSecOpsNotified();

    NotificationServiceImpl notificationServiceImpl = new NotificationServiceImpl(
        wingsPersistence, subdomainUrlHelper, emailNotificationService, htmlInputSanitizer, configuration);
    RateLimitedTotpChecker<Request> rateLimitedChecker = new RateLimitedTotpChecker<>(
        mockChecker, repository, RATE_LIMIT, notificationServiceImpl, fakeClock, attemptsUntilSecops);

    handler = new TOTPAuthHandler(null, null, null, rateLimitedChecker);

    Account account = buildCgAccount();
    wingsPersistence.save(account);

    USER.setTotpSecretKey("anySecretKey");
    USER.setAccounts(Lists.newArrayList(account));
    USER.setDefaultAccountId(account.getUuid());
    wingsPersistence.save(USER);
  }

  @After
  public void tearDown() {
    wingsPersistence.delete(USER);
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldReturnUserIfChallengeSuccessful() {
    // given
    when(mockChecker.check(any())).thenReturn(true);

    // when
    User returnedUser = handler.authenticate(USER, CREDS);

    // then
    assertThat(returnedUser).isNotNull();
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldThrowIfChallengeUnsuccessful() {
    // given
    when(mockChecker.check(any())).thenReturn(false);

    // then
    assertThatThrownBy(() -> handler.authenticate(USER, CREDS))
        .isInstanceOf(WingsException.class)
        .isEqualToComparingFieldByField(new WingsException(INVALID_TOTP_TOKEN, WingsException.USER));
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldThrowIfUserBreachesLimit() {
    // given
    when(mockChecker.check(any())).thenReturn(false);

    // when
    for (int i = 0; i < RATE_LIMIT.getCount(); i++) {
      try {
        handler.authenticate(USER, CREDS);
      } catch (RateLimitExceededException e) {
        fail("Should not rate limit at this point.");
      } catch (WingsException e) {
        // expected
      }
    }

    // then
    assertThatThrownBy(() -> handler.authenticate(USER, CREDS)).isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldSendEmailToUserIfLimitBreached() {
    // given
    when(mockChecker.check(any())).thenReturn(false);

    // when
    for (int i = 0; i < RATE_LIMIT.getCount(); i++) {
      try {
        handler.authenticate(USER, CREDS);
      } catch (RateLimitExceededException e) {
        fail("Should not throw RateLimitExceeded at this point.");
      } catch (WingsException e) {
        // expected
      }
    }

    // then
    try {
      handler.authenticate(USER, CREDS);
    } catch (RateLimitExceededException e) {
      // expected
    }

    ArgumentCaptor<EmailData> emailCaptor = ArgumentCaptor.forClass(EmailData.class);
    verify(emailNotificationService).send(emailCaptor.capture());
    assertThat(emailCaptor.getValue().getTo()).containsOnly(USER.getEmail());
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldSendEmailToSecOps() {
    // given
    when(mockChecker.check(any())).thenReturn(false);

    // when
    for (int i = 0; i < attemptsUntilSecops - 1; i++) {
      try {
        fakeClock.advanceBy(WINDOW.plusMillis(1));
        handler.authenticate(USER, CREDS);
      } catch (RateLimitExceededException e) {
        fail("Should not rate limit here");
      } catch (WingsException e) {
        // expected
      }
    }

    // then
    verify(emailNotificationService, never()).send(any());
    try {
      handler.authenticate(USER, CREDS);
    } catch (RateLimitExceededException e) {
      fail("Should not limit here");
    } catch (WingsException e) {
      // expected
    }

    ArgumentCaptor<EmailData> emailCaptor = ArgumentCaptor.forClass(EmailData.class);
    verify(emailNotificationService).send(emailCaptor.capture());
    assertThat(emailCaptor.getValue().getTo()).containsOnly(configuration.getTotpConfig().getSecOpsEmail());
  }

  @NotNull
  private Account buildCgAccount() {
    return Account.Builder.anAccount()
        .withCompanyName("CG Company inc")
        .withAccountKey("cgAccountKey")
        .withDefaultExperience(DefaultExperience.CG)
        .build();
  }
}
