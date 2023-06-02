/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateResults;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateTagsInServiceLevelObjectiveV2 implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Begin migration for adding Tags in ServiceLevelObjectivesV2");
    Query<AbstractServiceLevelObjective> serviceLevelObjectiveQuery =
        hPersistence.createQuery(AbstractServiceLevelObjective.class)
            .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.type, ServiceLevelObjectiveType.SIMPLE);
    try (HIterator<AbstractServiceLevelObjective> iterator = new HIterator<>(serviceLevelObjectiveQuery.fetch())) {
      while (iterator.hasNext()) {
        List<NGTag> tags;
        AbstractServiceLevelObjective serviceLevelObjective = iterator.next();
        tags = serviceLevelObjective.getTags() != null ? serviceLevelObjective.getTags() : new ArrayList<>();
        SimpleServiceLevelObjective simpleServiceLevelObjective = (SimpleServiceLevelObjective) serviceLevelObjective;
        tags.add(NGTag.builder()
                     .key("serviceLevelIndicatorType")
                     .value(simpleServiceLevelObjective.getServiceLevelIndicatorType().toString())
                     .build());
        UpdateResults updateResults = hPersistence.update(serviceLevelObjective,
            hPersistence.createUpdateOperations(AbstractServiceLevelObjective.class)
                .set(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.tags, tags));
        log.info("Updated tags in serviceLevelObjectivesV2 {}", updateResults);
      }
    }
  }

  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.NA;
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.NA;
  }
}
