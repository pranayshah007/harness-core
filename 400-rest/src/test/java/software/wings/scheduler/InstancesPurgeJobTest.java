/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.rule.OwnerRule.ANKIT;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceBuilder;
import software.wings.beans.infrastructure.instance.stats.InstanceStatsSnapshot;
import software.wings.beans.infrastructure.instance.stats.ServerlessInstanceStats;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class InstancesPurgeJobTest extends WingsBaseTest {
  private static final int MAX_INSTANCES = 100;
  private static final int MAX_INSTANCE_STATS = 500;

  private static final int MONTHS_INSTANCES_DATA_TO_GENERATE_EXCLUDING_CURRENT_MONTH = 11;
  private static final int MONTHS_INSTANCE_STATS_DATA_TO_GENERATE_EXCLUDING_CURRENT_MONTH = 11;

  @Inject private HPersistence persistence;

  @Inject @InjectMocks private InstancesPurgeJob job;

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void shouldPurgeCorrectly() {
    generateAccount();
    generateInstances();
    generateInstanceStats();
    generateServerlessInstanceStats();

    List<Instance> allInstances = persistence.createQuery(Instance.class).asList();
    List<InstanceStatsSnapshot> allInstanceStats = persistence.createQuery(InstanceStatsSnapshot.class).asList();
    List<ServerlessInstanceStats> allServerlessInstanceStats =
        persistence.createQuery(ServerlessInstanceStats.class).asList();

    List<Instance> instancesThatShouldBeRetained =
        allInstances.stream()
            .filter(instance
                -> !instance.isDeleted() || instance.getDeletedAt() >= job.getRetentionStartTime(2).toEpochMilli())
            .collect(toList());

    List<InstanceStatsSnapshot> instanceStatsThatShouldBeRetained =
        allInstanceStats.stream()
            .filter(stat -> stat.getTimestamp().toEpochMilli() >= job.getRetentionStartTime(5).toEpochMilli())
            .collect(toList());

    List<ServerlessInstanceStats> serverlessInstanceStatsThatShouldBeRetained =
        allServerlessInstanceStats.stream()
            .filter(stat -> stat.getTimestamp().toEpochMilli() >= job.getRetentionStartTime(5).toEpochMilli())
            .collect(toList());

    job.purge();

    List<Instance> retainedInstances = persistence.createQuery(Instance.class).asList();
    List<InstanceStatsSnapshot> retainedInstanceStats = persistence.createQuery(InstanceStatsSnapshot.class).asList();
    List<ServerlessInstanceStats> retainedServerlessInstanceStats =
        persistence.createQuery(ServerlessInstanceStats.class).asList();

    assertThat(retainedInstances).isEqualTo(instancesThatShouldBeRetained);
    assertThat(retainedInstanceStats).isEqualTo(instanceStatsThatShouldBeRetained);
    assertThat(retainedServerlessInstanceStats).isEqualTo(serverlessInstanceStatsThatShouldBeRetained);
  }

  private void generateInstances() {
    Instant startInstant = LocalDate.now(ZoneOffset.UTC)
                               .minusMonths(MONTHS_INSTANCES_DATA_TO_GENERATE_EXCLUDING_CURRENT_MONTH)
                               .with(TemporalAdjusters.firstDayOfMonth())
                               .atStartOfDay()
                               .toInstant(ZoneOffset.UTC);
    Instant endInstant = Instant.now();

    List<Instance> instances = Stream.generate(() -> createInstanceInTimeRange(startInstant, endInstant))
                                   .limit(MAX_INSTANCES)
                                   .collect(toList());
    persistence.save(instances);
  }

  private void generateAccount() {
    Account account = Account.Builder.anAccount()
                          .withUuid("DummyAccountId")
                          .withCompanyName("Dummy")
                          .withAccountName("Dummy")
                          .build();
    persistence.save(account);
  }

  private Instance createInstanceInTimeRange(Instant startInstant, Instant endInstant) {
    InstanceBuilder builder = Instance.builder().accountId("DummyAccountId").appId("DummyAppId");
    if (ThreadLocalRandom.current().nextBoolean()) {
      builder.isDeleted(true).deletedAt(
          ThreadLocalRandom.current().nextLong(startInstant.toEpochMilli(), endInstant.toEpochMilli()));
    } else {
      builder.isDeleted(false);
    }
    return builder.build();
  }

  private void generateInstanceStats() {
    Instant startInstant = LocalDate.now(ZoneOffset.UTC)
                               .minusMonths(MONTHS_INSTANCE_STATS_DATA_TO_GENERATE_EXCLUDING_CURRENT_MONTH)
                               .with(TemporalAdjusters.firstDayOfMonth())
                               .atStartOfDay()
                               .toInstant(ZoneOffset.UTC);
    Instant endInstant = Instant.now();

    List<InstanceStatsSnapshot> instanceStats =
        Stream.generate(() -> createInstanceStatInTimeRange(startInstant, endInstant))
            .limit(MAX_INSTANCE_STATS)
            .collect(toList());

    persistence.save(instanceStats);
  }

  private void generateServerlessInstanceStats() {
    Instant startInstant = LocalDate.now(ZoneOffset.UTC)
                               .minusMonths(MONTHS_INSTANCE_STATS_DATA_TO_GENERATE_EXCLUDING_CURRENT_MONTH)
                               .with(TemporalAdjusters.firstDayOfMonth())
                               .atStartOfDay()
                               .toInstant(ZoneOffset.UTC);
    Instant endInstant = Instant.now();

    List<ServerlessInstanceStats> serverlessInstanceStats =
        Stream.generate(() -> createServerlessInstanceStatInTimeRange(startInstant, endInstant))
            .limit(MAX_INSTANCE_STATS)
            .collect(toList());

    persistence.save(serverlessInstanceStats);
  }

  private InstanceStatsSnapshot createInstanceStatInTimeRange(Instant startInstant, Instant endInstant) {
    return new InstanceStatsSnapshot(Instant.ofEpochMilli(ThreadLocalRandom.current().nextLong(
                                         startInstant.toEpochMilli(), endInstant.toEpochMilli())),
        "DummyAccountId", Collections.emptyList());
  }

  private ServerlessInstanceStats createServerlessInstanceStatInTimeRange(Instant startInstant, Instant endInstant) {
    return new ServerlessInstanceStats(Instant.ofEpochMilli(ThreadLocalRandom.current().nextLong(
                                           startInstant.toEpochMilli(), endInstant.toEpochMilli())),
        "DummyAccountId", Collections.emptyList());
  }
}
