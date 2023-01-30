/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance.stats.collector;

import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.persistence.HPersistence;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceKeys;
import software.wings.beans.infrastructure.instance.stats.InstanceStatsSnapshot;
import software.wings.beans.infrastructure.instance.stats.InstanceStatsSnapshot.InstanceStatsSnapshotKeys;
import software.wings.integration.IntegrationTestBase;
import software.wings.resources.stats.model.InstanceTimeline;
import software.wings.resources.stats.model.InstanceTimeline.DataPoint;
import software.wings.service.intfc.instance.stats.InstanceStatService;
import software.wings.utils.WingsTestConstants;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import dev.morphia.Datastore;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * End-to-End test which tests:
 * 1. Instance stats are correctly saved
 * 2. Timeline is correctly fetched.
 *
 */
@Slf4j
public class StatsCollectorImplIntegrationTest extends IntegrationTestBase {
  @Inject private HPersistence persistence;
  @Inject private StatsCollectorImpl statsCollector;
  @Inject private InstanceStatService statService;

  private boolean ensureIndices;
  private static final String SOME_APP_NAME =
      "some-app-name-" + StatsCollectorImplIntegrationTest.class.getSimpleName();

  @Before
  public void init() throws Exception {
    super.setUp();
    loginAdminUser();

    if (!ensureIndices) {
      Datastore ds = persistence.getDatastore(InstanceStatsSnapshot.class);
      this.cleanUp();
      ds.ensureIndexes(InstanceStatsSnapshot.class);
      ensureIndices = true;
    }
  }

  @After
  public void cleanUp() {
    Datastore ds = persistence.getDatastore(Instance.class);
    ds.delete(ds.createQuery(Instance.class)
                  .filter(InstanceKeys.accountId, WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID)
                  .filter(InstanceKeys.appName, SOME_APP_NAME));
    ds.delete(ds.createQuery(InstanceStatsSnapshot.class)
                  .filter(InstanceStatsSnapshotKeys.accountId, WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testCreateStats() throws URISyntaxException {
    Datastore ds = persistence.getDatastore(Instance.class);

    Instant start = Instant.now();
    String accountId = WingsTestConstants.INTEGRATION_TEST_ACCOUNT_ID;

    // setup: save instances
    List<Instance> instances = Arrays.asList(sampleInstance(accountId), sampleInstance(accountId));
    instances.forEach(instance -> wingsPersistence.save(instance));

    // call createStats
    long count =
        ds.getCount(ds.createQuery(InstanceStatsSnapshot.class).filter(InstanceStatsSnapshotKeys.accountId, accountId));
    Instant instant = Instant.now();
    boolean success = statsCollector.createStats(accountId, instant);
    long finalCount =
        ds.getCount(ds.createQuery(InstanceStatsSnapshot.class).filter(InstanceStatsSnapshotKeys.accountId, accountId));
    Instant lastTs = statService.getLastSnapshotTime(accountId);
    assertThat(lastTs).isEqualTo(instant);
    assertThat(finalCount).isEqualTo(count + 1);
    assertThat(success).isTrue();

    // delete all instances
    ds.delete(ds.createQuery(Instance.class));
    instant = Instant.now();

    // call CreateStats again
    success = statsCollector.createStats(accountId, instant);
    assertThat(success).isTrue();
    lastTs = statService.getLastSnapshotTime(accountId);
    assertThat(lastTs).isEqualTo(instant);
    long countAfter =
        ds.getCount(ds.createQuery(InstanceStatsSnapshot.class).filter(InstanceStatsSnapshotKeys.accountId, accountId));
    assertThat(countAfter).isEqualTo(finalCount + 1);

    Instant end = Instant.now();

    // now hit rest api to get the timeline
    String url = buildAbsoluteUrl("/api/dash-stats/timeline",
        ImmutableMap.of("accountId", accountId, "fromTsMillis", String.valueOf(start.toEpochMilli()), "toTsMillis",
            String.valueOf(end.toEpochMilli())));

    log.info("Timeline Url: {}", url);
    WebTarget target = client.target(url);

    RestResponse<InstanceTimeline> restResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<InstanceTimeline>>() {});
    InstanceTimeline timeline = restResponse.getResource();
    assertThat(timeline.getPoints().size()).isEqualTo(2);

    DataPoint firstPoint = timeline.getPoints().get(0);

    // Commenting the assertions temporarily
    //    assertThat( firstPoint.getTotal()).isEqualTo(instances.size());

    //    DataPoint secondPoint = timeline.getPoints().get(1);
    //    assertThat(    //        secondPoint.getTotal()).isEqualTo("all the instances were deleted before second
    //    createStats call, so total should be zero", 0);
  }

  private Instance sampleInstance(String accountId) {
    return Instance.builder()
        .accountId(accountId)
        .serviceId("svc-" + RandomStringUtils.randomAlphanumeric(6))
        .infraMappingId("ifm-" + RandomStringUtils.randomAlphanumeric(6))
        .envId("env-" + RandomStringUtils.randomAlphanumeric(6))
        .envName("envName-" + RandomStringUtils.randomAlphanumeric(6))
        .appId("appId-" + RandomStringUtils.randomAlphanumeric(6))
        .appName(SOME_APP_NAME)
        .build();
  }

  private String buildAbsoluteUrl(String path, Map<String, String> params) throws URISyntaxException {
    try {
      URIBuilder uriBuilder = new URIBuilder();
      String scheme = StringUtils.isBlank(System.getenv().get("BASE_HTTP")) ? "https" : "http";
      uriBuilder.setScheme(scheme);
      uriBuilder.setHost("localhost");
      uriBuilder.setPort(9090);
      uriBuilder.setPath(path);
      if (params != null) {
        params.forEach((name, value) -> uriBuilder.addParameter(name, value.toString()));
      }
      return uriBuilder.build().toString();
    } catch (URISyntaxException uriSyntaxException) {
      log.error("Either the path or the baseUrl are probably incorrect.");
      throw uriSyntaxException;
    }
  }
}
