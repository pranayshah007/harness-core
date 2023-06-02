/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plan.NodeEntity;

import java.util.List;
import java.util.Set;
import org.springframework.data.repository.CrudRepository;

@OwnedBy(PIPELINE)
@HarnessRepo
public interface NodeEntityRepository extends CrudRepository<NodeEntity, String> {
  // Deleting nodeEntity with uuid in given set
  // Uses - id index
  void deleteAllByUuidIn(Set<String> ids);
  List<NodeEntity> findNodeEntityByPlanId(String planId);
}
