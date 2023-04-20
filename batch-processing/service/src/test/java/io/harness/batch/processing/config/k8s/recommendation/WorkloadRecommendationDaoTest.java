/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.recommendation.ResourceId;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings({"rawtypes", "unchecked"})
@RunWith(MockitoJUnitRunner.class)
public class WorkloadRecommendationDaoTest extends CategoryTest {
  public static final String ACCOUNT_ID = "px7xd_BFRCi-pfWPYXVjvw";
  public static final String CLUSTER_ID = "5ed0e57eb1c5694f54bc5517";
  public static final String NAMESPACE = "kube-system";
  public static final String NAME = "kube-dns";
  public static final String KIND = "Deplyment";
  @InjectMocks private WorkloadRecommendationDao workloadRecommendationDao;
  @Mock HPersistence hPersistence;

  @Mock private Query<K8sWorkloadRecommendation> query;
  @Mock private FieldEnd fieldEnd;

  @Before
  public void setUp() throws Exception {
    when(hPersistence.createQuery(K8sWorkloadRecommendation.class)).thenReturn(query);
    when(query.field(any())).thenReturn(fieldEnd);
    when(fieldEnd.equal(any())).thenReturn(query);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testFetchRecommendationForWorkload() throws Exception {
    ResourceId workloadId = ResourceId.builder()
                                .accountId(ACCOUNT_ID)
                                .clusterId(CLUSTER_ID)
                                .namespace(NAMESPACE)
                                .name(NAME)
                                .kind(KIND)
                                .build();
    when(query.get())
        .thenReturn(K8sWorkloadRecommendation.builder()
                        .accountId(ACCOUNT_ID)
                        .clusterId(CLUSTER_ID)
                        .namespace(NAMESPACE)
                        .workloadName(NAME)
                        .workloadType(KIND)
                        .containerRecommendations(new HashMap<>())
                        .containerCheckpoints(new HashMap<>())
                        .build());
    K8sWorkloadRecommendation recommendation = workloadRecommendationDao.fetchRecommendationForWorkload(workloadId);
    assertThat(recommendation)
        .isEqualTo(K8sWorkloadRecommendation.builder()
                       .accountId(ACCOUNT_ID)
                       .clusterId(CLUSTER_ID)
                       .namespace(NAMESPACE)
                       .workloadName(NAME)
                       .workloadType(KIND)
                       .containerRecommendations(new HashMap<>())
                       .containerCheckpoints(new HashMap<>())
                       .build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testFetchRecommendationForWorkloadNotPresent() throws Exception {
    when(query.get()).thenReturn(null);
    ResourceId workloadId = ResourceId.builder()
                                .accountId(ACCOUNT_ID)
                                .clusterId(CLUSTER_ID)
                                .namespace(NAMESPACE)
                                .name(NAME)
                                .kind(KIND)
                                .build();
    K8sWorkloadRecommendation recommendation = workloadRecommendationDao.fetchRecommendationForWorkload(workloadId);
    assertThat(recommendation)
        .isEqualTo(K8sWorkloadRecommendation.builder()
                       .accountId(ACCOUNT_ID)
                       .clusterId(CLUSTER_ID)
                       .namespace(NAMESPACE)
                       .workloadName(NAME)
                       .workloadType(KIND)
                       .containerRecommendations(new HashMap<>())
                       .containerCheckpoints(new HashMap<>())
                       .build());
  }
}
