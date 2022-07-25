package io.harness.cdng.migration;

import static software.wings.beans.AccountType.log;

import io.harness.entities.Instance;
import io.harness.entities.Instance.InstanceKeys;
import io.harness.migration.NGMigration;
import io.harness.mongo.MongoPersistence;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import software.wings.beans.Account;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import java.time.Instant;
import org.bson.Document;
import org.mongodb.morphia.query.Query;

public class DeleteOrphanInstancesOfDeletedEnvironments implements NGMigration {
  @Inject private HPersistence persistence;
  @Inject private MongoPersistence mongoPersistence;

  @Override
  public void migrate() {
    log.info("Running duplicate delete migration");
    String accountIdforDeletion = "PcCzPoKDRjyi_TuERV9VCQ";
    Query<Environment> environmentQuery = persistence.createQuery(Environment.class)
                                              .filter(Environment.EnvironmentKeys.deleted, true)
                                              .filter(Environment.EnvironmentKeys.accountId, accountIdforDeletion);
    try (HIterator<Environment> environments = new HIterator<>(environmentQuery.fetch())) {
      for (Environment environment : environments) {
        log.info("deleting instances of environment:{} for account ID :{}", environment.getName(),
            environment.getAccountId());
        deleteInstance(environment);
      }
    }
  }

  private void deleteInstance(Environment environment) {
    try {
      log.info("deleting Instances");
      BasicDBObject basicDBObject = new BasicDBObject().append(InstanceKeys.envIdentifier, environment.getIdentifier());
      BasicDBObject updateOps =
          new BasicDBObject(InstanceKeys.isDeleted, true).append(InstanceKeys.deletedAt, System.currentTimeMillis());

      BulkWriteOperation instanceWriteOperation =
          mongoPersistence.getCollection(Instance.class).initializeUnorderedBulkOperation();
      instanceWriteOperation.find(basicDBObject).update(new BasicDBObject("$set", updateOps));
      BulkWriteResult updateOperationResult = instanceWriteOperation.execute();
      log.info("Soft deleted instance {}", updateOperationResult);
      log.info("instances deleted successfully.");

    } catch (Exception ex) {
      log.error("Unexpected error occurred while migrating Instances for deleted environment", ex);
    }
  }
}
