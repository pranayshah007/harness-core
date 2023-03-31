/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import io.harness.account.AccountClient;
import io.harness.migration.NGMigration;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class ChangeEnvironmentRefFieldNameToEnvIdentifierMigration implements NGMigration {
  @Inject private MongoTemplate mongoTemplate;
  @Inject private AccountClient accountClient;
  private static final String DEBUG_LOG = "[ChangeEnvironmentRefFieldNameToEnvIdentifierMigration]: ";
  private static final String OLD_FIELD_NAME = "environmentRef";
  private static final String NEW_FIELD_NAME = "envIdentifier";
  private static final String COLLECTION_NAME = "serviceOverridesNG";

  @Override
  public void migrate() {
    try {
      log.info(
          DEBUG_LOG + "Starting migration of changing environmentRef field name to envIdentifier in serviceOverrideNG");
      List<AccountDTO> allAccounts = CGRestUtils.getResponse(accountClient.getAllAccounts());
      List<String> accountIdentifiers = allAccounts.stream()
                                            .filter(AccountDTO::isNextGenEnabled)
                                            .map(AccountDTO::getIdentifier)
                                            .collect(Collectors.toList());

      accountIdentifiers.forEach(accountId -> {
        try {
          log.info(DEBUG_LOG
              + "Starting migration of changing environmentRef field name to envIdentifier in serviceOverrideNG for account : "
              + accountId);

          Update update = new Update().rename(OLD_FIELD_NAME, NEW_FIELD_NAME);
          UpdateResult updateResult = mongoTemplate.updateMulti(new Query(), update, COLLECTION_NAME);

          log.info("{} Migration successful for account : {} ,Successfully updated records {}", DEBUG_LOG, accountId,
              updateResult.getModifiedCount());
        } catch (Exception e) {
          log.error(DEBUG_LOG
                  + "Migration of changing environmentRef field name to envIdentifier in serviceOverrideNG failed for account: "
                  + accountId,
              e);
        }
      });

      log.info(DEBUG_LOG
          + "Migration of changing environmentRef field name to envIdentifier in serviceOverrideNG completed");
    } catch (Exception e) {
      log.error(
          DEBUG_LOG + "Migration of changing environmentRef field name to envIdentifier in serviceOverrideNG failed.",
          e);
    }
  }
}
