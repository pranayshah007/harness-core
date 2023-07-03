/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.syncstep;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.rule.OwnerRule.MEENA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.gitops.steps.GitopsClustersOutcome;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(GITOPS)
public class SyncRunnableTest extends CategoryTest {
  @InjectMocks private SyncRunnable syncRunnable;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetScopedClusterIdsInPipelineExecution() {
    GitopsClustersOutcome.ClusterData cluster1 =
        GitopsClustersOutcome.ClusterData.builder().clusterId("cid1").scope("project").agentId("agent1").build();
    GitopsClustersOutcome.ClusterData cluster2 =
        GitopsClustersOutcome.ClusterData.builder().clusterId("cid2").scope("account").agentId("agent2").build();
    GitopsClustersOutcome.ClusterData cluster3 =
        GitopsClustersOutcome.ClusterData.builder().clusterId("cid3").scope("ACCOUNT").agentId("agent3").build();
    GitopsClustersOutcome.ClusterData cluster4 =
        GitopsClustersOutcome.ClusterData.builder().clusterId("cid4").scope("ORGANIZATION").agentId("agent4").build();
    GitopsClustersOutcome.ClusterData cluster5 =
        GitopsClustersOutcome.ClusterData.builder().clusterId("cid5").scope("ORG").agentId("agent5").build();
    GitopsClustersOutcome.ClusterData cluster6 =
        GitopsClustersOutcome.ClusterData.builder().clusterId("cid6").scope("organization").agentId("agent6").build();

    assertThat(syncRunnable.getScopedClusterIdsInPipelineExecution(new GitopsClustersOutcome(
                   Arrays.asList(cluster1, cluster2, cluster3, cluster4, cluster5, cluster6))))
        .isEqualTo(new HashMap<String, Set<String>>() {
          {
            put("agent1", new HashSet<>(List.of("cid1")));
            put("agent2", new HashSet<>(List.of("account.cid2")));
            put("agent3", new HashSet<>(List.of("account.cid3")));
            put("agent4", new HashSet<>(List.of("org.cid4")));
            put("agent5", new HashSet<>(List.of("org.cid5")));
            put("agent6", new HashSet<>(List.of("org.cid6")));
          }
        });
  }
}
