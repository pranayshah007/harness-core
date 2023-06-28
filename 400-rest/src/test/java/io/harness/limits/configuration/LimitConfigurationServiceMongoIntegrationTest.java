/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.limits.configuration;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.limits.ActionType.CREATE_APPLICATION;
import static io.harness.limits.ActionType.MAX_QPM_PER_MANAGER;
import static io.harness.rule.OwnerRule.RAGHAV_MURALI;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.limits.ConfiguredLimit;
import io.harness.limits.ConfiguredLimit.ConfiguredLimitKeys;
import io.harness.limits.impl.model.RateLimit;
import io.harness.limits.impl.model.StaticLimit;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.integration.IntegrationTestBase;
import software.wings.integration.IntegrationTestUtils;

import com.google.inject.Inject;
import dev.morphia.Datastore;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class LimitConfigurationServiceMongoIntegrationTest extends IntegrationTestBase {
  @Inject private LimitConfigurationServiceMongo configuredLimitService;
  @Inject private HPersistence dao;

  // namespacing accountId with class name to prevent collision with other tests
  private static final String SOME_ACCOUNT_ID =
      "some-account-id-" + LimitConfigurationServiceMongoIntegrationTest.class.getSimpleName();
  private static final String GLOBAL_ACCOUNT_ID =
      "global-account-id-" + LimitConfigurationServiceMongoIntegrationTest.class.getSimpleName();

  private boolean indexesEnsured;

  @Before
  public void ensureIndices() throws Exception {
    if (!indexesEnsured && !IntegrationTestUtils.isManagerRunning(client)) {
      dao.getDatastore(ConfiguredLimit.class).ensureIndexes(ConfiguredLimit.class);
      indexesEnsured = true;
    }
  }

  @After
  public void clearCollection() {
    Datastore ds = dao.getDatastore(ConfiguredLimit.class);
    ds.delete(ds.createQuery(ConfiguredLimit.class).filter(ConfiguredLimitKeys.accountId, SOME_ACCOUNT_ID));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testSaveAndGet() {
    ConfiguredLimit<StaticLimit> cl = new ConfiguredLimit<>(SOME_ACCOUNT_ID, new StaticLimit(10), CREATE_APPLICATION);
    boolean configured = configuredLimitService.configure(cl.getAccountId(), CREATE_APPLICATION, cl.getLimit());
    assertThat(configured).isTrue();

    ConfiguredLimit fetchedValue = configuredLimitService.get(SOME_ACCOUNT_ID, CREATE_APPLICATION);
    assertThat(fetchedValue).isEqualTo(cl);

    fetchedValue = configuredLimitService.get("non-existent-id", CREATE_APPLICATION);
    assertThat(fetchedValue).isNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testUpsert() {
    String accountId = SOME_ACCOUNT_ID;
    boolean configured = configuredLimitService.configure(accountId, CREATE_APPLICATION, new StaticLimit(10));
    assertThat(configured).isTrue();
    ConfiguredLimit fetchedValue = configuredLimitService.get(accountId, CREATE_APPLICATION);
    assertThat(fetchedValue.getLimit()).isEqualTo(new StaticLimit(10));

    configured = configuredLimitService.configure(accountId, CREATE_APPLICATION, new StaticLimit(20));
    assertThat(configured).isTrue();
    fetchedValue = configuredLimitService.get(accountId, CREATE_APPLICATION);
    assertThat(fetchedValue.getLimit()).isEqualTo(new StaticLimit(20));

    configured = configuredLimitService.configure(accountId, CREATE_APPLICATION, new StaticLimit(30));
    assertThat(configured).isTrue();
    fetchedValue = configuredLimitService.get(accountId, CREATE_APPLICATION);
    assertThat(fetchedValue.getLimit()).isEqualTo(new StaticLimit(30));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testUpsertForRateLimits() {
    String accountId = SOME_ACCOUNT_ID;

    RateLimit limit = new RateLimit(10, 2, TimeUnit.MINUTES);

    ConfiguredLimit<RateLimit> cl = new ConfiguredLimit<>(accountId, limit, CREATE_APPLICATION);
    boolean configured = configuredLimitService.configure(accountId, CREATE_APPLICATION, cl.getLimit());
    assertThat(configured).isTrue();
    ConfiguredLimit fetchedValue = configuredLimitService.get(accountId, CREATE_APPLICATION);
    assertThat(fetchedValue).isEqualTo(cl);

    limit = new RateLimit(20, 22, TimeUnit.MINUTES);

    cl = new ConfiguredLimit<>(accountId, limit, CREATE_APPLICATION);
    configured = configuredLimitService.configure(accountId, CREATE_APPLICATION, cl.getLimit());
    assertThat(configured).isTrue();
    fetchedValue = configuredLimitService.get(accountId, CREATE_APPLICATION);
    assertThat(fetchedValue).isEqualTo(cl);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testgetOrDefaultToGlobal_ReturnsGivenAccountLimit() {
    String accountId = SOME_ACCOUNT_ID;
    String globalAccountId = GLOBAL_ACCOUNT_ID;

    StaticLimit accountLimit = new StaticLimit(10);
    StaticLimit globalLimit = new StaticLimit(20);
    ConfiguredLimit<StaticLimit> cl = new ConfiguredLimit<>(accountId, accountLimit, MAX_QPM_PER_MANAGER);
    boolean configured = configuredLimitService.configure(accountId, MAX_QPM_PER_MANAGER, cl.getLimit());
    assertThat(configured).isTrue();
    ConfiguredLimit<StaticLimit> gcl = new ConfiguredLimit<>(globalAccountId, globalLimit, MAX_QPM_PER_MANAGER);
    configured = configuredLimitService.configure(globalAccountId, MAX_QPM_PER_MANAGER, gcl.getLimit());
    assertThat(configured).isTrue();
    ConfiguredLimit fetchedValue =
        configuredLimitService.getOrDefaultToGlobal(accountId, globalAccountId, MAX_QPM_PER_MANAGER);
    assertThat(fetchedValue).isEqualTo(cl);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testgetOrDefaultToGlobal_ReturnsGlobalAccountLimit() {
    String accountId = SOME_ACCOUNT_ID;
    String globalAccountId = GLOBAL_ACCOUNT_ID;

    StaticLimit globalLimit = new StaticLimit(20);
    ConfiguredLimit<StaticLimit> gcl = new ConfiguredLimit<>(globalAccountId, globalLimit, MAX_QPM_PER_MANAGER);
    boolean configured = configuredLimitService.configure(globalAccountId, MAX_QPM_PER_MANAGER, gcl.getLimit());
    assertThat(configured).isTrue();
    ConfiguredLimit fetchedValue =
        configuredLimitService.getOrDefaultToGlobal(accountId, globalAccountId, MAX_QPM_PER_MANAGER);
    assertThat(fetchedValue).isEqualTo(gcl);
  }
}
