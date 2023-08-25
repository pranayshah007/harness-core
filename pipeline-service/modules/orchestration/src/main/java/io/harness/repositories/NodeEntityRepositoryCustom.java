/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plan.NodeEntity;

import com.mongodb.client.result.DeleteResult;
import java.util.List;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
public interface NodeEntityRepositoryCustom {
  List<NodeEntity> findByPlanIdAndNodeIds(Query query);
  DeleteResult deleteAllByPlanIdNodeIds(Query query);
}
