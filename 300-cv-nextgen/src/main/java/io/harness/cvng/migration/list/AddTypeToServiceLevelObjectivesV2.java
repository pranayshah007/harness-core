package io.harness.cvng.migration.list;

import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateResults;

@Slf4j
public class AddTypeToServiceLevelObjectivesV2 implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Begin migration for adding Type in ServiceLevelObjectivesV2");
    Query<AbstractServiceLevelObjective> serviceLevelObjectiveQuery =
        hPersistence.createQuery(AbstractServiceLevelObjective.class)
            .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.type, null);
    try (HIterator<AbstractServiceLevelObjective> iterator = new HIterator<>(serviceLevelObjectiveQuery.fetch())) {
      while (iterator.hasNext()) {
        AbstractServiceLevelObjective serviceLevelObjective = iterator.next();
        UpdateResults updateResults = hPersistence.update(serviceLevelObjective,
            hPersistence.createUpdateOperations(AbstractServiceLevelObjective.class)
                .set(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.type, ServiceLevelObjectiveType.SIMPLE));
        log.info("Updated serviceLevelObjectivesV2 {}", updateResults);
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
