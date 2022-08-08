/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.deploymentsummary;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.entities.DeploymentSummary;
import io.harness.entities.DeploymentSummary.DeploymentSummaryKeys;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class DeploymentSummaryCustomImplTest extends InstancesTestBase {
  private final String INSTANCE_SYNC_KEY = "instanceSyncKey";
  @Mock MongoTemplate mongoTemplate;
  @InjectMocks DeploymentSummaryCustomImpl deploymentSummaryCustom;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void fetchNthRecordFromNowTest() {
    int N = 5;
    Criteria criteria = Criteria.where(DeploymentSummaryKeys.instanceSyncKey).is(INSTANCE_SYNC_KEY);
    Query query = new Query().addCriteria(criteria);
    query.with(Sort.by(Sort.Direction.DESC, DeploymentSummaryKeys.createdAt));
    query.skip((long) N - 1);
    query.limit(1);
    DeploymentSummary deploymentSummary = DeploymentSummary.builder().build();
    List<DeploymentSummary> deploymentSummaryList = Arrays.asList(deploymentSummary);
    when(mongoTemplate.find(query, DeploymentSummary.class)).thenReturn(deploymentSummaryList);
    assertThat(deploymentSummaryCustom.fetchNthRecordFromNow(N, INSTANCE_SYNC_KEY).get()).isEqualTo(deploymentSummary);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFetchNthRecordFromNowWhenDocumentsAreNotPresent() {
    int n = 5;
    Criteria criteria = Criteria.where(DeploymentSummaryKeys.instanceSyncKey).is(INSTANCE_SYNC_KEY);
    Query query = new Query().addCriteria(criteria);
    query.with(Sort.by(Sort.Direction.DESC, DeploymentSummaryKeys.createdAt));
    query.skip((long) n - 1);
    query.limit(1);
    when(mongoTemplate.find(query, DeploymentSummary.class)).thenReturn(Collections.emptyList());
    Optional<DeploymentSummary> record = deploymentSummaryCustom.fetchNthRecordFromNow(n, INSTANCE_SYNC_KEY);
    assertFalse(record.isPresent());
  }
}
