/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.backstagebeans.BackstageCatalogEntityTypes;
import io.harness.idp.scorecard.checks.entity.CheckStatusEntity;
import io.harness.spec.server.idp.v1.model.CheckGraph;
import io.harness.spec.server.idp.v1.model.CheckStats;
import io.harness.spec.server.idp.v1.model.CheckStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class CheckStatsMapper {
  public List<CheckStats> toDTO(Set<BackstageCatalogEntity> entities, Map<String, CheckStatus.StatusEnum> statusMap) {
    List<CheckStats> checkStats = new ArrayList<>();
    for (BackstageCatalogEntity entity : entities) {
      String entityId = entity.getMetadata().getUid();
      if (!statusMap.containsKey(entityId) || (statusMap.containsKey(entityId) && statusMap.get(entityId) == null)) {
        continue;
      }
      CheckStats stats = new CheckStats();
      stats.setName(entity.getMetadata().getName());
      stats.setOwner(BackstageCatalogEntityTypes.getEntityOwner(entity));
      stats.setSystem(BackstageCatalogEntityTypes.getEntitySystem(entity));
      stats.setKind(entity.getKind());
      stats.setType(BackstageCatalogEntityTypes.getEntityType(entity));
      stats.setStatus(String.valueOf(statusMap.get(entityId)));
      checkStats.add(stats);
    }
    return checkStats;
  }

  public List<CheckGraph> toDTO(List<CheckStatusEntity> checkStatusEntities) {
    List<CheckGraph> checkGraphs = new ArrayList<>();
    for (CheckStatusEntity checkStatusEntity : checkStatusEntities) {
      CheckGraph checkGraph = new CheckGraph();
      checkGraph.setCount(checkStatusEntity.getPassCount());
      checkGraph.setTimestamp(checkStatusEntity.getTimestamp());
      checkGraphs.add(checkGraph);
    }
    return checkGraphs;
  }
}
