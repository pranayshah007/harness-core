/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication.totp;

import static io.harness.rule.OwnerRule.BOGDAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.limits.impl.model.RateLimit;
import io.harness.rule.Owner;
import io.harness.time.FakeClock;

import software.wings.security.authentication.TotpChecker;
import software.wings.security.authentication.totp.RateLimitedTotpChecker.Request;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class RateLimitedTotpCheckerTest {
  private static final RateLimit RATE_LIMIT = new RateLimit(10, 5, TimeUnit.MINUTES);

  private static final Request USER1_REQUEST = new Request(null, 0, "user1_uuid", "user1_email@provider.com");
  private static final Request USER2_REQUEST = new Request(null, 0, "user2_uuid", "user2_email@provider.com");
  private static final int INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED = 50;

  private RateLimitedTotpChecker<Request> rateLimitedChecker;
  private NotificationService notificationService;
  private RateLimitProtectionRepository repository;
  private FakeClock fakeClock;
  private TotpChecker<SimpleTotpChecker.Request> fakeTotpChecker;

  @Before
  public void setUp() {
    notificationService = mock(NotificationService.class);
    fakeTotpChecker = mock(TotpChecker.class);
    repository = new RateLimitProtectionImMemRepository();
    fakeClock = new FakeClock();

    rateLimitedChecker = new RateLimitedTotpChecker<>(fakeTotpChecker, repository, RATE_LIMIT, notificationService,
        fakeClock, INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED);
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldSeparateLimitsPerUser() {
    // given
    when(fakeTotpChecker.check(any())).thenReturn(false);

    // when
    callRateLimited(RATE_LIMIT.getCount(), USER1_REQUEST);
    callRateLimited(RATE_LIMIT.getCount(), USER2_REQUEST);

    // then no exception
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldThrowIfRateLimitBreached() {
    // when
    callRateLimited(RATE_LIMIT.getCount(), USER1_REQUEST);

    // then
    fakeClock.advanceBy(window().minusMillis(1));
    assertThatThrownBy(() -> callRateLimited(1, USER1_REQUEST))
        .isInstanceOf(RateLimitExceededException.class)
        .hasMessage(errorMessage(USER1_REQUEST));
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void ifNotLimitedCheckerShouldReturnTrueIfCheckWasSuccessful() {
    // given
    when(fakeTotpChecker.check(any())).thenReturn(true);

    // when
    assertThat(rateLimitedChecker.check(USER1_REQUEST)).isTrue();

    // then no exception
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void ifNotLimitedCheckerShouldReturnFalseIfCheckWasNotSuccessful() {
    // given
    when(fakeTotpChecker.check(any())).thenReturn(false);

    // then
    assertThat(rateLimitedChecker.check(USER1_REQUEST)).isFalse();
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldNotRateLimitCorrectAttempts() {
    // given
    when(fakeTotpChecker.check(any())).thenReturn(true);

    // then
    callRateLimited(INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED + 1, USER1_REQUEST);
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldNotSendNotificationsOnCorrectAttempts() {
    // given
    when(fakeTotpChecker.check(any())).thenReturn(true);

    // when
    callRateLimited(INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED + 1, USER1_REQUEST);

    // then
    verify(notificationService, never()).notifySecOps(anyString());
    verify(notificationService, never()).notifyUser(anyString());
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldAllowMfaAfterWindowPassed() {
    // given
    when(fakeTotpChecker.check(any())).thenReturn(false);

    // when
    try {
      callRateLimited(RATE_LIMIT.getCount() + 1, USER1_REQUEST);
      fail("Should not get to this point");
    } catch (RateLimitExceededException e) {
      // unused
    }
    fakeClock.advanceBy(window().plusMillis(1));

    // then
    assertThat(rateLimitedChecker.check(USER1_REQUEST)).isFalse();
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldThrowIfLimitBreached() {
    // given
    when(fakeTotpChecker.check(any())).thenReturn(false);

    // when
    callRateLimited(RATE_LIMIT.getCount(), USER1_REQUEST); // no exception

    // then no exception
    assertThatThrownBy(() -> rateLimitedChecker.check(USER1_REQUEST))
        .isInstanceOf(RateLimitExceededException.class)
        .hasMessage(errorMessage(USER1_REQUEST));
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldAllowRateLimitedUserToAttemptAfterWindowPasses() {
    // given
    when(fakeTotpChecker.check(any())).thenReturn(false);
    callRateLimited(RATE_LIMIT.getCount(), USER1_REQUEST); // no exception
    assertThatThrownBy(() -> rateLimitedChecker.check(USER1_REQUEST))
        .isInstanceOf(RateLimitExceededException.class)
        .hasMessage(errorMessage(USER1_REQUEST));

    // when
    when(fakeTotpChecker.check(any())).thenReturn(true);
    fakeClock.advanceBy(window().plusMillis(1L));

    // then no exception
    assertThat(rateLimitedChecker.check(USER1_REQUEST)).isTrue();
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldNotifySecOpsAfterSomeAmountOfIncorrectAttempts() {
    // when
    for (int i = 0; i < INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED; i++) {
      try {
        rateLimitedChecker.check(USER1_REQUEST);
        fakeClock.advanceBy(window().plusMillis(1));
      } catch (Exception e) {
        // ignore
      }
    }

    // then
    verify(notificationService).notifySecOps(eq(USER1_REQUEST.getUserUuid()));
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldNotifyUserAfterLimitBreached() {
    // when
    callRateLimited(RATE_LIMIT.getCount(), USER1_REQUEST);

    // then
    assertThatThrownBy(() -> callRateLimited(1, USER1_REQUEST)).isInstanceOf(RateLimitExceededException.class);
    verify(notificationService).notifyUser(eq(USER1_REQUEST.getUserEmail()));
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void userShouldNotReceiveMoreThanOneNotificationPerHour() {
    // when
    try {
      callRateLimited(RATE_LIMIT.getCount() + 1, USER1_REQUEST);
    } catch (Exception e) {
      // expected
    }

    fakeClock.advanceBy(window().plusMillis(1));

    try {
      callRateLimited(RATE_LIMIT.getCount() + 1, USER1_REQUEST);
    } catch (Exception e) {
      // expected
    }

    // then
    verify(notificationService).notifyUser(eq(USER1_REQUEST.getUserEmail()));
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void secOpsShouldNotReceiveMoreThanOneNotificationPerHourPerUser() {
    // given
    Instant firstRequestTime = fakeClock.instant();

    // when
    for (int i = 0; i < INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED; i++) {
      try {
        rateLimitedChecker.check(USER1_REQUEST);
        fakeClock.advanceBy(window().dividedBy(RATE_LIMIT.getCount()).plusMillis(1));
      } catch (Exception e) {
        // ignore
      }
    }

    fakeClock.instant(firstRequestTime.plusMillis(1)); // get the clock back
    for (int i = 0; i < INCORRECT_ATTEMPTS_UNTIL_SECOPS_NOTIFIED; i++) {
      try {
        rateLimitedChecker.check(USER1_REQUEST);
        fakeClock.advanceBy(window().dividedBy(RATE_LIMIT.getCount()).plusMillis(1));
      } catch (Exception e) {
        // ignore
      }
    }

    // then
    verify(notificationService).notifySecOps(eq(USER1_REQUEST.getUserUuid()));
  }

  private Duration window() {
    return Duration.ofMillis(RATE_LIMIT.getDurationUnit().toMillis(RATE_LIMIT.getDuration()));
  }

  private String errorMessage(Request request) {
    return "Rate limit breached for user with UUID of " + request.getUserUuid();
  }

  private void callRateLimited(int callCount, Request request) {
    for (int i = 0; i < callCount; i++) {
      rateLimitedChecker.check(request);
    }
  }
}
