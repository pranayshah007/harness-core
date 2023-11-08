/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.backstagebeans.BackstageCatalogEntityTypes;
import io.harness.spec.server.idp.v1.model.ScorecardStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class ScorecardStatsMapper {
  public List<ScorecardStats> toDTO(Set<BackstageCatalogEntity> entities, Map<String, Integer> scoreMap) {
    List<ScorecardStats> scorecardStats = new ArrayList<>();
    for (BackstageCatalogEntity entity : entities) {
      ScorecardStats stats = new ScorecardStats();
      stats.setName(entity.getMetadata().getName());
      stats.setOwner(BackstageCatalogEntityTypes.getEntityOwner(entity));
      stats.setSystem(BackstageCatalogEntityTypes.getEntitySystem(entity));
      stats.setKind(entity.getKind());
      stats.setType(BackstageCatalogEntityTypes.getEntityType(entity));
      if (scoreMap.containsKey(entity.getMetadata().getUid())) {
        stats.setScore(scoreMap.get(entity.getMetadata().getUid()));
      }
      scorecardStats.add(stats);
    }
    return scorecardStats;
  }
}
