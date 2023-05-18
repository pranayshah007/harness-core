/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.support;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.service.intfc.WorkloadRepository;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class K8SWorkloadServiceTest extends CategoryTest {
  @InjectMocks private K8SWorkloadService k8SWorkloadService;
  @Mock private WorkloadRepository workloadRepository;

  private static final String CLUSTER_ID = "clusterId";
  private static final String ACCOUNT_ID = "accountId";
  private static final String WORKLOAD_NAME = "workloadName";
  private static final String NAMESPACE = "namespace";
  private static final Map<String, String> LABELS = ImmutableMap.of("key1", "value1", "key2", "value2");

  private static final K8SWorkloadService.CacheKey CACHE_KEY =
      new K8SWorkloadService.CacheKey(ACCOUNT_ID, CLUSTER_ID, NAMESPACE, null);

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetK8sWorkloadLabel() {
    when(workloadRepository.getWorkload(ACCOUNT_ID, CLUSTER_ID, NAMESPACE, ImmutableSet.of(WORKLOAD_NAME)))
        .thenReturn(ImmutableList.of(getK8sWorkload()));
    k8SWorkloadService.updateK8sWorkloadLabelCache(CACHE_KEY, ImmutableSet.of(WORKLOAD_NAME));
    Map<String, String> k8sWorkloadLabel =
        k8SWorkloadService.getK8sWorkloadLabel(ACCOUNT_ID, CLUSTER_ID, NAMESPACE, WORKLOAD_NAME);
    assertThat(k8sWorkloadLabel).containsExactlyEntriesOf(LABELS);
  }

  private K8sWorkload getK8sWorkload() {
    return K8sWorkload.builder()
        .accountId(ACCOUNT_ID)
        .clusterId(CLUSTER_ID)
        .namespace(NAMESPACE)
        .name(WORKLOAD_NAME)
        .labels(LABELS)
        .build();
  }
}
