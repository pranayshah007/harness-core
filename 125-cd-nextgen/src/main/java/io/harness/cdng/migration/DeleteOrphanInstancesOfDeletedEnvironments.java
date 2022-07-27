package io.harness.cdng.migration;

import static software.wings.beans.AccountType.log;

import io.harness.entities.Instance;
import io.harness.entities.Instance.InstanceKeys;
import io.harness.migration.NGMigration;
import io.harness.mongo.MongoPersistence;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import org.mongodb.morphia.query.Query;

public class DeleteOrphanInstancesOfDeletedEnvironments implements NGMigration {
  @Inject private HPersistence persistence;
  @Inject private MongoPersistence mongoPersistence;

  @Override
  public void migrate() {
    log.info(
        "DeletedEnvironmentInstancesCleanupMigration: Running duplicate delete migration for customer Kareo (Account ID: PcCzPoKDRjyi_TuERV9VCQ Cluster: prod-2)");
    String accountIdforDeletion = "PcCzPoKDRjyi_TuERV9VCQ";
    Query<Environment> environmentQuery = persistence.createQuery(Environment.class)
                                              .filter(EnvironmentKeys.deleted, true)
                                              .filter(EnvironmentKeys.accountId, accountIdforDeletion);
    try (HIterator<Environment> environments = new HIterator<>(environmentQuery.fetch())) {
      for (Environment environment : environments) {
        log.info("DeletedEnvironmentInstancesCleanupMigration: deleting instances of environment:{} for account ID :{}",
            environment.getName(), environment.getAccountId());
        deleteInstance(environment);
      }
    }
  }

  private void deleteInstance(Environment environment) {
    try {
      log.info("DeletedEnvironmentInstancesCleanupMigration: deleting Instances");
      BasicDBObject basicDBObject = new BasicDBObject().append(InstanceKeys.envIdentifier, environment.getIdentifier());
      BasicDBObject updateOps =
          new BasicDBObject(InstanceKeys.isDeleted, true).append(InstanceKeys.deletedAt, System.currentTimeMillis());

      BulkWriteOperation instanceWriteOperation =
          mongoPersistence.getCollection(Instance.class).initializeUnorderedBulkOperation();
      instanceWriteOperation.find(basicDBObject).update(new BasicDBObject("$set", updateOps));
      BulkWriteResult updateOperationResult = instanceWriteOperation.execute();
      log.info("DeletedEnvironmentInstancesCleanupMigration: instances deleted successfully.");

    } catch (Exception ex) {
      log.error(
          "DeletedEnvironmentInstancesCleanupMigration: Unexpected error occurred while migrating Instances for deleted environment",
          ex);
    }
  }
}
