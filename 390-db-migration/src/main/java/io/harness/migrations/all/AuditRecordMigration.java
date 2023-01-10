/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toList;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.audit.AuditHeader;
import software.wings.audit.AuditHeader.AuditHeaderKeys;
import software.wings.audit.EntityAuditRecord;
import software.wings.audit.ResourceType;
import software.wings.beans.CGConstants;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import dev.morphia.Morphia;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AuditRecordMigration implements Migration {
  public static final String GIT_SYNC = "GIT_SYNC";
  public static final String GIT = "GIT";
  @Inject private WingsPersistence wingsPersistence;
  @Inject private Morphia morphia;

  // Timestamp few hours before audit was enabled in prod (General store acc).
  private long timeStamp = 1558628587000l;

  private static HashSet<String> artifactServerTypes = new HashSet<>(
      Arrays.asList("APM Verification Config", "Amazon S3 Helm Repo Config", "Artifactory Config", "Bamboo Config",
          "Docker Config", "Http Helm Repo Config", "Jenkins Config", "Nexus Config", "Sftp Config", "Smb Config"));

  private static HashSet<String> collabProviderTypes =
      new HashSet<>(Arrays.asList("Jira Config", "Service Now Config", "Slack Config", "Smtp Config"));

  private static HashSet<String> verificationProviderTypes = new HashSet<>(Arrays.asList("APM Verification Config",
      "AppDynamics Config", "Bugsnag Config", "Datadog Config", "DynaTrace Config", "Elk Config", "Logz Config",
      "New Relic Config", "Prometheus Config", "Splunk Config", "Sumo Config"));

  private static HashSet<String> connectionAttributeTypes = new HashSet<>(
      Arrays.asList("Host Connection Attributes", "Win Rm Connection Attributes", "BastionConnectionAttributes"));

  Set<String> migrationAffectedTypes = new HashSet<>(Arrays.asList(
      ResourceType.CLOUD_PROVIDER.name(), "CONNECTOR", "HELM_REPO", "SETTING", ResourceType.ENCRYPTED_RECORDS.name()));

  @Override
  public void migrate() {
    final DBCollection collection = wingsPersistence.getCollection(AuditHeader.class);
    migrateGitUserId(collection);
    migrateSettingAttributeType(collection);
  }

  @SuppressWarnings("deprecation")
  private void migrateGitUserId(DBCollection collection) {
    int i = 1;
    // Update Git_Sync audit records with auditId
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
    try (HIterator<AuditHeader> auditRecordsIterator = new HIterator<>(wingsPersistence.createQuery(AuditHeader.class)
                                                                           .field(AuditHeaderKeys.createdAt)
                                                                           .greaterThan(timeStamp)
                                                                           .field(AuditHeaderKeys.createdByName)
                                                                           .equal(GIT_SYNC)
                                                                           .project(AuditHeaderKeys.createdBy, true)
                                                                           .fetch())) {
      while (auditRecordsIterator.hasNext()) {
        final AuditHeader auditHeader = auditRecordsIterator.next();

        if (GIT.equals(auditHeader.getCreatedBy().getUuid())) {
          continue;
        }

        if (i % 1000 == 0) {
          bulkWriteOperation.execute();
          bulkWriteOperation = collection.initializeUnorderedBulkOperation();
          log.info("AuditRecords: {} updated", i);
        }
        ++i;

        bulkWriteOperation
            .find(wingsPersistence.createQuery(AuditHeader.class)
                      .filter(AuditHeaderKeys.uuid, auditHeader.getUuid())
                      .getQueryObject())
            .updateOne(new BasicDBObject("$set", new BasicDBObject(AuditHeaderKeys.createdById, GIT)));
      }
    }
    if (i % 1000 != 1) {
      bulkWriteOperation.execute();
    }
  }

  @SuppressWarnings("deprecation")
  private void migrateSettingAttributeType(DBCollection collection) {
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
    int i = 1;
    try (HIterator<AuditHeader> auditRecordsIterator =
             new HIterator<>(wingsPersistence.createQuery(AuditHeader.class)
                                 .field(AuditHeaderKeys.createdAt)
                                 .greaterThan(timeStamp)
                                 .field(AuditHeaderKeys.affectedResourceType)
                                 .in(migrationAffectedTypes)
                                 .project(AuditHeaderKeys.entityAuditRecords, true)
                                 .fetch())) {
      while (auditRecordsIterator.hasNext()) {
        final AuditHeader auditHeader = auditRecordsIterator.next();
        if (auditRecordDoesNotNeedUpdate(auditHeader)) {
          continue;
        }
        if (i % 1000 == 0) {
          bulkWriteOperation.execute();
          bulkWriteOperation = collection.initializeUnorderedBulkOperation();
          log.info("AuditRecords: {} updated", i);
        }
        ++i;

        BasicDBList basicDBList = new BasicDBList();
        auditHeader.getEntityAuditRecords().forEach(record -> basicDBList.add(morphia.toDBObject(record)));
        bulkWriteOperation
            .find(wingsPersistence.createQuery(AuditHeader.class)
                      .filter(AuditHeaderKeys.uuid, auditHeader.getUuid())
                      .getQueryObject())
            .updateOne(new BasicDBObject("$set", new BasicDBObject(AuditHeaderKeys.entityAuditRecords, basicDBList)));
      }
    } catch (Exception e) {
      log.warn("something failed", e);
    }

    if (i % 1000 != 1) {
      bulkWriteOperation.execute();
    }
  }

  private boolean auditRecordDoesNotNeedUpdate(AuditHeader auditHeader) {
    if (isEmpty(auditHeader.getEntityAuditRecords())) {
      return true;
    }

    boolean unchanged = true;
    // stamp _GLOBAL_APP_ID_ as appId wherever missing
    List<EntityAuditRecord> recordList =
        auditHeader.getEntityAuditRecords().stream().filter(this::recordNeedsUpdateForGlobalAppId).collect(toList());

    if (isNotEmpty(recordList)) {
      recordList.forEach(record -> record.setAppId(CGConstants.GLOBAL_APP_ID));
      unchanged = false;
    }

    // Change "HELM_REPO" to ARTIFACT_SERVER
    recordList = auditHeader.getEntityAuditRecords()
                     .stream()
                     .filter(record -> "HELM_REPO".equals(record.getAffectedResourceType()))
                     .collect(toList());

    if (isNotEmpty(recordList)) {
      recordList.forEach(record -> record.setAffectedResourceType(ResourceType.ARTIFACT_SERVER.name()));
      unchanged = false;
    }

    // Change "CONNECTOR" to appropriate value
    recordList = auditHeader.getEntityAuditRecords()
                     .stream()
                     .filter(record -> "CONNECTOR".equals(record.getAffectedResourceType()))
                     .collect(toList());
    if (isNotEmpty(recordList)) {
      recordList.forEach(this::updateRequiredForConnectorToNewCategory);
      unchanged = false;
    }

    return unchanged;
  }

  private void updateRequiredForConnectorToNewCategory(EntityAuditRecord record) {
    String entityType = record.getEntityType();
    if (artifactServerTypes.contains(entityType)) {
      record.setAffectedResourceType(ResourceType.ARTIFACT_SERVER.name());
    } else if (verificationProviderTypes.contains(entityType)) {
      record.setAffectedResourceType(ResourceType.VERIFICATION_PROVIDER.name());
    } else if (collabProviderTypes.contains(entityType)) {
      record.setAffectedResourceType(ResourceType.COLLABORATION_PROVIDER.name());
    } else if ("GitConfig".equals(entityType)) {
      record.setAffectedResourceType(ResourceType.SOURCE_REPO_PROVIDER.name());
    } else if (connectionAttributeTypes.contains(entityType)) {
      record.setAffectedResourceType(ResourceType.CONNECTION_ATTRIBUTES.name());
    } else {
      log.warn("Following entity type was not handled in migration: " + entityType);
    }
  }

  private boolean recordNeedsUpdateForGlobalAppId(EntityAuditRecord record) {
    return migrationAffectedTypes.contains(record.getAffectedResourceType())
        && !CGConstants.GLOBAL_APP_ID.equals(record.getAppId());
  }
}
