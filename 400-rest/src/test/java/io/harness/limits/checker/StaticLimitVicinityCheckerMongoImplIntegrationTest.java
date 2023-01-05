/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.limits.checker;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.limits.ActionType;
import io.harness.limits.Counter;
import io.harness.limits.impl.model.StaticLimit;
import io.harness.rule.Owner;

import software.wings.dl.WingsPersistence;
import software.wings.integration.IntegrationTestBase;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class StaticLimitVicinityCheckerMongoImplIntegrationTest extends IntegrationTestBase {
  private static final String NAMESPACE = StaticLimitVicinityCheckerMongoImplIntegrationTest.class.getSimpleName();

  @Inject private WingsPersistence persistence;

  @Before
  public void init() {
    Query<Counter> query = wingsPersistence.createQuery(Counter.class).field("key").endsWith(NAMESPACE);
    wingsPersistence.delete(query);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testCrossed() {
    String key = "some-key-" + NAMESPACE + ":" + ActionType.DEPLOY;
    Counter counter = new Counter(key, 10);
    wingsPersistence.save(counter);
    StaticLimitVicinityChecker checker = new StaticLimitVicinityCheckerMongoImpl(new StaticLimit(12), key, persistence);

    // 10/12 => 0.83
    assertThat(checker.hasCrossedPercentLimit(80)).isTrue();
    assertThat(checker.hasCrossedPercentLimit(85)).isFalse();
  }
}
