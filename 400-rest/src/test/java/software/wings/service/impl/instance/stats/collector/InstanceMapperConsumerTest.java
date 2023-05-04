/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance.stats.collector;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.EntityType;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.stats.InstanceStatsSnapshot;

import com.google.common.base.Suppliers;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class InstanceMapperConsumerTest {
  @Value
  @AllArgsConstructor
  static class App {
    private String appId;
    private String appName;
  }

  private Supplier<List<InstanceMapperTest.App>> sampleApps = Suppliers.memoize(
      ()
          -> Arrays.asList(
              new InstanceMapperTest.App(RandomStringUtils.randomAlphabetic(5), RandomStringUtils.randomAlphabetic(5)),
              new InstanceMapperTest.App(
                  RandomStringUtils.randomAlphabetic(5), RandomStringUtils.randomAlphabetic(5))));

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testMapping() {
    Instant ts = Instant.now();
    String accountId = "some-account-id";
    InstanceMapperConsumer mapper = new InstanceMapperConsumer(ts, accountId);

    List<Instance> instances = getSampleInstance(accountId);
    instances.forEach(mapper);
    InstanceStatsSnapshot statsSnapshot = mapper.map(instances);

    assertThat(statsSnapshot.getTimestamp()).isEqualTo(ts);
    assertThat(statsSnapshot.getAccountId()).isEqualTo(accountId);
    assertThat(statsSnapshot.getTotal()).isEqualTo(instances.size());
    assertThat(statsSnapshot.getAggregateCounts()).hasSize(2);
    assertThat(
        statsSnapshot.getAggregateCounts().stream().filter(s -> s.getEntityType() == EntityType.APPLICATION).count())
        .isEqualTo(2);

    List<InstanceMapperTest.App> apps = statsSnapshot.getAggregateCounts()
                                            .stream()
                                            .filter(it -> it.getEntityType() == EntityType.APPLICATION)
                                            .map(it -> new InstanceMapperTest.App(it.getId(), it.getName()))
                                            .collect(Collectors.toList());

    assertThat(apps.containsAll(sampleApps.get())).isTrue();
    assertThat(
        statsSnapshot.getAggregateCounts().stream().filter(it -> it.getEntityType() == EntityType.SERVICE).count())
        .isEqualTo(0);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testMappingWithEmptySet() {
    Instant ts = Instant.now();
    String accountId = "some-account-id";
    InstanceMapperConsumer mapper = new InstanceMapperConsumer(ts, accountId);

    List<Instance> instances = Collections.emptyList();
    InstanceStatsSnapshot statsSnapshot = mapper.map(instances);

    assertThat(statsSnapshot.getTimestamp()).isEqualTo(ts);
    assertThat(statsSnapshot.getAccountId()).isEqualTo(accountId);
    assertThat(statsSnapshot.getTotal()).isEqualTo(instances.size());
    assertThat(statsSnapshot.getAggregateCounts()).isEmpty();
    assertThat(
        statsSnapshot.getAggregateCounts().stream().filter(it -> it.getEntityType() == EntityType.APPLICATION).count())
        .isEqualTo(0);

    List<InstanceMapperTest.App> apps = statsSnapshot.getAggregateCounts()
                                            .stream()
                                            .filter(it -> it.getEntityType() == EntityType.APPLICATION)
                                            .map(it -> new InstanceMapperTest.App(it.getId(), it.getName()))
                                            .collect(Collectors.toList());

    assertThat(apps.isEmpty()).isTrue();
    assertThat(
        statsSnapshot.getAggregateCounts().stream().filter(it -> it.getEntityType() == EntityType.SERVICE).count())
        .isEqualTo(0);
  }

  private List<Instance> getSampleInstance(String accountId) {
    return sampleApps.get()
        .stream()
        .map(it
            -> Instance.builder()
                   .accountId(accountId)
                   .appId(it.getAppId())
                   .appName(it.getAppName())
                   .serviceId("some-service-id")
                   .serviceName("some-service-name")
                   .build())
        .collect(Collectors.toList());
  }
}
