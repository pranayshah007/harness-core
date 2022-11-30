/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.limits.checker.rate;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.checker.rate.UsageBucket.UsageBucketKeys;
import io.harness.limits.checkers.LimitChecker;
import io.harness.limits.impl.model.RateLimit;
import io.harness.rule.Owner;

import software.wings.dl.WingsPersistence;
import software.wings.integration.IntegrationTestBase;
import software.wings.integration.IntegrationTestUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class MongoSlidingWindowRateLimitCheckerIntegrationTest extends IntegrationTestBase {
  @Inject private WingsPersistence persistence;

  private boolean indexesEnsured;
  private static final String SOME_ACCOUNT_ID = "acc-id-" + RandomStringUtils.randomAlphanumeric(5)
      + MongoSlidingWindowRateLimitCheckerIntegrationTest.class.getSimpleName();

  private static final Action ACTION = new Action(SOME_ACCOUNT_ID, ActionType.CREATE_APPLICATION);
  private final ExecutorService executors = Executors.newFixedThreadPool(7);

  @Before
  public void init() throws Exception {
    this.cleanUp();
    if (!indexesEnsured && !IntegrationTestUtils.isManagerRunning(client)) {
      persistence.getDatastore(UsageBucket.class).ensureIndexes(UsageBucket.class);
      indexesEnsured = true;
    }
  }

  @After
  public void cleanUp() throws Exception {
    persistence.delete(persistence.createQuery(UsageBucket.class).filter(UsageBucketKeys.key, ACTION.key()));
  }

  @Value
  private static class Request implements Runnable {
    private int i;
    private LimitChecker limitChecker;

    Request(int i, LimitChecker limitChecker) {
      this.i = i;
      this.limitChecker = limitChecker;
    }

    @NonFinal private boolean allowed;

    @Override
    public void run() {
      this.allowed = limitChecker.checkAndConsume();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testCheckAndConsume() throws Exception {
    int maxReq = 10;
    int durationInMillis = 5000;
    RateLimit limit = new RateLimit(maxReq, durationInMillis, TimeUnit.MILLISECONDS);
    MongoSlidingWindowRateLimitChecker limitChecker =
        new MongoSlidingWindowRateLimitChecker(limit, persistence, ACTION);

    for (int i = 0; i < maxReq; i++) {
      assertThat(limitChecker.checkAndConsume()).isTrue();
    }

    assertThat(limitChecker.checkAndConsume()).isFalse();

    Thread.sleep(durationInMillis + 5);
    assertThat(limitChecker.checkAndConsume()).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testVicinity() throws Exception {
    int maxReq = 10;
    int durationInMillis = 5000;
    RateLimit limit = new RateLimit(maxReq, durationInMillis, TimeUnit.MILLISECONDS);
    MongoSlidingWindowRateLimitChecker limitChecker =
        new MongoSlidingWindowRateLimitChecker(limit, persistence, ACTION);

    double count = 0.8 * maxReq;
    for (int i = 0; i < count; i++) {
      assertThat(limitChecker.checkAndConsume()).isTrue();
    }

    assertThat(limitChecker.crossed(70)).isTrue();
    assertThat(limitChecker.crossed(90)).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testCheckAndConsumeConcurrent() throws Exception {
    int maxAllowedReq = 40;
    int durationInMillis = 5000;
    RateLimit limit = new RateLimit(maxAllowedReq, durationInMillis, TimeUnit.MILLISECONDS);
    wingsPersistence.save(new UsageBucket(ACTION.key(), new ArrayList<>()));

    MongoSlidingWindowRateLimitChecker limitChecker =
        new MongoSlidingWindowRateLimitChecker(limit, persistence, ACTION);

    List<Future> futures = new LinkedList<>();
    List<Request> requests = new LinkedList<>();
    int totalRequests = maxAllowedReq + ThreadLocalRandom.current().nextInt(40, 100);

    for (int i = 0; i < totalRequests; i++) {
      Request request = new Request(i, limitChecker);
      Future f = executors.submit(request);
      futures.add(f);
      requests.add(request);
    }

    for (Future f : futures) {
      f.get();
    }

    long allowedCount = requests.stream().filter(request -> request.allowed).count();
    long disallowedCount = requests.stream().filter(request -> !request.allowed).count();

    // verify that rate limit is within 1 percent accuracy of expected count
    assertThat((double) allowedCount).isCloseTo((double) maxAllowedReq, offset(totalRequests / 100.0));
    assertThat((double) disallowedCount)
        .isCloseTo((double) (totalRequests - maxAllowedReq), offset(totalRequests / 100.0));

    assertThat(limitChecker.checkAndConsume()).isFalse();

    Thread.sleep(durationInMillis + 10);
    boolean allowed = limitChecker.checkAndConsume();
    assertThat(allowed).isTrue();
  }
}
