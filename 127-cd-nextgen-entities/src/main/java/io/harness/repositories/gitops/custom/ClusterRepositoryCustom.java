/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.gitops.custom;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.entity.Cluster;

import com.mongodb.client.result.DeleteResult;
import java.util.List;
import javax.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(GITOPS)
public interface ClusterRepositoryCustom {
  Page<Cluster> find(@NotNull Criteria criteria, @NotNull Pageable pageable);
  Cluster create(@NotNull Cluster cluster);
  long bulkCreate(@NotNull List<Cluster> cluster);
  Cluster update(@NotNull Criteria criteria, @NotNull Cluster cluster);
  DeleteResult delete(@NotNull Criteria criteria);
  Cluster findOne(@NotNull Criteria criteria);
}
