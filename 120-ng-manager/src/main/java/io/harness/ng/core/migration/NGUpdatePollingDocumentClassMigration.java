/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static java.lang.String.format;

import io.harness.migration.NGMigration;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.PollingDocument.PollingDocumentKeys;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class NGUpdatePollingDocumentClassMigration implements NGMigration {
  @Inject private MongoTemplate mongoTemplate;
  private static final String DEBUG_LOG = "[NGUpdatePollingDocumentClassMigration]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting migration of updating polling document class field");

      // update all pollDocuments
      String key = format("%s._class", PollingDocumentKeys.polledResponse);

      log.info(DEBUG_LOG + "updating ArtifactPolledResponse class field");
      org.springframework.data.mongodb.core.query.Query queryArtifactPolledResponse =
          new org.springframework.data.mongodb.core.query.Query().addCriteria(
              Criteria.where(key).is("io.harness.polling.bean.artifact.ArtifactPolledResponse"));
      Update updateArtifactPolledResponse = new Update().set(key, "io.harness.polling.bean.ArtifactPolledResponse");
      UpdateResult updateResultArtifactPolledResponse =
          mongoTemplate.updateMulti(queryArtifactPolledResponse, updateArtifactPolledResponse, PollingDocument.class);
      log.info(DEBUG_LOG
          + format("ArtifactPolledResponse class field updated [matched=%d] [modified=%d]",
              updateResultArtifactPolledResponse.getMatchedCount(),
              updateResultArtifactPolledResponse.getModifiedCount()));

      log.info(DEBUG_LOG + "updating ManifestPolledResponse class field");
      org.springframework.data.mongodb.core.query.Query queryManifestPolledResponse =
          new org.springframework.data.mongodb.core.query.Query().addCriteria(
              Criteria.where(key).is("io.harness.polling.bean.manifest.ManifestPolledResponse"));
      Update updateManifestPolledResponse = new Update().set(key, "io.harness.polling.bean.ManifestPolledResponse");
      UpdateResult updateResultManifestPolledResponse =
          mongoTemplate.updateMulti(queryManifestPolledResponse, updateManifestPolledResponse, PollingDocument.class);
      log.info(DEBUG_LOG
          + format("ManifestPolledResponse class field updated [matched=%d] [modified=%d]",
              updateResultManifestPolledResponse.getMatchedCount(),
              updateResultManifestPolledResponse.getModifiedCount()));

      log.info(DEBUG_LOG + "updating GitPollingPolledResponse class field");
      org.springframework.data.mongodb.core.query.Query queryGitPollingPolledResponse =
          new org.springframework.data.mongodb.core.query.Query().addCriteria(
              Criteria.where(key).is("io.harness.polling.bean.gitpolling.GitPollingPolledResponse"));
      Update updateGitPollingPolledResponse = new Update().set(key, "io.harness.polling.bean.GitPollingPolledResponse");
      UpdateResult updateResultGitPollingPolledResponse = mongoTemplate.updateMulti(
          queryGitPollingPolledResponse, updateGitPollingPolledResponse, PollingDocument.class);
      log.info(DEBUG_LOG
          + format("GitPollingPolledResponse class field updated [matched=%d] [modified=%d]",
              updateResultGitPollingPolledResponse.getMatchedCount(),
              updateResultGitPollingPolledResponse.getModifiedCount()));

      key = format("%s._class", PollingDocumentKeys.pollingInfo);

      log.info(DEBUG_LOG + "updating HelmChartManifestInfo class field");
      org.springframework.data.mongodb.core.query.Query queryHelmChartManifestInfo =
          new org.springframework.data.mongodb.core.query.Query().addCriteria(
              Criteria.where(key).is("io.harness.polling.bean.manifest.HelmChartManifestInfo"));
      Update updateHelmChartManifestInfo = new Update().set(key, "io.harness.polling.bean.HelmChartManifestInfo");
      UpdateResult updateResultHelmChartManifestInfo =
          mongoTemplate.updateMulti(queryHelmChartManifestInfo, updateHelmChartManifestInfo, PollingDocument.class);
      log.info(DEBUG_LOG
          + format("HelmChartManifestInfo class field updated [matched=%d] [modified=%d]",
              updateResultHelmChartManifestInfo.getMatchedCount(),
              updateResultHelmChartManifestInfo.getModifiedCount()));

      log.info(DEBUG_LOG + "updating GitHubPollingInfo class field");
      org.springframework.data.mongodb.core.query.Query queryGitHubPollingInfo =
          new org.springframework.data.mongodb.core.query.Query().addCriteria(
              Criteria.where(key).is("io.harness.polling.bean.gitpolling.GitHubPollingInfo"));
      Update updateGitHubPollingInfo = new Update().set(key, "io.harness.polling.bean.GitHubPollingInfo");
      UpdateResult updateResultGitHubPollingInfo =
          mongoTemplate.updateMulti(queryGitHubPollingInfo, updateGitHubPollingInfo, PollingDocument.class);
      log.info(DEBUG_LOG
          + format("GitHubPollingInfo class field updated [matched=%d] [modified=%d]",
              updateResultGitHubPollingInfo.getMatchedCount(), updateResultGitHubPollingInfo.getModifiedCount()));

      log.info(DEBUG_LOG + "Finished migration of updating polling document class field");
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Update/migration of polling documents failed", e);
    }
  }
}