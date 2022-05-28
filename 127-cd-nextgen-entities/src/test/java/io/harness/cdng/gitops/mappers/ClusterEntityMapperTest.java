/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.gitops.beans.ClusterRequest;
import io.harness.cdng.gitops.beans.ClusterResponse;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

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
                                 .projectIdentifier("orgId")
                                 .build();

    Cluster entity = ClusterEntityMapper.toEntity("accountId", request);

    assertThat(entity.getAccountId()).isEqualTo("accountId");
    assertThat(entity.getOrgIdentifier()).isEqualTo("orgId");
    assertThat(entity.getProjectIdentifier()).isEqualTo("orgId");
    assertThat(entity.getClusterRef()).isEqualTo("id");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testWriteDTO() {
    Cluster request = Cluster.builder()
                          .accountId("accountId")
                          .clusterRef("id")
                          .envRef("env")
                          .orgIdentifier("orgId")
                          .projectIdentifier("orgId")
                          .build();

    ClusterResponse entity = ClusterEntityMapper.writeDTO(request);

    assertThat(entity.getOrgIdentifier()).isEqualTo("orgId");
    assertThat(entity.getProjectIdentifier()).isEqualTo("orgId");
    assertThat(entity.getClusterRef()).isEqualTo("id");
  }
}
