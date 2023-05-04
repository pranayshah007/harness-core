/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static java.lang.String.format;

import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class CustomDeploymentDetailsClassMigration implements NGMigration {
  @Inject private MongoTemplate mongoTemplate;
  private static final String DEBUG_LOG = "[CustomDeploymentDetailsClassMigration]: ";
  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting migration of updating Custom Deployment Details Info document class field");

      // update all pollDocuments
      String key = format("%s._class", StageExecutionInfoKeys.executionDetails);

      log.info(DEBUG_LOG + "updating CustomDeploymentDetails class field");
      Query queryCustomDeploymentDetailsResponse = new Query().addCriteria(
          Criteria.where(key).is("io.harness.cdng.customDeployment.beans.CustomDeploymentExecutionDetails"));
      Update updateCustomDeploymentDetailsResponse =
          new Update().set(key, "io.harness.cdng.customDeployment.CustomDeploymentExecutionDetails");
      UpdateResult updateResultCustomDeploymentDetailsResponse = mongoTemplate.updateMulti(
          queryCustomDeploymentDetailsResponse, updateCustomDeploymentDetailsResponse, StageExecutionInfo.class);

      log.info(DEBUG_LOG
          + format("CustomDeploymentExecutionDetails class field updated [matched=%d] [modified=%d]",
              updateResultCustomDeploymentDetailsResponse.getMatchedCount(),
              updateResultCustomDeploymentDetailsResponse.getModifiedCount()));

      log.info(DEBUG_LOG + "Finished migration of updating Custom Deployment Details Info document class field");
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Update/migration of Custom Deployment Details Info documents failed", e);
    }
  }
}
