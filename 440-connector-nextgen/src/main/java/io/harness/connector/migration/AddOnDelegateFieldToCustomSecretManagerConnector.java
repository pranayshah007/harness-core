package io.harness.connector.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.embedded.customsecretmanager.CustomSecretManagerConnector.CustomSecretManagerConnectorKeys;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.migration.NGMigration;
import io.harness.repositories.ConnectorRepository;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class AddOnDelegateFieldToCustomSecretManagerConnector implements NGMigration {
  @Inject private ConnectorRepository connectorRepository;

  @Override
  public void migrate() {
    Criteria criteriaForFalse = Criteria.where(Connector.ConnectorKeys.type)
                                    .is(ConnectorType.CUSTOM_SECRET_MANAGER)
                                    .and(CustomSecretManagerConnectorKeys.host)
                                    .exists(true);
    Query queryForFalse = new Query(criteriaForFalse);
    Update updateForFalse = new Update().set(CustomSecretManagerConnectorKeys.onDelegate, Boolean.FALSE);
    connectorRepository.updateMultiple(queryForFalse, updateForFalse);

    Criteria criteriaForTrue = Criteria.where(Connector.ConnectorKeys.type)
                                   .is(ConnectorType.CUSTOM_SECRET_MANAGER)
                                   .and(CustomSecretManagerConnectorKeys.host)
                                   .exists(false);
    Query queryForTrue = new Query(criteriaForTrue);
    Update updateForTrue = new Update().set(CustomSecretManagerConnectorKeys.onDelegate, Boolean.TRUE);
    connectorRepository.updateMultiple(queryForTrue, updateForTrue);
  }
}
