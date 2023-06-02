/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.cluster.dao.ClusterRecordDao;
import io.harness.ccm.cluster.entities.DirectKubernetesCluster;
import io.harness.ccm.commons.entities.ClusterRecord;
import io.harness.ccm.commons.service.intf.ClusterRecordService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ClusterHelperImplTest extends CategoryTest {
  @Mock private ClusterRecordService clusterRecordService;
  @Mock private ClusterRecordDao cgClusterRecordDao;
  @InjectMocks private ClusterHelperImpl clusterHelper;

  private static final String CLUSTER_ID = "clusterId";
  private static final String CLUSTER_NAME = "clusterName";

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testFetchClusterNamePresentInNG() throws Exception {
    when(clusterRecordService.get(eq(CLUSTER_ID)))
        .thenReturn(ClusterRecord.builder().clusterName(CLUSTER_NAME).build());

    assertThat(clusterHelper.fetchClusterName(CLUSTER_ID)).isEqualTo(CLUSTER_NAME);
    assertThat(clusterHelper.fetchClusterName(CLUSTER_ID)).isEqualTo(CLUSTER_NAME);

    verifyNoInteractions(cgClusterRecordDao);
    verify(clusterRecordService, times(1));
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testFetchClusterNamePresentInCG() throws Exception {
    when(clusterRecordService.get(any())).thenReturn(null);

    io.harness.ccm.cluster.entities.ClusterRecord clusterRecord =
        io.harness.ccm.cluster.entities.ClusterRecord.builder()
            .cluster(DirectKubernetesCluster.builder().clusterName(CLUSTER_NAME).build())
            .build();
    when(cgClusterRecordDao.get(eq(CLUSTER_ID))).thenReturn(clusterRecord);

    assertThat(clusterHelper.fetchClusterName(CLUSTER_ID)).isEqualTo(CLUSTER_NAME);
  }
}
