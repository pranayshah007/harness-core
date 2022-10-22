/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.cdng.gitops.beans.ClusterFromGitops;
import io.harness.cdng.gitops.beans.ClusterRequest;
import io.harness.cdng.gitops.beans.ClusterResponse;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ClusterEntityMapperTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testToEntity() {
    ClusterRequest request = ClusterRequest.builder()
                                 .identifier("id")
                                 .envRef("env")
                                 .orgIdentifier("orgId")
                                 .projectIdentifier("projectId")
                                 .build();

    Cluster entity = ClusterEntityMapper.toEntity("accountId", request);

    assertThat(entity.getAccountId()).isEqualTo("accountId");
    assertThat(entity.getOrgIdentifier()).isEqualTo("orgId");
    assertThat(entity.getProjectIdentifier()).isEqualTo("projectId");
    assertThat(entity.getClusterRef()).isEqualTo("id");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testWriteDTO() {
    long epochSecond = Instant.now().getEpochSecond();
    Cluster request = Cluster.builder()
                          .accountId("accountId")
                          .clusterRef("id")
                          .envRef("env")
                          .orgIdentifier("orgId")
                          .projectIdentifier("projectId")
                          .createdAt(epochSecond)
                          .build();

    ClusterResponse entity = ClusterEntityMapper.writeDTO(request);

    assertThat(entity.getOrgIdentifier()).isEqualTo("orgId");
    assertThat(entity.getProjectIdentifier()).isEqualTo("projectId");
    assertThat(entity.getClusterRef()).isEqualTo("id");
    assertThat(entity.getLinkedAt()).isEqualTo(epochSecond);
  }

  @Test
  @Owner(developers = OwnerRule.ROHITKARELIA)
  @Category(UnitTests.class)
  public void testWriteDTOCLusterRefIsCamelCase() {
    long epochSecond = Instant.now().getEpochSecond();
    Cluster request = Cluster.builder()
                          .accountId("accountId")
                          .clusterRef("idFromGitOps")
                          .envRef("env")
                          .orgIdentifier("orgId")
                          .projectIdentifier("projectId")
                          .createdAt(epochSecond)
                          .build();

    ClusterFromGitops fromGitOps =
        ClusterFromGitops.builder().name("idFromGitOps").scopeLevel(ScopeLevel.PROJECT).build();

    Map<String, ClusterFromGitops> clusterFromGitops = new HashMap<>();
    clusterFromGitops.put("idFromGitops", fromGitOps);

    ClusterResponse entity = ClusterEntityMapper.writeDTO(request, clusterFromGitops);

    assertThat(entity.getOrgIdentifier()).isEqualTo("orgId");
    assertThat(entity.getProjectIdentifier()).isEqualTo("projectId");
    assertThat(entity.getClusterRef()).isEqualTo("idFromGitOps");
    assertThat(entity.getLinkedAt()).isEqualTo(epochSecond);
  }

  @Test
  @Owner(developers = OwnerRule.ROHITKARELIA)
  @Category(UnitTests.class)
  public void testWriteDTOClusterRefWithUnderScore() {
    long epochSecond = Instant.now().getEpochSecond();
    Cluster request = Cluster.builder()
                          .accountId("accountId")
                          .clusterRef("id_From_GitOps")
                          .envRef("env")
                          .orgIdentifier("orgId")
                          .projectIdentifier("projectId")
                          .createdAt(epochSecond)
                          .build();

    ClusterFromGitops fromGitOps =
        ClusterFromGitops.builder().name("id_From_GitOps").scopeLevel(ScopeLevel.PROJECT).build();

    Map<String, ClusterFromGitops> clusterFromGitops = new HashMap<>();
    clusterFromGitops.put("id_From_GitOps", fromGitOps);

    ClusterResponse entity = ClusterEntityMapper.writeDTO(request, clusterFromGitops);

    assertThat(entity.getOrgIdentifier()).isEqualTo("orgId");
    assertThat(entity.getProjectIdentifier()).isEqualTo("projectId");
    assertThat(entity.getClusterRef()).isEqualTo("id_From_GitOps");
    assertThat(entity.getLinkedAt()).isEqualTo(epochSecond);
  }
}
