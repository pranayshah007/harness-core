/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HPersistence.DEFAULT_STORE;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.CustomArtifactServerConfig;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStream.ArtifactStreamKeys;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.ArtifactKeys;
import software.wings.security.EnvFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.UsageRestrictions;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@SuppressWarnings("deprecation")
public class MigrateServiceLevelArtifactStreamsToConnectorLevel implements Migration {
  private static final String ACCOUNT_ID = "zEaak-FLS425IEO7OLzMUg"; // TODO: change this to reflect correct account
  private static final String SEPARATOR = "_";
  private static final String CUSTOM_ARTIFACT_SERVER = "Custom Artifact Server";

  @Inject private WingsPersistence wingsPersistence;
  @Inject private SettingsService settingsService;

  @Override
  public void migrate() {
    log.info("Migration Started - move service level artifact streams to ConnectorDisconnectHandler level");
    Account account = wingsPersistence.get(Account.class, ACCOUNT_ID);
    if (account == null) {
      log.info("Specified account not found. Not migrating artifact streams from services to connectors.");
      return;
    }

    migrateAccount(account.getUuid());
    log.info("Migration Completed - move service level artifact streams to ConnectorDisconnectHandler level");
  }

  private void migrateAccount(String accountId) {
    log.info("Processing account: " + accountId);

    // Prefetch applications for this account
    List<Application> applications = wingsPersistence.createQuery(Application.class)
                                         .filter(ApplicationKeys.accountId, accountId)
                                         .project(ApplicationKeys.name, true)
                                         .asList();
    Map<String, String> appIdToNameMap = new HashMap<>();
    for (Application application : applications) {
      appIdToNameMap.put(application.getUuid(), application.getName());
    }
    // Get all services for account
    Map<String, String> serviceIdToNameMap = new HashMap<>();
    List<Service> services = wingsPersistence.createQuery(Service.class)
                                 .field(ServiceKeys.appId)
                                 .in(appIdToNameMap.keySet())
                                 .project(ServiceKeys.name, true)
                                 .asList();
    for (Service service : services) {
      serviceIdToNameMap.put(service.getUuid(), service.getName());
    }

    final DBCollection collection = wingsPersistence.getCollection(DEFAULT_STORE, "artifactStream");
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
    int updated = 0;
    boolean found = false;
    try (HIterator<SettingAttribute> settingAttributeHIterator =
             new HIterator<>(wingsPersistence.createQuery(SettingAttribute.class)
                                 .filter(SettingAttributeKeys.accountId, accountId)
                                 .fetch())) {
      for (SettingAttribute settingAttribute : settingAttributeHIterator) {
        Set<String> addedNames = new HashSet<>();
        // For each SettingId find all the artifact Steams
        log.info("Finding all artifact streams associated with settingId {}", settingAttribute.getUuid());
        try (HIterator<ArtifactStream> artifactStreamsIterator =
                 new HIterator<>(wingsPersistence.createQuery(ArtifactStream.class)
                                     .filter(ArtifactStreamKeys.settingId, settingAttribute.getUuid())
                                     .field(ArtifactStreamKeys.appId)
                                     .notEqual(GLOBAL_APP_ID)
                                     .fetch())) {
          for (ArtifactStream artifactStream : artifactStreamsIterator) {
            if (isBlank(artifactStream.getServiceId())
                || !serviceIdToNameMap.containsKey(artifactStream.getServiceId())) {
              // Skip if serviceId is not valid
              continue;
            }
            found = true;
            String name = getArtifactStreamName(appIdToNameMap, serviceIdToNameMap, artifactStream, addedNames);
            bulkWriteOperation
                .find(wingsPersistence.createQuery(ArtifactStream.class)
                          .filter(ArtifactStreamKeys.uuid, artifactStream.getUuid())
                          .getQueryObject())
                .updateOne(new BasicDBObject("$set",
                    new BasicDBObject(ArtifactStreamKeys.name, name)
                        .append(ArtifactStreamKeys.appId, GLOBAL_APP_ID)
                        .append(ArtifactStreamKeys.serviceId, settingAttribute.getUuid())));
            updated++;
          }
        }
        if (found) {
          bulkWriteOperation.execute();
          log.info("Updated: " + updated + " artifact streams for setting id: " + settingAttribute.getUuid());
          updated = 0;
          bulkWriteOperation = collection.initializeUnorderedBulkOperation();
          found = false;
        }
      }
    }

    // Create Custom Artifact Source folder and migrate all CUSTOM ARTIFACT STREAMS
    migrateCustomArtifactStreamsToAccountLevel(accountId, appIdToNameMap, serviceIdToNameMap, bulkWriteOperation);
    log.info("Done processing account: " + accountId);
  }

  private void migrateCustomArtifactStreamsToAccountLevel(String accountId, Map<String, String> appIdToNameMap,
      Map<String, String> serviceIdToNameMap, BulkWriteOperation bulkWriteOperation) {
    log.info("Inside migrateCustomArtifactStreamsToAccountLevel for account: " + accountId);
    Pair<SettingAttribute, String> settingAttributeStringPair = getCustomArtifactServerName(accountId);
    SettingAttribute savedSettingAttribute = settingAttributeStringPair.getLeft();
    if (savedSettingAttribute == null) {
      GenericEntityFilter appFilter = GenericEntityFilter.builder().filterType("ALL").build();
      Set<String> prodFilterTypes = new HashSet<>();
      prodFilterTypes.add("PROD");
      EnvFilter prodEnvFilter = EnvFilter.builder().filterTypes(prodFilterTypes).build();
      Set<String> nonProdFilterTypes = new HashSet<>();
      nonProdFilterTypes.add("NON_PROD");
      EnvFilter nonProdEnvFilter = EnvFilter.builder().filterTypes(nonProdFilterTypes).build();
      Set<UsageRestrictions.AppEnvRestriction> appEnvRestrictions = new HashSet<>();
      appEnvRestrictions.add(
          UsageRestrictions.AppEnvRestriction.builder().appFilter(appFilter).envFilter(prodEnvFilter).build());
      appEnvRestrictions.add(
          UsageRestrictions.AppEnvRestriction.builder().appFilter(appFilter).envFilter(nonProdEnvFilter).build());
      SettingAttribute settingAttribute =
          SettingAttribute.Builder.aSettingAttribute()
              .withName(settingAttributeStringPair.getRight())
              .withValue(CustomArtifactServerConfig.builder().accountId(accountId).build())
              .withAccountId(accountId)
              .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
              .withUsageRestrictions(UsageRestrictions.builder().appEnvRestrictions(appEnvRestrictions).build())
              .build();

      log.info("Creating Custom Artifact Server folder for account: " + accountId);
      savedSettingAttribute = settingsService.save(settingAttribute);
    }

    log.info("Finding all artifact streams of type CUSTOM");
    int updated = 0;
    boolean found = false;
    Set<String> addedNames = new HashSet<>();
    try (HIterator<ArtifactStream> customArtifactStreamsIterator =
             new HIterator<>(wingsPersistence.createQuery(ArtifactStream.class)
                                 .filter(ArtifactStreamKeys.artifactStreamType, CUSTOM.name())
                                 .field(ArtifactStreamKeys.appId)
                                 .notEqual(GLOBAL_APP_ID)
                                 .filter(ArtifactStreamKeys.accountId, accountId)
                                 .fetch())) {
      for (ArtifactStream artifactStream : customArtifactStreamsIterator) {
        if (isBlank(artifactStream.getServiceId()) || !serviceIdToNameMap.containsKey(artifactStream.getServiceId())) {
          // Skip if serviceId is not valid
          continue;
        }
        found = true;
        String name = getArtifactStreamName(appIdToNameMap, serviceIdToNameMap, artifactStream, addedNames);
        bulkWriteOperation
            .find(wingsPersistence.createQuery(ArtifactStream.class)
                      .filter(ArtifactStreamKeys.uuid, artifactStream.getUuid())
                      .getQueryObject())
            .updateOne(new BasicDBObject("$set",
                new BasicDBObject(ArtifactStreamKeys.name, name)
                    .append(ArtifactStreamKeys.appId, GLOBAL_APP_ID)
                    .append(ArtifactStreamKeys.settingId, savedSettingAttribute.getUuid())
                    .append(ArtifactStreamKeys.serviceId, savedSettingAttribute.getUuid())));
        updated++;

        // Update all artifacts belonging to this artifact stream - set settingId to the new ConnectorDisconnectHandler created above
        int updatedArtifacts = 0;
        boolean foundArtifacts = false;
        final DBCollection artifactCollection = wingsPersistence.getCollection(DEFAULT_STORE, "artifacts");
        BulkWriteOperation artifactsBulkWriteOperation = artifactCollection.initializeUnorderedBulkOperation();
        try (HIterator<Artifact> artifactIterator =
                 new HIterator<>(wingsPersistence.createQuery(Artifact.class)
                                     .filter(ArtifactKeys.artifactStreamType, CUSTOM.name())
                                     .field(ArtifactKeys.appId)
                                     .notEqual(GLOBAL_APP_ID)
                                     .filter(ArtifactKeys.accountId, accountId)
                                     .filter(ArtifactKeys.artifactStreamId, artifactStream.getUuid())
                                     .fetch())) {
          for (Artifact artifact : artifactIterator) {
            foundArtifacts = true;
            artifactsBulkWriteOperation
                .find(wingsPersistence.createQuery(Artifact.class)
                          .filter(ArtifactKeys.uuid, artifact.getUuid())
                          .getQueryObject())
                .updateOne(new BasicDBObject("$set",
                    new BasicDBObject(ArtifactKeys.appId, GLOBAL_APP_ID)
                        .append(ArtifactKeys.settingId, savedSettingAttribute.getUuid())));
            artifactsBulkWriteOperation
                .find(wingsPersistence.createQuery(Artifact.class)
                          .filter(ArtifactKeys.uuid, artifact.getUuid())
                          .getQueryObject())
                .updateOne(new BasicDBObject("$unset", new BasicDBObject(ArtifactKeys.serviceIds, "")));
            updatedArtifacts++;
          }
        }
        if (foundArtifacts) {
          artifactsBulkWriteOperation.execute();
          log.info("Migrated: " + updatedArtifacts + " custom artifacts belonging to artifactStream: "
              + artifactStream.getUuid() + " to settingId: " + savedSettingAttribute.getUuid());
        }
      }
    }
    if (found) {
      bulkWriteOperation.execute();
      log.info("Migrated: " + updated + " custom artifact streams to setting id: " + savedSettingAttribute.getUuid());
    }
  }

  private String getArtifactStreamName(Map<String, String> appIdToNameMap, Map<String, String> serviceIdToNameMap,
      ArtifactStream artifactStream, Set<String> addedNames) {
    // Resolve the duplicate names
    // Name : appName_serviceName_artifactStreamName
    // Make APP_ID as GLOBAL_APP_ID
    // serviceId can be settingId
    // bulk update
    String originalName = appIdToNameMap.get(artifactStream.fetchAppId()) + SEPARATOR
        + serviceIdToNameMap.get(artifactStream.getServiceId()) + SEPARATOR + artifactStream.getName();
    String name = originalName;
    for (int i = 1; addedNames.contains(name); i++) {
      name = originalName + SEPARATOR + i;
    }
    addedNames.add(name);
    return name;
  }

  private Pair<SettingAttribute, String> getCustomArtifactServerName(String accountId) {
    int i = 0;
    while (true) {
      String name;
      if (i == 0) {
        name = CUSTOM_ARTIFACT_SERVER;
      } else {
        name = CUSTOM_ARTIFACT_SERVER + " " + i;
      }

      SettingAttribute settingAttribute = settingsService.getByName(accountId, GLOBAL_APP_ID, name);
      if (settingAttribute == null) {
        // Create a new setting attribute.
        return ImmutablePair.of(null, name);
      }
      if (CUSTOM.name().equals(settingAttribute.getValue().getType())) {
        // Setting attribute already exists.
        return Pair.of(settingAttribute, name);
      }
      i++;
    }
  }
}
