package io.harness.ng.core.migration;

import io.harness.connector.entities.Connector;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketConnector;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketUsernamePasswordApiAccess;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.migration.NGMigration;
import io.harness.repositories.ConnectorRepository;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class NGBitbucketConnectorMigration implements NGMigration {
  @Inject private ConnectorRepository connectorRepository;
  private static final int BATCH_SIZE = 100;
  private static final String classField = BitbucketConnector.BitbucketConnectorKeys.bitbucketApiAccess + "._class";

  @Override
  public void migrate() {
    try {
      Criteria criteria = Criteria.where(Connector.ConnectorKeys.type)
                              .is(ConnectorType.BITBUCKET)
                              .and(BitbucketConnector.BitbucketConnectorKeys.hasApiAccess)
                              .is(Boolean.TRUE)
                              .and(classField)
                              .exists(false)
                              .and(BitbucketConnector.BitbucketConnectorKeys.apiAccessType)
                              .exists(false);
      Query query = new Query(criteria);
      query.cursorBatchSize(BATCH_SIZE);
      Update update = new Update();
      update.set(classField, BitbucketUsernamePasswordApiAccess.class.getCanonicalName());
      update.set(BitbucketConnector.BitbucketConnectorKeys.apiAccessType, BitbucketApiAccessType.USERNAME_AND_TOKEN);
      log.info("[NGBitbucketConnectorMigration] Query for updating Harness Bitbucket connector Access: {}", query.toString());

      UpdateResult result = connectorRepository.updateMultiple(query, update);

      log.info("[NGBitbucketConnectorMigration] Successfully updated {} Bitbucket connector api access type",
          result.getModifiedCount());
    } catch (Exception e) {
      log.error("[NGBitbucketConnectorMigration] Failed to update Bitbucket connector api access type. Error: {}", e);
    }
  }
}
