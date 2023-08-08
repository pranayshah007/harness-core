/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.account;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.CDS_QUERY_OPTIMIZATION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.Base.ACCOUNT_ID_KEY2;

import static java.lang.reflect.Modifier.isAbstract;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.delegate.service.intfc.DelegateNgTokenService;
import io.harness.event.timeseries.processor.TimescaleDataCleanup;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.checker.rate.UsageBucket;
import io.harness.limits.checker.rate.UsageBucket.UsageBucketKeys;
import io.harness.mongo.MongoPersistence;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.HIterator;
import io.harness.persistence.PersistentEntity;
import io.harness.scheduler.PersistentScheduler;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.DeletedEntity;
import software.wings.beans.DeletedEntity.DeletedEntityKeys;
import software.wings.beans.DeletedEntity.DeletedEntityType;
import software.wings.beans.User;
import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.beans.sso.SSOSettings;
import software.wings.scheduler.events.segment.SegmentGroupEventJobContext;
import software.wings.scheduler.events.segment.SegmentGroupEventJobContext.SegmentGroupEventJobContextKeys;
import software.wings.service.impl.ChurnedAuditFilesAndChunksCleanup;
import software.wings.service.impl.ChurnedConfigFilesAndChunksCleanup;
import software.wings.service.impl.SSOSettingServiceImpl;
import software.wings.service.impl.ServiceClassLocator;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.ownership.OwnedByAccount;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mongodb.ReadPreference;
import dev.morphia.Morphia;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.reflections.Reflections;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVERLESS, HarnessModuleComponent.CDS_GITX,
        HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(PL)
@Slf4j
public class DeleteAccountHelper {
  private static final String ACCOUNT_ID = "accountId";
  private static final String APP_ID = "appId";
  private static final String IO_HARNESS = "io.harness";
  private static final String SOFTWARE_WINGS = "software.wings";
  private static final Set<Class<? extends PersistentEntity>> separateDeletionEntities =
      new HashSet<>(Arrays.asList(Account.class, User.class, SSOSettings.class));

  private static final int CURRENT_DELETION_ALGO_NUM = 1;

  @Inject private Morphia morphia;
  @Inject ServiceClassLocator serviceClassLocator;
  @Inject private SSOSettingServiceImpl ssoSettingService;
  @Inject private UserService userService;
  @Inject private MongoPersistence hPersistence;
  @Inject @Named("BackgroundJobScheduler") private PersistentScheduler persistentScheduler;
  @Inject private PerpetualTaskService perpetualTaskService;

  @Inject private FeatureFlagService featureFlagService;
  @Inject private DelegateService delegateService;
  @Inject private DelegateNgTokenService delegateNgTokenService;
  @Inject private ChurnedAuditFilesAndChunksCleanup churnedAuditFilesAndChunksCleanup;

  @Inject private ChurnedConfigFilesAndChunksCleanup churnedConfigFilesAndChunksCleanup;
  @Inject private TimescaleDataCleanup timescaleDataCleanup;

  public List<String> deleteAllEntities(String accountId) {
    List<String> entitiesRemainingForDeletion = new ArrayList<>();
    entitiesRemainingForDeletion.addAll(deleteApplicationAccessEntities(accountId));
    entitiesRemainingForDeletion.addAll(deleteAccountAccessEntities(accountId));
    entitiesRemainingForDeletion.addAll(deleteOwnedByAccountEntities(accountId));
    removeAccountFromUsageBucketsCollection(accountId);
    removeAccountFromSegmentGroupEventContextCollection(accountId);
    return entitiesRemainingForDeletion;
  }

  @VisibleForTesting
  List<String> deleteApplicationAccessEntities(String accountId) {
    List<String> remainingEntities = new ArrayList<>();
    Reflections reflections = new Reflections(SOFTWARE_WINGS, IO_HARNESS);
    Set<Class<? extends ApplicationAccess>> applicationAccessEntities =
        reflections.getSubTypesOf(ApplicationAccess.class);
    for (Class<? extends ApplicationAccess> entity : applicationAccessEntities) {
      if (!deleteEntityUsingAppId(accountId, entity)) {
        remainingEntities.add(getCollectionName(entity));
      }
    }
    return remainingEntities;
  }

  public boolean deleteEntityUsingAppId(String accountId, Class<? extends ApplicationAccess> entity) {
    if (!isAbstract(entity.getModifiers()) && !entity.isAssignableFrom(Application.class)
        && !entity.isAssignableFrom(Account.class) && PersistentEntity.class.isAssignableFrom(entity)) {
      Class<? extends PersistentEntity> persistentEntity = entity.asSubclass(PersistentEntity.class);
      return deleteAppLevelDocuments(accountId, persistentEntity);
    }
    return true;
  }

  @VisibleForTesting
  List<String> deleteAccountAccessEntities(String accountId) {
    List<String> remainingEntities = new ArrayList<>();
    Reflections reflections = new Reflections(SOFTWARE_WINGS, IO_HARNESS);
    Set<Class<? extends AccountAccess>> accountAccessEntities = reflections.getSubTypesOf(AccountAccess.class);
    for (Class<? extends AccountAccess> entity : accountAccessEntities) {
      if (!deleteEntityUsingAccountId(accountId, entity)) {
        remainingEntities.add(getCollectionName(entity));
      }
    }
    return remainingEntities;
  }

  public boolean deleteEntityUsingAccountId(String accountId, Class<? extends AccountAccess> entity) {
    String collectionName = getCollectionName(entity);
    try {
      if (!isAbstract(entity.getModifiers()) && PersistentEntity.class.isAssignableFrom(entity)) {
        log.info("Deleting account level collection {}", collectionName);
        Class<? extends PersistentEntity> persistentEntity = entity.asSubclass(PersistentEntity.class);
        hPersistence.delete(
            hPersistence.createQuery(persistentEntity, excludeAuthority).filter(ACCOUNT_ID_KEY2, accountId));
      }
    } catch (Exception e) {
      log.error("Exception while deleting AccountAccess collection {} for accountId {}", collectionName, accountId, e);
      return false;
    }
    return true;
  }

  @VisibleForTesting
  List<String> deleteOwnedByAccountEntities(String accountId) {
    List<String> remainingEntities = new ArrayList<>();
    List<OwnedByAccount> services = serviceClassLocator.descendingServicesForInterface(OwnedByAccount.class);
    for (OwnedByAccount service : services) {
      String collectionName = getCollectionName(service.getClass());
      try {
        log.info("Deleting OwnedByAccount collection {}", collectionName);
        service.deleteByAccountId(accountId);
      } catch (Exception e) {
        log.error(
            "Exception while deleting OwnedByAccount collection {} for accountId {}", collectionName, accountId, e);
        remainingEntities.add(collectionName);
      }
    }
    return remainingEntities;
  }

  private boolean deleteAppLevelDocuments(String accountId, Class<? extends PersistentEntity> entry) {
    FindOptions findOptions = new FindOptions();
    if (featureFlagService.isEnabled(CDS_QUERY_OPTIMIZATION, accountId)) {
      findOptions.readPreference(ReadPreference.secondaryPreferred());
    }
    try (
        HIterator<Application> applicationsInAccount = new HIterator<>(hPersistence.createQuery(Application.class)
                                                                           .filter(ApplicationKeys.accountId, accountId)
                                                                           .fetch(findOptions))) {
      while (applicationsInAccount.hasNext()) {
        Application application = applicationsInAccount.next();
        hPersistence.delete(hPersistence.createQuery(entry).filter(APP_ID, application.getUuid()));
      }
    } catch (Exception e) {
      log.error("Issue while deleting app level documents for this collection {}", entry.getName(), e);
      return false;
    }
    return true;
  }

  public boolean deleteExportableAccountData(String accountId) {
    Set<Class<? extends PersistentEntity>> toBeExported = findExportableEntityTypes();
    log.info("The exportable entities are {}", toBeExported);

    toBeExported.forEach(entry -> {
      try {
        deleteAppLevelDocuments(accountId, entry);
        log.info(
            "Deleting account level documents from collection {} and count of account level records deleted are {}",
            entry.getName(), hPersistence.createQuery(entry).filter(ACCOUNT_ID, accountId).count());
        hPersistence.delete(hPersistence.createQuery(entry).filter(ACCOUNT_ID, accountId));
      } catch (Exception e) {
        log.error("Issue while deleting account level documents this collection {}", entry.getName(), e);
      }
    });
    List<User> users = userService.getUsersOfAccount(accountId);
    if (!users.isEmpty()) {
      if (featureFlagService.isEnabled(FeatureName.PL_USER_DELETION_V2, accountId)) {
        users.forEach(user -> userService.forceDelete(accountId, user.getUuid()));
      } else {
        users.forEach(user -> userService.delete(accountId, user.getUuid()));
      }
    }
    ssoSettingService.deleteByAccountId(accountId);
    return hPersistence.delete(Account.class, accountId);
  }

  private Set<Class<? extends PersistentEntity>> findExportableEntityTypes() {
    Set<Class<? extends PersistentEntity>> toBeExported = new HashSet<>();

    morphia.getMapper().getMappedClasses().forEach(mc -> {
      Class<?> clazz = mc.getClazz();
      if (PersistentEntity.class.isAssignableFrom(mc.getClazz()) && mc.getEntityAnnotation() != null
          && isAnnotatedExportable(clazz) && !separateDeletionEntities.contains(clazz)) {
        // Find out non-abstract classes with both 'Entity' and 'HarnessEntity' annotation.
        log.info("Collection '{}' is exportable", clazz.getName());
        toBeExported.add(clazz.asSubclass(PersistentEntity.class));
      }
    });
    return toBeExported;
  }

  private boolean isAnnotatedExportable(Class<?> clazz) {
    HarnessEntity harnessEntity = clazz.getAnnotation(HarnessEntity.class);
    return harnessEntity != null && harnessEntity.exportable();
  }

  @VisibleForTesting
  String getCollectionName(Class<?> clazz) {
    return morphia.getMapper().getCollectionName(clazz);
  }

  /** With any change of deletion logic CURRENT_DELETION_ALGO_NUM value should be incremented **/
  public boolean deleteAccount(String accountId, boolean deleteAccountFromAccountsCollection) {
    log.info("Deleting data for account {}. Deletion algo version: {}", accountId, CURRENT_DELETION_ALGO_NUM);
    deleteQuartzJobsForAccount(accountId);
    deletePerpetualTasksForAccount(accountId);
    delegateService.deleteByAccountId(accountId);
    List<String> entitiesRemainingForDeletion = deleteAllEntities(accountId);
    delegateNgTokenService.deleteByAccountId(accountId);
    churnedAuditFilesAndChunksCleanup.deleteAuditFilesAndChunks(accountId);
    churnedConfigFilesAndChunksCleanup.deleteConfigFilesAndChunks(accountId);
    timescaleDataCleanup.cleanupChurnedAccountData(accountId);
    if (isEmpty(entitiesRemainingForDeletion)) {
      if (deleteAccountFromAccountsCollection) {
        deleteAccountFromAccountsCollection(accountId);
      }
    } else {
      log.info("Entities Remaining For Deletion for accountID: " + accountId
          + "are: " + entitiesRemainingForDeletion.toString());
    }
    return true;
  }

  public void deleteAccountFromAccountsCollection(String accountId) {
    hPersistence.delete(Account.class, accountId);
    upsertDeletedEntity(accountId, CURRENT_DELETION_ALGO_NUM);
  }

  public void handleDeletedAccount(DeletedEntity deletedAccount) {
    if (CURRENT_DELETION_ALGO_NUM > deletedAccount.getDeletionAlgoNum()) {
      deleteAccount(deletedAccount.getEntityId(), true);
    }
  }

  public void upsertDeletedEntity(String accountId, int deletionAlgoNum) {
    Query<DeletedEntity> query =
        hPersistence.createQuery(DeletedEntity.class).filter(DeletedEntityKeys.entityId, accountId);
    UpdateOperations<DeletedEntity> updateOperations = hPersistence.createUpdateOperations(DeletedEntity.class)
                                                           .set(DeletedEntityKeys.entityId, accountId)
                                                           .set(DeletedEntityKeys.entityType, DeletedEntityType.ACCOUNT)
                                                           .set(DeletedEntityKeys.deletionAlgoNum, deletionAlgoNum);
    hPersistence.upsert(query, updateOperations);
  }

  private void deleteQuartzJobsForAccount(String accountId) {
    try {
      log.info("Deleting all Quartz Jobs for account {}", accountId);
      persistentScheduler.deleteAllQuartzJobsForAccount(accountId);
      log.info("Deleted all Quartz Jobs for account {}", accountId);
    } catch (SchedulerException e) {
      log.error("Exception occurred at deleteQuartzJobsForAccount() for account {}", accountId, e);
    }
  }

  private void deletePerpetualTasksForAccount(String accountId) {
    perpetualTaskService.deleteAllTasksForAccount(accountId);
    log.info("Deleted all Perpetual Tasks for account {}", accountId);
  }

  @VisibleForTesting
  void removeAccountFromUsageBucketsCollection(String accountId) {
    hPersistence.delete(
        hPersistence.createQuery(UsageBucket.class, excludeAuthority).field(UsageBucketKeys.key).contains(accountId));
  }

  @VisibleForTesting
  void removeAccountFromSegmentGroupEventContextCollection(String accountId) {
    try (HIterator<SegmentGroupEventJobContext> iterator =
             new HIterator<>(hPersistence.createQuery(SegmentGroupEventJobContext.class, excludeAuthority)
                                 .filter(SegmentGroupEventJobContextKeys.accountIds, accountId)
                                 .fetch())) {
      while (iterator.hasNext()) {
        SegmentGroupEventJobContext eventJobContext = iterator.next();
        eventJobContext.getAccountIds().remove(accountId);
        if (eventJobContext.getAccountIds().isEmpty()) {
          hPersistence.delete(eventJobContext);
        } else {
          hPersistence.save(eventJobContext);
        }
      }
    }
  }
}
