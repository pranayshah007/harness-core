/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessModule._955_ACCOUNT_MGMT;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.dl.exportimport.WingsMongoExportImport.getCollectionName;

import static org.springframework.security.crypto.bcrypt.BCrypt.hashpw;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.FeatureFlag;
import io.harness.beans.FeatureFlag.FeatureFlagKeys;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HIterator;
import io.harness.persistence.PersistentEntity;
import io.harness.rest.RestResponse;
import io.harness.scheduler.PersistentScheduler;
import io.harness.security.encryption.EncryptionType;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.GitCommit;
import software.wings.beans.KmsConfig;
import software.wings.beans.LicenseInfo;
import software.wings.beans.Schema;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.beans.account.AccountStatus;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapSettings.LdapSettingsKeys;
import software.wings.beans.sso.SSOType;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.TriggerConditionType;
import software.wings.dl.WingsMongoPersistence;
import software.wings.dl.exportimport.ExportMode;
import software.wings.dl.exportimport.ImportMode;
import software.wings.dl.exportimport.ImportStatusReport;
import software.wings.dl.exportimport.ImportStatusReport.ImportStatus;
import software.wings.dl.exportimport.WingsMongoExportImport;
import software.wings.licensing.LicenseService;
import software.wings.scheduler.AlertCheckJob;
import software.wings.scheduler.InstanceStatsCollectorJob;
import software.wings.scheduler.LimitVicinityCheckerJob;
import software.wings.scheduler.ScheduledTriggerJob;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.UserService;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.AccountPermissionUtils;
import software.wings.yaml.gitSync.YamlChangeSet;
import software.wings.yaml.gitSync.YamlChangeSet.Status;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import dev.morphia.Morphia;
import dev.morphia.query.Query;
import io.swagger.annotations.Api;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.springframework.security.crypto.bcrypt.BCrypt;

/**
 * This class provides REST APIs can be used to export metadata associated with one specific account from one
 * harness cluster and be imported into another harness cluster, as part of effort in migrating account
 * from one cluster to another.
 *
 * @author marklu on 10/24/18
 */

@Api("account")
@Path("/account")
@Scope(ResourceType.SETTING)
@Singleton
@Slf4j
@OwnedBy(PL)
@TargetModule(_955_ACCOUNT_MGMT)
public class AccountExportImportResource {
  private static final String COLLECTION_CONFIG_FILES = "configs.files";
  private static final String COLLECTION_CONFIG_CHUNKS = "configs.chunks";
  private static final String COLLECTION_QUARTZ_JOBS = "quartz_jobs";
  private static final String JSON_FILE_SUFFIX = ".json";
  private static final String ZIP_FILE_SUFFIX = ".zip";

  private WingsMongoPersistence wingsPersistence;
  private Morphia morphia;
  private WingsMongoExportImport mongoExportImport;
  private PersistentScheduler scheduler;
  private AccountService accountService;
  private LicenseService licenseService;
  private AppService appService;
  private UserService userService;
  private AuthService authService;
  private AccountPermissionUtils accountPermissionUtils;
  private FeatureFlagService featureFlagService;
  private MainConfiguration mainConfiguration;

  private Map<String, Class<? extends PersistentEntity>> genericExportableEntityTypes = new LinkedHashMap<>();
  private Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private JsonParser jsonParser = new JsonParser();

  // Entity classes that need special export handling (e.g. with a special entity filter)
  private static Set<Class<? extends PersistentEntity>> includedEntities = new HashSet<>(
      Arrays.asList(Account.class, User.class, Application.class, Schema.class, EncryptedData.class, KmsConfig.class));

  private static Set<String> includedMongoCollections = new HashSet<>();

  static {
    for (Class<? extends PersistentEntity> entityClazz : includedEntities) {
      includedMongoCollections.add(getCollectionName(entityClazz));
    }
  }

  @Inject
  public AccountExportImportResource(WingsMongoPersistence wingsPersistence, Morphia morphia,
      WingsMongoExportImport mongoExportImport, AccountService accountService, LicenseService licenseService,
      AppService appService, AuthService authService, UserService userService,
      AccountPermissionUtils accountPermissionUtils, @Named("BackgroundJobScheduler") PersistentScheduler scheduler,
      FeatureFlagService featureFlagService, MainConfiguration mainConfiguration) {
    this.wingsPersistence = wingsPersistence;
    this.morphia = morphia;
    this.mongoExportImport = mongoExportImport;
    this.scheduler = scheduler;
    this.accountService = accountService;
    this.licenseService = licenseService;
    this.appService = appService;
    this.userService = userService;
    this.authService = authService;
    this.accountPermissionUtils = accountPermissionUtils;
    this.featureFlagService = featureFlagService;
    this.mainConfiguration = mainConfiguration;
    findExportableEntityTypes();
  }

  @GET
  @Path("/exportableCollections")
  @Timed
  @ExceptionMetered
  public RestResponse<Set<String>> exportAccountCollectionData(@QueryParam("accountId") final String accountId) {
    if (!userService.isAccountAdmin(accountId)) {
      String errorMessage = "User is not account administrator and can't perform the export operation.";
      RestResponse<Boolean> restResponse = accountPermissionUtils.checkIfHarnessUser(errorMessage);
      if (restResponse != null) {
        log.error(errorMessage);
        throw new InvalidRequestException(errorMessage);
      }
    }
    Set<String> exportableCollections = new HashSet<>();
    Map<String, Boolean> toBeExported = getToBeExported(ExportMode.ALL, null);

    for (Entry<String, Class<? extends PersistentEntity>> entry : genericExportableEntityTypes.entrySet()) {
      if (isExportable(toBeExported, entry.getKey())) {
        Class<? extends PersistentEntity> entityClazz = entry.getValue();
        if (entityClazz != null && isNotEmpty(getCollectionName(entityClazz))) {
          String collectionName = getCollectionName(entityClazz);
          if (isNotEmpty(collectionName)) {
            exportableCollections.add(collectionName);
          }
        }
      }
    }
    exportableCollections.addAll(includedMongoCollections);
    return new RestResponse<>(exportableCollections);
  }

  @GET
  @Path("/exportBatchCollection")
  @Produces(MediaType.APPLICATION_JSON)
  @ExceptionMetered
  public Response exportAccountCollectionBatchData(@QueryParam("accountId") final String accountId,
      @QueryParam("mode") @DefaultValue("ALL") ExportMode exportMode,
      @QueryParam("entityTypes") List<String> entityTypes,
      @QueryParam("collectionName") String collectionNameToBeExported,
      @QueryParam("exportConfigs") boolean exportConfigs, @QueryParam("batchNumber") int batchNumber,
      @QueryParam("batchSize") int batchSize, @QueryParam("mongoBatchSize") int mongoBatchSize,
      @QueryParam("exportRecordsUpdatedAfter") long exportRecordsUpdatedAfter,
      @QueryParam("exportRecordsCreatedAfter") long exportRecordsCreatedAfter,
      @QueryParam("identifiers") List<String> identifiers) throws Exception {
    // Only if the user the account administrator or in the Harness user group can perform the export operation.
    if (!userService.isAccountAdmin(accountId)) {
      String errorMessage = "User is not account administrator and can't perform the export operation.";
      RestResponse<Boolean> restResponse = accountPermissionUtils.checkIfHarnessUser(errorMessage);
      if (restResponse != null) {
        log.error(errorMessage);
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }
    }

    if (exportMode == ExportMode.SPECIFIC && isEmpty(entityTypes)) {
      throw new IllegalArgumentException("Export type is SPECIFIC but no entity type is specified.");
    }

    String zipFileName = accountId + ZIP_FILE_SUFFIX;
    File zipFile = new File(Files.createTempDir(), zipFileName);
    FileOutputStream fileOutputStream = new FileOutputStream(zipFile);
    ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);

    Map<String, Boolean> toBeExported = getToBeExported(exportMode, entityTypes);
    List<String> appIds = appService.getAppIdsByAccountId(accountId);
    if (batchSize == 0) {
      batchSize = mainConfiguration.getExportAccountDataBatchSize();
    }

    // 1. Export harness schema collection.
    String schemaCollectionName = getCollectionName(Schema.class);
    if (isExportable(toBeExported, schemaCollectionName)) {
      DBObject emptyFilter = new BasicDBObject();
      List<String> schemas = mongoExportImport.exportRecords(emptyFilter, schemaCollectionName);
      if (schemas.size() == 0) {
        log.warn("Schema collection data doesn't exist, schema version data won't be exported.");
      } else {
        batchExportToStream(zipOutputStream, fileOutputStream, schemas, schemaCollectionName, batchSize);
      }
    }

    if (exportConfigs) {
      collectionNameToBeExported = null;
    }

    // 2. Export account data.
    if (isNotEmpty(collectionNameToBeExported) && getCollectionName(Account.class).equals(collectionNameToBeExported)) {
      String accountCollectionName = getCollectionName(Account.class);
      if (isExportable(toBeExported, accountCollectionName)) {
        DBObject idFilter = new BasicDBObject("_id", accountId);
        List<String> accounts = mongoExportImport.exportRecords(idFilter, accountCollectionName);
        if (accounts.size() == 0) {
          throw new IllegalArgumentException(
              "Account '" + accountId + "' doesn't exist, can't proceed with this export operation.");
        } else {
          batchExportToStream(zipOutputStream, fileOutputStream, accounts, accountCollectionName, batchSize);
        }
      }
    }

    // 3. Export all users
    String userCollectionName = getCollectionName(User.class);
    if (isExportable(toBeExported, userCollectionName)) {
      DBObject accountsFilter =
          new BasicDBObject(UserKeys.accounts, new BasicDBObject("$in", new String[] {accountId}));
      List<String> users = mongoExportImport.exportRecords(accountsFilter, userCollectionName);
      batchExportToStream(zipOutputStream, fileOutputStream, users, userCollectionName, batchSize);
    }

    DBObject accountIdFilter = new BasicDBObject("accountId", accountId);
    DBObject appIdsFilter = new BasicDBObject("appId", new BasicDBObject("$in", appIds));

    // 4. Export all applications
    if (isNotEmpty(collectionNameToBeExported)
        && getCollectionName(Application.class).equals(collectionNameToBeExported)) {
      String applicationsCollectionName = getCollectionName(Application.class);
      if (isExportable(toBeExported, applicationsCollectionName)) {
        List<String> applications = mongoExportImport.exportRecords(accountIdFilter, applicationsCollectionName);
        batchExportToStream(zipOutputStream, fileOutputStream, applications, applicationsCollectionName, batchSize);
      }
    }

    DBObject accountOrAppIdsFilter = new BasicDBObject();
    accountOrAppIdsFilter.put("$or", Arrays.asList(accountIdFilter, appIdsFilter));

    // 5. Export config file content that are persisted in the Mongo GridFs. "configs.files" and "configs.chunks"
    // are not managed by Morphia, need to handle it separately and only export those entries associated with
    // CONFIG_FILE type of secrets for now,
    if (exportConfigs) {
      exportConfigFilesContent(zipOutputStream, fileOutputStream, accountOrAppIdsFilter);
    }

    // 6. Export all other Harness entities that has @Entity annotation excluding what's in the blacklist.
    if (isNotEmpty(collectionNameToBeExported)) {
      for (Entry<String, Class<? extends PersistentEntity>> entry : genericExportableEntityTypes.entrySet()) {
        if (isExportable(toBeExported, entry.getKey())) {
          Class<? extends PersistentEntity> entityClazz = entry.getValue();
          if (entityClazz != null && isNotEmpty(getCollectionName(entityClazz))
              && getCollectionName(entityClazz).equals(collectionNameToBeExported)
              && !includedMongoCollections.contains(collectionNameToBeExported)) {
            final DBObject exportFilter;
            // 'gitCommits' and 'yamlChangeSet' need special export filter.
            if (GitCommit.class == entityClazz) {
              exportFilter = getGitCommitExportFilter(accountIdFilter);
            } else if (YamlChangeSet.class == entityClazz) {
              exportFilter = getYamlChangeSetExportFilter(accountIdFilter);
            } else if (FeatureFlag.class == entityClazz) {
              // 8. Need to migrate the feature flag also as part of account migration
              //    https://harness.atlassian.net/browse/PL-9683
              exportFilter = getFeatureFlagExportFilter(accountId);
            } else {
              exportFilter = accountOrAppIdsFilter;
            }
            String collectionName = getCollectionName(entityClazz);
            mongoExportImport.exportRecords(zipOutputStream, fileOutputStream, exportFilter, collectionName,
                batchNumber, batchSize, mongoBatchSize, identifiers, exportRecordsUpdatedAfter,
                exportRecordsCreatedAfter);
          }
        }
      }
    }

    // 7. No need to export Quartz jobs. They can be recreated based on accountId/appId/triggerId etc.
    // Export 'kmsConfig' including the global KMS secret manager if configured (e.g. QA and PROD, but not in fremium
    // yet).
    if (exportConfigs) {
      String kmsConfigCollectionName = getCollectionName(KmsConfig.class);
      if (isExportable(toBeExported, kmsConfigCollectionName)) {
        List<String> accountIdList = Arrays.asList(accountId);
        DBObject exportFilter = new BasicDBObject("accountId", new BasicDBObject("$in", accountIdList));
        List<String> records = mongoExportImport.exportRecords(exportFilter, kmsConfigCollectionName);
        batchExportToStream(zipOutputStream, fileOutputStream, records, kmsConfigCollectionName, batchSize);
      }
    }

    log.info("Flushing exported data into a zip file {}.", zipFileName);
    zipOutputStream.flush();
    zipOutputStream.close();
    fileOutputStream.flush();
    fileOutputStream.close();
    log.info("Finished flushing {} bytes of exported account data into a zip file {}.", zipFile.length(), zipFileName);

    return Response.ok(zipFile, MediaType.APPLICATION_OCTET_STREAM)
        .header("content-disposition", "attachment; filename = " + zipFileName)
        .build();
  }

  @GET
  @Path("/exportCollection")
  @Produces(MediaType.APPLICATION_JSON)
  @ExceptionMetered
  public Response exportAccountCollectionData(@QueryParam("accountId") final String accountId,
      @QueryParam("mode") @DefaultValue("ALL") ExportMode exportMode,
      @QueryParam("entityTypes") List<String> entityTypes,
      @QueryParam("collectionName") String collectionNameToBeExported,
      @QueryParam("exportConfigs") boolean exportConfigs, @QueryParam("batchSize") int batchSize,
      @QueryParam("mongoBatchSize") int mongoBatchSize,
      @QueryParam("exportRecordsUpdatedAfter") long exportRecordsUpdatedAfter,
      @QueryParam("exportRecordsCreatedAfter") long exportRecordsCreatedAfter,
      @QueryParam("identifiers") List<String> identifiers) throws Exception {
    // Only if the user the account administrator or in the Harness user group can perform the export operation.
    if (!userService.isAccountAdmin(accountId)) {
      String errorMessage = "User is not account administrator and can't perform the export operation.";
      RestResponse<Boolean> restResponse = accountPermissionUtils.checkIfHarnessUser(errorMessage);
      if (restResponse != null) {
        log.error(errorMessage);
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }
    }

    if (exportMode == ExportMode.SPECIFIC && isEmpty(entityTypes)) {
      throw new IllegalArgumentException("Export type is SPECIFIC but no entity type is specified.");
    }

    String zipFileName = accountId + ZIP_FILE_SUFFIX;
    File zipFile = new File(Files.createTempDir(), zipFileName);
    FileOutputStream fileOutputStream = new FileOutputStream(zipFile);
    ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);

    Map<String, Boolean> toBeExported = getToBeExported(exportMode, entityTypes);
    List<String> appIds = appService.getAppIdsByAccountId(accountId);
    if (batchSize == 0) {
      batchSize = mainConfiguration.getExportAccountDataBatchSize();
    }

    // 1. Export harness schema collection.
    String schemaCollectionName = getCollectionName(Schema.class);
    if (isExportable(toBeExported, schemaCollectionName)) {
      DBObject emptyFilter = new BasicDBObject();
      List<String> schemas = mongoExportImport.exportRecords(emptyFilter, schemaCollectionName);
      if (schemas.size() == 0) {
        log.warn("Schema collection data doesn't exist, schema version data won't be exported.");
      } else {
        batchExportToStream(zipOutputStream, fileOutputStream, schemas, schemaCollectionName, batchSize);
      }
    }

    if (exportConfigs) {
      collectionNameToBeExported = null;
    }

    // 2. Export account data.
    if (isNotEmpty(collectionNameToBeExported) && getCollectionName(Account.class).equals(collectionNameToBeExported)) {
      String accountCollectionName = getCollectionName(Account.class);
      if (isExportable(toBeExported, accountCollectionName)) {
        DBObject idFilter = new BasicDBObject("_id", accountId);
        List<String> accounts = mongoExportImport.exportRecords(idFilter, accountCollectionName);
        if (accounts.size() == 0) {
          throw new IllegalArgumentException(
              "Account '" + accountId + "' doesn't exist, can't proceed with this export operation.");
        } else {
          batchExportToStream(zipOutputStream, fileOutputStream, accounts, accountCollectionName, batchSize);
        }
      }
    }

    // 3. Export all users
    String userCollectionName = getCollectionName(User.class);
    if (isExportable(toBeExported, userCollectionName)) {
      DBObject accountsFilter =
          new BasicDBObject(UserKeys.accounts, new BasicDBObject("$in", new String[] {accountId}));
      List<String> users = mongoExportImport.exportRecords(accountsFilter, userCollectionName);
      batchExportToStream(zipOutputStream, fileOutputStream, users, userCollectionName, batchSize);
    }

    DBObject accountIdFilter = new BasicDBObject("accountId", accountId);
    DBObject appIdsFilter = new BasicDBObject("appId", new BasicDBObject("$in", appIds));

    // 4. Export all applications
    if (isNotEmpty(collectionNameToBeExported)
        && getCollectionName(Application.class).equals(collectionNameToBeExported)) {
      String applicationsCollectionName = getCollectionName(Application.class);
      if (isExportable(toBeExported, applicationsCollectionName)) {
        List<String> applications = mongoExportImport.exportRecords(accountIdFilter, applicationsCollectionName);
        batchExportToStream(zipOutputStream, fileOutputStream, applications, applicationsCollectionName, batchSize);
      }
    }

    DBObject accountOrAppIdsFilter = new BasicDBObject();
    accountOrAppIdsFilter.put("$or", Arrays.asList(accountIdFilter, appIdsFilter));

    // 5. Export config file content that are persisted in the Mongo GridFs. "configs.files" and "configs.chunks"
    // are not managed by Morphia, need to handle it separately and only export those entries associated with
    // CONFIG_FILE type of secrets for now,
    if (exportConfigs) {
      exportConfigFilesContent(zipOutputStream, fileOutputStream, accountOrAppIdsFilter);
    }

    // 6. Export all other Harness entities that has @Entity annotation excluding what's in the blacklist.
    if (isNotEmpty(collectionNameToBeExported)) {
      for (Entry<String, Class<? extends PersistentEntity>> entry : genericExportableEntityTypes.entrySet()) {
        if (isExportable(toBeExported, entry.getKey())) {
          Class<? extends PersistentEntity> entityClazz = entry.getValue();
          if (entityClazz != null && isNotEmpty(getCollectionName(entityClazz))
              && getCollectionName(entityClazz).equals(collectionNameToBeExported)
              && !includedMongoCollections.contains(collectionNameToBeExported)) {
            final DBObject exportFilter;
            // 'gitCommits' and 'yamlChangeSet' need special export filter.
            if (GitCommit.class == entityClazz) {
              exportFilter = getGitCommitExportFilter(accountIdFilter);
            } else if (YamlChangeSet.class == entityClazz) {
              exportFilter = getYamlChangeSetExportFilter(accountIdFilter);
            } else if (FeatureFlag.class == entityClazz) {
              // 8. Need to migrate the feature flag also as part of account migration
              //    https://harness.atlassian.net/browse/PL-9683
              exportFilter = getFeatureFlagExportFilter(accountId);
            } else {
              exportFilter = accountOrAppIdsFilter;
            }
            String collectionName = getCollectionName(entityClazz);
            mongoExportImport.exportRecords(zipOutputStream, fileOutputStream, exportFilter, collectionName, batchSize,
                mongoBatchSize, identifiers, exportRecordsUpdatedAfter, exportRecordsCreatedAfter);
          }
        }
      }
    }

    // 7. No need to export Quartz jobs. They can be recreated based on accountId/appId/triggerId etc.
    // Export 'kmsConfig' including the global KMS secret manager if configured (e.g. QA and PROD, but not in fremium
    // yet).
    if (exportConfigs) {
      String kmsConfigCollectionName = getCollectionName(KmsConfig.class);
      if (isExportable(toBeExported, kmsConfigCollectionName)) {
        List<String> accountIdList = Arrays.asList(accountId);
        DBObject exportFilter = new BasicDBObject("accountId", new BasicDBObject("$in", accountIdList));
        List<String> records = mongoExportImport.exportRecords(exportFilter, kmsConfigCollectionName);
        batchExportToStream(zipOutputStream, fileOutputStream, records, kmsConfigCollectionName, batchSize);
      }
    }

    log.info("Flushing exported data into a zip file {}.", zipFileName);
    zipOutputStream.flush();
    zipOutputStream.close();
    fileOutputStream.flush();
    fileOutputStream.close();
    log.info("Finished flushing {} bytes of exported account data into a zip file {}.", zipFile.length(), zipFileName);

    return Response.ok(zipFile, MediaType.APPLICATION_OCTET_STREAM)
        .header("content-disposition", "attachment; filename = " + zipFileName)
        .build();
  }

  @GET
  @Path("/export")
  @Produces(MediaType.APPLICATION_JSON)
  @ExceptionMetered
  public Response exportAccountData(@QueryParam("accountId") final String accountId,
      @QueryParam("mode") @DefaultValue("ALL") ExportMode exportMode,
      @QueryParam("entityTypes") List<String> entityTypes) throws Exception {
    // Only if the user the account administrator or in the Harness user group can perform the export operation.
    if (!userService.isAccountAdmin(accountId)) {
      String errorMessage = "User is not account administrator and can't perform the export operation.";
      RestResponse<Boolean> restResponse = accountPermissionUtils.checkIfHarnessUser(errorMessage);
      if (restResponse != null) {
        log.error(errorMessage);
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }
    }

    if (exportMode == ExportMode.SPECIFIC && isEmpty(entityTypes)) {
      throw new IllegalArgumentException("Export type is SPECIFIC but no entity type is specified.");
    }

    String zipFileName = accountId + ZIP_FILE_SUFFIX;
    File zipFile = new File(Files.createTempDir(), zipFileName);
    FileOutputStream fileOutputStream = new FileOutputStream(zipFile);
    ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);

    Map<String, Boolean> toBeExported = getToBeExported(exportMode, entityTypes);
    List<String> appIds = appService.getAppIdsByAccountId(accountId);

    // 1. Export harness schema collection.
    String schemaCollectionName = getCollectionName(Schema.class);
    if (isExportable(toBeExported, schemaCollectionName)) {
      DBObject emptyFilter = new BasicDBObject();
      List<String> schemas = mongoExportImport.exportRecords(emptyFilter, schemaCollectionName);
      if (isEmpty(schemas)) {
        log.warn("Schema collection data doesn't exist, schema version data won't be exported.");
      } else {
        exportToStreamWithoutBatching(zipOutputStream, fileOutputStream, schemas, schemaCollectionName);
      }
    }

    // 2. Export account data.
    String accountCollectionName = getCollectionName(Account.class);
    if (isExportable(toBeExported, accountCollectionName)) {
      DBObject idFilter = new BasicDBObject("_id", accountId);
      List<String> accounts = mongoExportImport.exportRecords(idFilter, accountCollectionName);
      if (isEmpty(accounts)) {
        throw new IllegalArgumentException(
            "Account '" + accountId + "' doesn't exist, can't proceed with this export operation.");
      } else {
        exportToStreamWithoutBatching(zipOutputStream, fileOutputStream, accounts, accountCollectionName);
      }
    }

    // 3. Export all users
    String userCollectionName = getCollectionName(User.class);
    if (isExportable(toBeExported, userCollectionName)) {
      DBObject accountsFilter =
          new BasicDBObject(UserKeys.accounts, new BasicDBObject("$in", new String[] {accountId}));
      List<String> users = mongoExportImport.exportRecords(accountsFilter, userCollectionName);
      exportToStreamWithoutBatching(zipOutputStream, fileOutputStream, users, userCollectionName);
    }

    DBObject accountIdFilter = new BasicDBObject("accountId", accountId);
    DBObject appIdsFilter = new BasicDBObject("appId", new BasicDBObject("$in", appIds));

    // 4. Export all applications
    String applicationsCollectionName = getCollectionName(Application.class);
    if (isExportable(toBeExported, applicationsCollectionName)) {
      List<String> applications = mongoExportImport.exportRecords(accountIdFilter, applicationsCollectionName);
      exportToStreamWithoutBatching(zipOutputStream, fileOutputStream, applications, applicationsCollectionName);
    }

    DBObject accountOrAppIdsFilter = new BasicDBObject();
    accountOrAppIdsFilter.put("$or", Arrays.asList(accountIdFilter, appIdsFilter));

    // 5. Export config file content that are persisted in the Mongo GridFs. "configs.files" and "configs.chunks"
    // are not managed by Morphia, need to handle it separately and only export those entries associated with
    // CONFIG_FILE type of secrets for now,
    exportConfigFilesContent(zipOutputStream, fileOutputStream, accountOrAppIdsFilter);

    // 6. Export all other Harness entities that has @Entity annotation excluding what's in the blacklist.
    for (Entry<String, Class<? extends PersistentEntity>> entry : genericExportableEntityTypes.entrySet()) {
      if (isExportable(toBeExported, entry.getKey())) {
        Class<? extends PersistentEntity> entityClazz = entry.getValue();
        if (entityClazz != null) {
          final DBObject exportFilter;
          // 'gitCommits' and 'yamlChangeSet' need special export filter.
          if (GitCommit.class == entityClazz) {
            exportFilter = getGitCommitExportFilter(accountIdFilter);
          } else if (YamlChangeSet.class == entityClazz) {
            exportFilter = getYamlChangeSetExportFilter(accountIdFilter);
          } else if (FeatureFlag.class == entityClazz) {
            // 8. Need to migrate the feature flag also as part of account migration
            //    https://harness.atlassian.net/browse/PL-9683
            exportFilter = getFeatureFlagExportFilter(accountId);
          } else {
            exportFilter = accountOrAppIdsFilter;
          }
          String collectionName = getCollectionName(entityClazz);
          List<String> records = mongoExportImport.exportRecords(exportFilter, collectionName);
          exportToStreamWithoutBatching(zipOutputStream, fileOutputStream, records, collectionName);
        }
      }
    }

    // 7. No need to export Quartz jobs. They can be recreated based on accountId/appId/triggerId etc.
    // Export 'kmsConfig' including the global KMS secret manager if configured (e.g. QA and PROD, but not in fremium
    // yet).
    String kmsConfigCollectionName = getCollectionName(KmsConfig.class);
    if (isExportable(toBeExported, kmsConfigCollectionName)) {
      List<String> accountIdList = Arrays.asList(accountId);
      DBObject exportFilter = new BasicDBObject("accountId", new BasicDBObject("$in", accountIdList));
      List<String> records = mongoExportImport.exportRecords(exportFilter, kmsConfigCollectionName);
      exportToStreamWithoutBatching(zipOutputStream, fileOutputStream, records, kmsConfigCollectionName);
    }

    log.info("Flushing exported data into a zip file {}.", zipFileName);
    zipOutputStream.flush();
    zipOutputStream.close();
    fileOutputStream.flush();
    fileOutputStream.close();
    log.info("Finished flushing {} bytes of exported account data into a zip file {}.", zipFile.length(), zipFileName);

    return Response.ok(zipFile, MediaType.APPLICATION_OCTET_STREAM)
        .header("content-disposition", "attachment; filename = " + zipFileName)
        .build();
  }

  private boolean isExportable(Map<String, Boolean> toBeExported, String collectionName) {
    Boolean exportable = toBeExported.get(collectionName);
    return exportable != null && exportable;
  }

  private void exportToStream(ZipOutputStream zipOutputStream, FileOutputStream fileOutputStream, List<String> records,
      String zipEntryName) throws IOException {
    ZipEntry zipEntryData = new ZipEntry(zipEntryName);
    log.info("Zipping entry: {}", zipEntryName);
    zipOutputStream.putNextEntry(zipEntryData);
    JsonArray jsonArrayRecord = convertStringListToJsonArray(records);
    String jsonString = gson.toJson(jsonArrayRecord);
    zipOutputStream.write(jsonString.getBytes(Charset.defaultCharset()));
    zipOutputStream.flush();
    fileOutputStream.flush();
  }

  private void batchExportToStream(ZipOutputStream zipOutputStream, FileOutputStream fileOutputStream,
      List<String> records, String collectionName, int batchSize) throws IOException {
    int numberOfBatches = isNotEmpty(records) && records.size() % batchSize == 0 ? records.size() / batchSize
                                                                                 : 1 + records.size() / batchSize;

    for (int i = 0; i < numberOfBatches; i++) {
      List<String> batchRecord = records.subList(i * batchSize, Math.min((i + 1) * batchSize, records.size()));
      String zipEntryName = collectionName + "_" + i + JSON_FILE_SUFFIX;
      exportToStream(zipOutputStream, fileOutputStream, batchRecord, zipEntryName);
    }

    zipOutputStream.flush();
    fileOutputStream.flush();
    log.info("{} '{}' records have been exported.", records.size(), collectionName);
  }

  private DBObject getGitCommitExportFilter(DBObject accountIdFilter) {
    // 'gitCommits' within last 1 month should be exported.
    DBObject createdAtFilter = new BasicDBObject(
        "createdAt", new BasicDBObject("$gt", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)));
    return new BasicDBObject("$and", Arrays.asList(accountIdFilter, createdAtFilter));
  }

  private DBObject getYamlChangeSetExportFilter(DBObject accountIdFilter) {
    // 'yamlChangeSet' in QUEUED status should be exported.
    DBObject statusQueuedFilter = new BasicDBObject("status", Status.QUEUED.name());
    return new BasicDBObject("$and", Arrays.asList(accountIdFilter, statusQueuedFilter));
  }

  @VisibleForTesting
  public DBObject getFeatureFlagExportFilter(String accountId) {
    DBObject globallyEnabledFilter = new BasicDBObject(FeatureFlagKeys.enabled, Boolean.TRUE);
    DBObject accountLevelEnabledFilter = new BasicDBObject(FeatureFlagKeys.accountIds, accountId);
    DBObject accountIdAndGloballyEnabledFilter =
        new BasicDBObject("$or", Arrays.asList(globallyEnabledFilter, accountLevelEnabledFilter));
    DBObject obsoleteFeatureFlagFilter = new BasicDBObject(FeatureFlagKeys.obsolete, Boolean.FALSE);
    return new BasicDBObject("$and", Arrays.asList(accountIdAndGloballyEnabledFilter, obsoleteFeatureFlagFilter));
  }

  private void exportConfigFilesContent(ZipOutputStream zipOutputStream, FileOutputStream fileOutputStream,
      DBObject accountOrAppIdsFilter) throws IOException {
    log.info("[AccountExportImportResource]: Inside exportConfigFilesContent");
    // 1. Export EncryptedData records
    String encryptedDataCollectionName = getCollectionName(EncryptedData.class);
    List<String> encryptedDataRecords =
        mongoExportImport.exportRecords(accountOrAppIdsFilter, encryptedDataCollectionName);
    exportToStreamWithoutBatching(zipOutputStream, fileOutputStream, encryptedDataRecords, encryptedDataCollectionName);
    log.info("[AccountExportImportResource]: Exported encryptedDataRecords {}", encryptedDataRecords);
    // 2. Find out all file IDs referred by KMS/CONFIG_FILE encrypted records.
    List<ObjectId> configFileIds = new ArrayList<>();
    if (isNotEmpty(encryptedDataRecords)) {
      for (String encryptedDataRecord : encryptedDataRecords) {
        JsonElement encryptedDataElement = jsonParser.parse(encryptedDataRecord);
        // Only KMS type of encrypted records have file content saved in File serivce/GridFS, which need to be exported.
        EncryptionType encryptionType =
            EncryptionType.valueOf(encryptedDataElement.getAsJsonObject().get("encryptionType").getAsString());
        log.info("[AccountExportImportResource]: Encryption type for encryptedDataRecord {} is {}", encryptedDataRecord,
            encryptionType);
        JsonElement type = encryptedDataElement.getAsJsonObject().get("type");
        if (type == null || type.isJsonNull()) {
          continue;
        }
        SettingVariableTypes settingVariableType = SettingVariableTypes.valueOf(type.getAsString());
        log.info("[AccountExportImportResource]: Settings variable type for encryptedDataRecord {} is {}",
            encryptedDataRecord, settingVariableType);
        if (settingVariableType == SettingVariableTypes.CONFIG_FILE) {
          String fileId = encryptedDataElement.getAsJsonObject().get("encryptedValue").getAsString();
          ObjectId objectId = getObjectIdFromFileId(fileId);
          log.info("[AccountExportImportResource]: FileId is {} and objectId is {}", fileId, objectId);
          if (objectId != null) {
            log.info("[AccountExportImportResource]: Adding fileId {} to configFileIds {}", fileId, configFileIds);
            configFileIds.add(new ObjectId(fileId));
          }
        }
      }
    }
    log.info(
        "[AccountExportImportResource]: Going to export all fileIds is configFileIds: {} from config.files collection ",
        configFileIds);
    // 3. Export all 'configs.files' records in the configFileIds list.
    DBObject inIdsFilter = new BasicDBObject("_id", new BasicDBObject("$in", configFileIds));
    List<String> configFilesRecords = mongoExportImport.exportRecords(inIdsFilter, COLLECTION_CONFIG_FILES);
    exportToStreamWithoutBatching(zipOutputStream, fileOutputStream, configFilesRecords, COLLECTION_CONFIG_FILES);
    log.info(
        "[AccountExportImportResource]: Export completed for fileIds is configFileIds: {} from config.files collection ",
        configFileIds);
    // 4. Export all 'configs.chunks' records in the configFileIds list.
    log.info(
        "[AccountExportImportResource]: Going to export all fileIds is configFileIds: {} from config.chunks collection ",
        configFileIds);
    DBObject inFilesIdFilter = new BasicDBObject("files_id", new BasicDBObject("$in", configFileIds));
    List<String> configChunkRecords = mongoExportImport.exportRecords(inFilesIdFilter, COLLECTION_CONFIG_CHUNKS);
    exportToStreamWithoutBatching(zipOutputStream, fileOutputStream, configChunkRecords, COLLECTION_CONFIG_CHUNKS);
    log.info(
        "[AccountExportImportResource]: Export completed for fileIds is configFileIds: {} from config.chunks collection ",
        configFileIds);
  }

  private void exportToStreamWithoutBatching(ZipOutputStream zipOutputStream, FileOutputStream fileOutputStream,
      List<String> records, String collectionName) throws IOException {
    String zipEntryName = collectionName + JSON_FILE_SUFFIX;
    ZipEntry zipEntry = new ZipEntry(zipEntryName);
    log.info("Zipping entry: {}", zipEntryName);
    zipOutputStream.putNextEntry(zipEntry);
    JsonArray jsonArray = convertStringListToJsonArray(records);
    String jsonString = gson.toJson(jsonArray);
    zipOutputStream.write(jsonString.getBytes(Charset.defaultCharset()));
    zipOutputStream.flush();
    fileOutputStream.flush();
    log.info("{} '{}' records have been exported.", records.size(), collectionName);
  }

  @POST
  @Path("/import")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.APPLICATION_JSON)
  @ExceptionMetered
  public RestResponse<ImportStatusReport> importAccountData(@QueryParam("accountId") final String accountId,
      @QueryParam("mode") @DefaultValue("UPSERT") ImportMode importMode,
      @QueryParam("singleCollectionImport") boolean singleCollectionImport,
      @QueryParam("disableSchemaCheck") boolean disableSchemaCheck, @QueryParam("adminUser") String adminUserEmail,
      @QueryParam("adminPassword") String adminPassword, @QueryParam("accountName") String newAccountName,
      @QueryParam("companyName") String newCompanyName, @FormDataParam("file") final InputStream uploadInputStream,
      @QueryParam("updateAccountAttributes") boolean updateAccountAttributes) throws Exception {
    // Only if the user the account administrator or in the Harness user group can perform the export operation.
    if (!userService.isAccountAdmin(accountId)) {
      String errorMessage = "User is not account administrator and can't perform the import operation.";
      RestResponse<ImportStatusReport> restResponse = accountPermissionUtils.checkIfHarnessUser(errorMessage);
      if (restResponse != null) {
        log.error(errorMessage);
        return restResponse;
      }
    }

    log.info("Started importing data for account '{}'.", accountId);
    Map<String, String> zipEntryDataMap = readZipEntries(uploadInputStream);
    log.info("Finished reading uploaded input stream in zip format.");

    List<ImportStatus> importStatuses = new ArrayList<>();

    // 1. Check schema version compatibility. Would enforce the exported collections' schema is the same as the current
    // installation's
    if (!disableSchemaCheck) {
      checkSchemaVersionCompatibility(zipEntryDataMap);
    }

    // 2.1 Preserve the account license expiration, license unit etc. information.
    Account account = accountService.get(accountId);
    // LicenseInfo should not be null for migrated Freemium accounts
    LicenseInfo licenseInfo = account.getLicenseInfo();

    // 2.2 Import account data first.
    String accountCollectionName = getCollectionName(Account.class);
    List<String> accounts = convertJsonArrayListToStringList(getJsonArray(zipEntryDataMap, accountCollectionName));
    if (isNotEmpty(accounts)) {
      importStatuses.add(mongoExportImport.importRecords(accountCollectionName, accounts, importMode));
    }

    // 3. Import users
    String userCollectionName = getCollectionName(User.class);
    List<String> users = convertJsonArrayListToStringList(getJsonArray(zipEntryDataMap, userCollectionName));
    // Find potential user email clashes and find the mapping of imported user id to existing user id.
    Map<String, String> clashedUserIdMapping =
        findClashedUserIdMapping(accountId, getJsonArray(zipEntryDataMap, userCollectionName));
    if (isNotEmpty(users)) {
      importStatuses.add(mongoExportImport.importRecords(userCollectionName, users, importMode));
    }

    // 4. Import applications
    String applicationsCollectionName = getCollectionName(Application.class);
    List<String> applications = convertJsonArrayListToStringList(
        getJsonArray(zipEntryDataMap, applicationsCollectionName, clashedUserIdMapping));
    if (isNotEmpty(applications)) {
      importStatuses.add(mongoExportImport.importRecords(applicationsCollectionName, applications, importMode));
    }

    // 5. Import all "encryptedRecords", "configs.file" and "configs.chunks" content
    String encryptedDataCollectionName = getCollectionName(EncryptedData.class);
    List<String> encryptedData = convertJsonArrayListToStringList(
        getJsonArray(zipEntryDataMap, encryptedDataCollectionName, clashedUserIdMapping));
    if (isNotEmpty(encryptedData)) {
      importStatuses.add(mongoExportImport.importRecords(encryptedDataCollectionName, encryptedData, importMode));
    }

    List<String> configFiles = convertJsonArrayListToStringList(getJsonArray(zipEntryDataMap, COLLECTION_CONFIG_FILES));
    if (isNotEmpty(configFiles)) {
      ImportStatus importStatus = mongoExportImport.importRecords(COLLECTION_CONFIG_FILES, configFiles, importMode);
      if (importStatus != null) {
        importStatuses.add(importStatus);
      }
    }
    List<String> configChunks =
        convertJsonArrayListToStringList(getJsonArray(zipEntryDataMap, COLLECTION_CONFIG_CHUNKS));
    if (isNotEmpty(configChunks)) {
      ImportStatus importStatus = mongoExportImport.importRecords(COLLECTION_CONFIG_CHUNKS, configChunks, importMode);
      if (importStatus != null) {
        importStatuses.add(importStatus);
      }
    }

    // 6. Import all other entity types.
    for (Entry<String, Class<? extends PersistentEntity>> entry : genericExportableEntityTypes.entrySet()) {
      String collectionName = getCollectionName(entry.getValue());
      List<String> jsonArray =
          convertJsonArrayListToStringList(getJsonArray(zipEntryDataMap, collectionName, clashedUserIdMapping));
      if (isEmpty(jsonArray)) {
        log.info("No data found for collection '{}' to import.", collectionName);
      } else {
        ImportStatus importStatus = null;
        if (entry.getValue() == FeatureFlag.class) {
          importStatus = enableFeatureFlagForAccount(accountId, collectionName, jsonArray);
        } else {
          importStatus = mongoExportImport.importRecords(collectionName, jsonArray, importMode);
        }
        if (importStatus != null) {
          importStatuses.add(importStatus);
        }
      }
    }

    // 7. import kmsConfig as a special handling.
    String kmsConfigCollectionName = getCollectionName(KmsConfig.class);
    List<String> kmsConfigs =
        convertJsonArrayListToStringList(getJsonArray(zipEntryDataMap, kmsConfigCollectionName, clashedUserIdMapping));
    if (isNotEmpty(kmsConfigs)) {
      ImportStatus importStatus = mongoExportImport.importRecords(kmsConfigCollectionName, kmsConfigs, importMode);
      if (importStatus != null) {
        importStatuses.add(importStatus);
      }
    }

    // 8. Update license to the previously set license (unit, type etc.)
    if (updateAccountAttributes && licenseInfo != null) {
      licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
      licenseService.updateAccountLicense(accountId, licenseInfo);
      log.info("Updated license of account {} to: {}", accountId, licenseInfo);
    }

    // 9. Update the first account administrator's password for post-migration validation by CSE team if specified
    if (!StringUtils.isEmpty(adminUserEmail) && !StringUtils.isEmpty(adminPassword)) {
      updateAdminUserPassword(accountId, adminUserEmail, adminPassword);
    }

    // PL-3126: When the 'accountName' query parameter is provided, it means the account name need to be renamed at
    // account migration/import time.
    if (updateAccountAttributes && isNotEmpty(newAccountName)) {
      accountService.updateAccountName(accountId, newAccountName, newCompanyName);
    }

    // 10. Reinstantiate Quartz jobs (recreate through APIs) in the new cluster
    if (!singleCollectionImport) {
      reinstantiateQuartzJobs(accountId, importStatuses);
    }

    log.info("{} collections has been imported.", importStatuses.size());
    log.info("Finished importing data for account '{}'.", accountId);

    return new RestResponse<>(ImportStatusReport.builder().statuses(importStatuses).mode(importMode).build());
  }

  @VisibleForTesting
  public ImportStatus enableFeatureFlagForAccount(String accountId, String collectionName, List<String> records) {
    int importedRecords = 0;
    int idClashCount = 0;

    for (String record : records) {
      // Check for if there is any existing record with the same _id.
      DBObject importRecord = BasicDBObject.parse(record);
      String featureName = ((BasicDBObject) importRecord).getString(FeatureFlagKeys.name);
      FeatureName featureEnum = null;
      try {
        featureEnum = FeatureName.valueOf(featureName);
      } catch (IllegalArgumentException iae) {
        log.error(
            "IllegalArgumentException exception occurred while fetch feature " + featureName.replaceAll("[\r\n]", ""),
            iae);
      }

      if (featureEnum != null) {
        if (featureFlagService.isEnabled(featureEnum, accountId)) {
          log.info("Feature {} is already enabled for accountId {} or is enabled globally",
              featureName.replaceAll("[\r\n]", ""), accountId);
          idClashCount++;
        } else {
          featureFlagService.enableAccount(featureEnum, accountId);
          importedRecords++;
        }
      }
    }

    return ImportStatus.builder()
        .collectionName(collectionName)
        .imported(importedRecords)
        .idClashes(idClashCount)
        .build();
  }

  private void updateAdminUserPassword(String accountId, String adminUserEmail, String adminPassword) {
    User adminUser = userService.getUserByEmail(adminUserEmail);
    if (adminUser != null) {
      String adminUserId = adminUser.getUuid();
      // CSE should notify the admin user of the customer to reset password after validated the migrated account.
      String hashed = hashpw(adminPassword, BCrypt.gensalt());
      wingsPersistence.update(adminUser,
          wingsPersistence.createUpdateOperations(User.class)
              .set(UserKeys.passwordHash, hashed)
              .set(UserKeys.passwordExpired, false)
              .set(UserKeys.passwordChangedAt, System.currentTimeMillis()));
      authService.invalidateAllTokensForUser(adminUserId);

      log.info("Updated password of admin user {} with id {} for account {} during account import",
          adminUser.getEmail(), adminUserId, accountId);
    }
  }

  private void reinstantiateQuartzJobs(String accountId, List<ImportStatus> importStatuses) {
    // 1. Recreate account level jobs
    AlertCheckJob.addWithDelay(scheduler, accountId);
    InstanceStatsCollectorJob.addWithDelay(scheduler, accountId);
    LimitVicinityCheckerJob.addWithDelay(scheduler, accountId);

    // Recreate application or lower level jobs each need some special handling.
    List<String> appIds = appService.getAppIdsByAccountId(accountId);
    int importedJobCount = 0;

    // 3. ScheduledTriggerJob
    List<Trigger> triggers = getAllScheduledTriggersForAccount(accountId, appIds);
    for (Trigger trigger : triggers) {
      // Scheduled triggers is using the cron expression as trigger. No need to add special delay.
      ScheduledTriggerJob.add(scheduler, accountId, trigger.getAppId(), trigger.getUuid(), trigger);
      importedJobCount++;
    }

    log.info("{} cron jobs has been recreated.", importedJobCount);

    if (importedJobCount > 0) {
      ImportStatus importStatus =
          ImportStatus.builder().collectionName(COLLECTION_QUARTZ_JOBS).imported(importedJobCount).build();
      importStatuses.add(importStatus);
    }
  }

  Map<String, String> findClashedUserIdMapping(String accountId, JsonArray users) {
    // The users to be imported might have the same email with existing user in the cluster it's being imported into.
    // This method will build the mapping between these clashed user ids. Upon occurrence of user clash with the same
    // email:
    // 1. The user with email clash won't be imported.
    // 2. The existing user with the same email need to be added to the account to be exported.
    Map<String, String> userIdMapping = new HashMap<>();
    if (users != null && users.size() > 0) {
      Account account = wingsPersistence.get(Account.class, accountId);
      for (JsonElement user : users) {
        JsonObject userObject = user.getAsJsonObject();
        String userId = userObject.get("_id").getAsString();
        final String email = userObject.get("email").getAsString();
        if (isEmpty(email)) {
          String userName = userObject.get("name").getAsString();
          // Ignore as this user doesn't have an email attribute
          log.info("User '{}' with id {} doesn't have an email attribute, it will be skipped from being imported.",
              userName, userId);
          continue;
        }
        User existingUser = userService.getUserByEmail(email);
        if (existingUser != null && !existingUser.getUuid().equals(userId)) {
          userIdMapping.put(userId, existingUser.getUuid());
          log.info(
              "User '{}' with email '{}' clashes with one existing user '{}'.", userId, email, existingUser.getUuid());
          // Adding the new import account into the account list of the existing user.
          if (isEmpty(existingUser.getAccounts())) {
            existingUser.setAccounts(Collections.singletonList(account));
          }
          if (!existingUser.getAccounts().contains(account)) {
            existingUser.getAccounts().add(account);
          }
          wingsPersistence.save(existingUser);
        }
      }
      if (userIdMapping.size() > 0) {
        log.info(
            "{} users have email clashes with existing users and all of the references to it in the imported records need to be replaced.",
            userIdMapping.size());
      }
    }
    return userIdMapping;
  }

  Map<String, String> findClashedUserIdMapping(String accountId, List<JsonArray> usersList) {
    // The users to be imported might have the same email with existing user in the cluster it's being imported into.
    // This method will build the mapping between these clashed user ids. Upon occurrence of user clash with the same
    // email:
    // 1. The user with email clash won't be imported.
    // 2. The existing user with the same email need to be added to the account to be exported.
    Map<String, String> userIdMapping = new HashMap<>();
    if (isNotEmpty(usersList)) {
      for (JsonArray users : usersList) {
        if (users != null && users.size() > 0) {
          Account account = wingsPersistence.get(Account.class, accountId);
          for (JsonElement user : users) {
            JsonObject userObject = user.getAsJsonObject();
            String userId = userObject.get("_id").getAsString();
            final String email = userObject.get("email").getAsString();
            if (isEmpty(email)) {
              String userName = userObject.get("name").getAsString();
              // Ignore as this user doesn't have an email attribute
              log.info("User '{}' with id {} doesn't have an email attribute, it will be skipped from being imported.",
                  userName, userId);
              continue;
            }
            User existingUser = userService.getUserByEmail(email);
            if (existingUser != null && !existingUser.getUuid().equals(userId)) {
              userIdMapping.put(userId, existingUser.getUuid());
              log.info("User '{}' with email '{}' clashes with one existing user '{}'.", userId, email,
                  existingUser.getUuid());
              // Adding the new import account into the account list of the existing user.
              if (isEmpty(existingUser.getAccounts())) {
                existingUser.setAccounts(Collections.singletonList(account));
              }
              if (!existingUser.getAccounts().contains(account)) {
                existingUser.getAccounts().add(account);
              }
              wingsPersistence.save(existingUser);
            }
          }
          if (userIdMapping.size() > 0) {
            log.info(
                "{} users have email clashes with existing users and all of the references to it in the imported records need to be replaced.",
                userIdMapping.size());
          }
        }
      }
    }
    return userIdMapping;
  }

  String replaceClashedUserIds(String collectionJson, Map<String, String> clashedUserIdMapping) {
    // 3. All references to the old user id in all records to be imported need to be replaced with the new user id
    // Typical reference to user ids are in 'createdBy/uuid', 'lastUpdatedBy/uuid' and user group membership.
    String result = collectionJson;
    if (isNotEmpty(clashedUserIdMapping)) {
      for (Entry<String, String> idMappingEntry : clashedUserIdMapping.entrySet()) {
        result = result.replaceAll("\"" + idMappingEntry.getKey() + "\"", "\"" + idMappingEntry.getValue() + "\"");
      }
    }
    return result;
  }

  private List<Trigger> getAllScheduledTriggersForAccount(String accountId, List<String> appIds) {
    List<Trigger> triggers = new ArrayList<>();
    Query<Trigger> query = wingsPersistence.createQuery(Trigger.class).filter("appId in", appIds);
    try (HIterator<Trigger> iterator = new HIterator<>(query.fetch())) {
      for (Trigger trigger : iterator) {
        if (trigger.getCondition().getConditionType() == TriggerConditionType.SCHEDULED) {
          triggers.add(trigger);
        }
      }
    }
    return triggers;
  }

  private List<LdapSettings> getAllLdapSettingsForAccount(String accountId) {
    List<LdapSettings> ldapSettings = new ArrayList<>();
    Query<LdapSettings> query = wingsPersistence.createQuery(LdapSettings.class)
                                    .filter(LdapSettingsKeys.accountId, accountId)
                                    .filter("type", SSOType.LDAP);
    Iterator<LdapSettings> iterator = query.iterator();
    while (iterator.hasNext()) {
      ldapSettings.add(iterator.next());
    }

    return ldapSettings;
  }

  private Map<String, String> readZipEntries(InputStream inputStream) throws IOException {
    try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
      Map<String, String> collectionDataMap = new HashMap<>();
      ZipEntry zipEntry;
      while ((zipEntry = zipInputStream.getNextEntry()) != null) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        int len = 0;
        byte[] buffer = new byte[1024];
        while ((len = zipInputStream.read(buffer)) > 0) {
          outputStream.write(buffer, 0, len);
        }
        if (StringUtils.isNotBlank(zipEntry.getName())) {
          String[] parts = zipEntry.getName().split("/");
          String zipEntryName;
          if (parts.length > 1) {
            zipEntryName = parts[parts.length - 1];
          } else {
            zipEntryName = zipEntry.getName();
          }
          collectionDataMap.put(zipEntryName, new String(outputStream.toByteArray(), Charset.defaultCharset()));
        }
      }
      return collectionDataMap;
    }
  }

  private List<JsonArray> getJsonArray(Map<String, String> zipDataMap, String collectionName) {
    return getJsonArray(zipDataMap, collectionName, null);
  }

  private List<JsonArray> getJsonArray(
      Map<String, String> zipDataMap, String collectionName, Map<String, String> clashedUserIdMapping) {
    List<JsonArray> jsonArrayList = new ArrayList<>();
    String zipEntryName = collectionName + JSON_FILE_SUFFIX;
    String json = zipDataMap.get(zipEntryName);

    String batchZipEntryName = collectionName + "_0" + JSON_FILE_SUFFIX;
    String batchJson = zipDataMap.get(batchZipEntryName);

    if (isNotEmpty(json)) {
      // Replace clashed user id with the new user id in the current system.
      json = replaceClashedUserIds(json, clashedUserIdMapping);
      jsonArrayList.add((JsonArray) jsonParser.parse(json));
      return jsonArrayList;
    }
    if (isNotEmpty(batchJson)) {
      int i = 0;
      while (isNotEmpty(batchJson)) {
        batchZipEntryName = collectionName + "_" + i + JSON_FILE_SUFFIX;
        batchJson = zipDataMap.get(batchZipEntryName);
        if (isNotEmpty(batchJson)) {
          batchJson = replaceClashedUserIds(batchJson, clashedUserIdMapping);
          jsonArrayList.add((JsonArray) jsonParser.parse(batchJson));
        }
        i++;
      }
      return jsonArrayList;
    }
    return null;
  }

  private List<String> convertJsonArrayToStringList(JsonArray jsonArray) {
    if (jsonArray != null) {
      List<String> result = new ArrayList<>(jsonArray.size());
      for (JsonElement jsonElement : jsonArray) {
        result.add(jsonElement.toString());
      }
      return result;
    }
    return new ArrayList<>();
  }

  private List<String> convertJsonArrayListToStringList(List<JsonArray> jsonArrayList) {
    List<String> result = new ArrayList<>();
    if (isNotEmpty(jsonArrayList)) {
      for (JsonArray jsonArray : jsonArrayList) {
        if (jsonArray != null) {
          result.addAll(convertJsonArrayToStringList(jsonArray));
        }
      }
    }
    return result;
  }

  private JsonArray convertStringListToJsonArray(List<String> records) {
    JsonArray jsonArray = new JsonArray();
    for (String record : records) {
      jsonArray.add(jsonParser.parse(record));
    }
    return jsonArray;
  }

  private ObjectId getObjectIdFromFileId(String fileId) {
    try {
      return new ObjectId(fileId);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid BSON object id: " + fileId);
      return null;
    }
  }

  private JsonObject getSchema(JsonParser jsonParser) {
    DBObject idFilter = new BasicDBObject("_id", "schema");
    List<String> schemas = mongoExportImport.exportRecords(idFilter, getCollectionName(Schema.class));
    if (schemas.size() == 0) {
      throw new IllegalArgumentException("Schema collection doesn't exist, can't proceed with this export operation.");
    } else {
      String schemaJson = schemas.get(0);
      return jsonParser.parse(schemaJson).getAsJsonObject();
    }
  }

  private int getSchemaVersion(JsonObject schema) {
    return schema.get("version").getAsInt();
  }

  private void checkSchemaVersionCompatibility(Map<String, String> zipDataMap) {
    String collectionName = getCollectionName(Schema.class);
    JsonArray schemas = getJsonArray(zipDataMap, collectionName).get(0);
    if (schemas != null) {
      // Schema version. There should be only one entry.
      int importSchemaVersion = getSchemaVersion(schemas.get(0).getAsJsonObject());
      int currentSchemaVersion = getSchemaVersion(getSchema(jsonParser));
      log.info("Import schema version is {}; Current cluster's schema version is {}.", importSchemaVersion,
          currentSchemaVersion);
      if (importSchemaVersion == currentSchemaVersion) {
        log.info("Schema compatibility check has passed. Proceed further to import account data.");
      } else {
        throw new WingsException("Incompatible schema version! Import schema version: " + importSchemaVersion
            + "; Current schema version: " + currentSchemaVersion);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void findExportableEntityTypes() {
    morphia.getMapper().getMappedClasses().forEach(mc -> {
      Class<? extends PersistentEntity> clazz = (Class<? extends PersistentEntity>) mc.getClazz();
      if (mc.getEntityAnnotation() != null && isAnnotatedExportable(clazz)) {
        // Find out non-abstract classes with both 'Entity' and 'HarnessEntity' annotation.
        String mongoCollectionName = mc.getEntityAnnotation().value();
        if (!includedMongoCollections.contains(mongoCollectionName)) {
          log.debug("Collection '{}' is exportable", mongoCollectionName);
          genericExportableEntityTypes.put(mongoCollectionName, clazz);
        }
      }
    });
  }

  private boolean isAnnotatedExportable(Class<? extends PersistentEntity> clazz) {
    HarnessEntity harnessEntity = clazz.getAnnotation(HarnessEntity.class);
    return harnessEntity != null && harnessEntity.exportable();
  }

  public Map<String, Boolean> getToBeExported(ExportMode exportType, List<String> entityTypes) {
    Map<String, Boolean> toBeExported = new HashMap<>();
    switch (exportType) {
      case ALL:
        for (String exportableEntityType : genericExportableEntityTypes.keySet()) {
          toBeExported.put(exportableEntityType, Boolean.TRUE);
        }
        for (String includedEntityType : includedMongoCollections) {
          toBeExported.put(includedEntityType, Boolean.TRUE);
        }
        break;
      case SPECIFIC:
        for (String exportableEntityType : genericExportableEntityTypes.keySet()) {
          toBeExported.put(exportableEntityType, Boolean.FALSE);
        }
        for (String includedEntityType : includedMongoCollections) {
          toBeExported.put(includedEntityType, Boolean.FALSE);
        }
        for (String entityType : entityTypes) {
          toBeExported.put(entityType, Boolean.TRUE);
        }
        break;
      default:
        throw new IllegalArgumentException("Export type " + exportType + " is not supported.");
    }
    return toBeExported;
  }
}
