/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.integration.IntegrationTestBase;
import software.wings.service.impl.analysis.TimeSeriesKeyTransactions;
import software.wings.service.impl.analysis.TimeSeriesKeyTransactions.TimeSeriesKeyTransactionsKeys;

import com.google.common.collect.Sets;
import java.util.Arrays;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TimeSeriesResourceIntegrationTest extends IntegrationTestBase {
  @Test
  @Owner(developers = PRAVEEN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testGetKeyTransactions() {
    loginAdminUser();
    String cvConfigId = generateUuid();
    TimeSeriesKeyTransactions keyTransactions = TimeSeriesKeyTransactions.builder()
                                                    .cvConfigId(cvConfigId)
                                                    .keyTransactions(Sets.newHashSet("transaction1", "transaction2"))
                                                    .build();
    wingsPersistence.save(keyTransactions);

    WebTarget target =
        client.target(API_BASE + "/timeseries/key-transactions?accountId=" + accountId + "&cvConfigId=" + cvConfigId);
    RestResponse<TimeSeriesKeyTransactions> restResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<TimeSeriesKeyTransactions>>() {});
    assertThat(restResponse.getResource().getCvConfigId()).isEqualTo(cvConfigId);
    assertThat(
        restResponse.getResource().getKeyTransactions().containsAll(Sets.newHashSet("transaction1", "transaction2")))
        .isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testAddKeyTransactions() {
    loginAdminUser();
    String cvConfigId = generateUuid();

    WebTarget target = client.target(
        API_BASE + "/timeseries/add-to-key-transactions?accountId=" + accountId + "&cvConfigId=" + cvConfigId);
    RestResponse<Boolean> restResponse = getRequestBuilderWithAuthHeader(target).post(
        Entity.json(JsonUtils.asJson(Arrays.asList("transaction1", "transaction2"))),
        new GenericType<RestResponse<Boolean>>() {});
    assertThat(restResponse.getResource()).isTrue();

    TimeSeriesKeyTransactions keyTransactions = wingsPersistence.createQuery(TimeSeriesKeyTransactions.class)
                                                    .filter(TimeSeriesKeyTransactionsKeys.cvConfigId, cvConfigId)
                                                    .get();

    assertThat(keyTransactions).isNotNull();
    assertThat(keyTransactions.getCvConfigId()).isEqualTo(cvConfigId);
    assertThat(keyTransactions.getKeyTransactions().containsAll(Arrays.asList("transaction1", "transaction2")))
        .isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testRemoveKeyTransactions() {
    loginAdminUser();
    String cvConfigId = generateUuid();
    TimeSeriesKeyTransactions keyTransactions = TimeSeriesKeyTransactions.builder()
                                                    .cvConfigId(cvConfigId)
                                                    .keyTransactions(Sets.newHashSet("transaction1", "transaction2"))
                                                    .build();
    wingsPersistence.save(keyTransactions);

    WebTarget target = client.target(
        API_BASE + "/timeseries/remove-from-key-transactions?accountId=" + accountId + "&cvConfigId=" + cvConfigId);
    RestResponse<Boolean> restResponse = getRequestBuilderWithAuthHeader(target).post(
        Entity.json(JsonUtils.asJson(Arrays.asList("transaction1"))), new GenericType<RestResponse<Boolean>>() {});
    assertThat(restResponse.getResource()).isTrue();

    TimeSeriesKeyTransactions keyTransactionsFromDB = wingsPersistence.createQuery(TimeSeriesKeyTransactions.class)
                                                          .filter(TimeSeriesKeyTransactionsKeys.cvConfigId, cvConfigId)
                                                          .get();

    assertThat(keyTransactionsFromDB).isNotNull();
    assertThat(keyTransactionsFromDB.getCvConfigId()).isEqualTo(cvConfigId);
    assertThat(keyTransactionsFromDB.getKeyTransactions().containsAll(Arrays.asList("transaction2"))).isTrue();
  }
}
