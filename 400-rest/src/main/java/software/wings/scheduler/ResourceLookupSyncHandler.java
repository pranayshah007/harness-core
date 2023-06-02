/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;

import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpAndRedisModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.logging.AccountLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.persistence.HIterator;
import io.harness.workers.background.AccountLevelEntityProcessController;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.HarnessTagLink.HarnessTagLinkKeys;
import software.wings.beans.NameValuePair;
import software.wings.beans.ResourceLookup;
import software.wings.beans.ResourceLookup.ResourceLookupKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.ResourceLookupService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResourceLookupSyncHandler extends IteratorPumpAndRedisModeHandler implements Handler<Account> {
  private static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofMinutes(120);
  private static final Duration ACCEPTABLE_EXECUTION_TIME = ofMinutes(5);

  @Inject private ResourceLookupService resourceLookupService;
  @Inject private AccountService accountService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private MorphiaPersistenceProvider<Account> persistenceProvider;
  @Inject private HarnessTagService harnessTagService;

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<Account, MorphiaFilterExpander<Account>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       ResourceLookupSyncHandler.class,
                       MongoPersistenceIterator.<Account, MorphiaFilterExpander<Account>>builder()
                           .clazz(Account.class)
                           .fieldName(AccountKeys.resourceLookupSyncIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
                           .handler(this)
                           .entityProcessController(new AccountLevelEntityProcessController(accountService))
                           .schedulingType(REGULAR)
                           .persistenceProvider(persistenceProvider)
                           .redistribute(true));
  }

  @Override
  protected void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<Account, MorphiaFilterExpander<Account>>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       ResourceLookupSyncHandler.class,
                       MongoPersistenceIterator.<Account, MorphiaFilterExpander<Account>>builder()
                           .clazz(Account.class)
                           .fieldName(AccountKeys.resourceLookupSyncIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
                           .handler(this)
                           .entityProcessController(new AccountLevelEntityProcessController(accountService))
                           .persistenceProvider(persistenceProvider));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "ResourceLookupTagLinkSync";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(Account account) {
    try (AccountLogContext ignore1 = new AccountLogContext(account.getUuid(), OVERRIDE_ERROR)) {
      long startTime = System.nanoTime();
      syncTagLinkResourceLookup(account);
      long endTime = System.nanoTime();
      log.info("ResourceLookupSyncHandler: Resource Lookup sync for accountId {} took {} nanoseconds",
          account.getUuid(), endTime - startTime);
    }
  }

  @VisibleForTesting
  public void syncTagLinkResourceLookup(Account account) {
    String accountId = account.getUuid();

    // Set of resource Ids
    Set<String> tagLinkResourceIds = new HashSet<>();
    // List of tags attached to a resource
    Map<String, List<NameValuePair>> entityIdTagsMap = new HashMap<>();
    // appIds for a resource
    Map<String, String> entityIdAppIdMap = new HashMap<>();
    // entityType for a resource
    Map<String, String> entityIdEntityTypeMap = new HashMap<>();

    try (HIterator<HarnessTagLink> tagLinksHIterator =
             new HIterator<>(wingsPersistence.createQuery(HarnessTagLink.class)
                                 .filter(HarnessTagLinkKeys.accountId, accountId)
                                 .limit(NO_LIMIT)
                                 .fetch())) {
      while (tagLinksHIterator.hasNext()) {
        HarnessTagLink tagLink = tagLinksHIterator.next();
        tagLinkResourceIds.add(tagLink.getEntityId());
        entityIdEntityTypeMap.put(tagLink.getEntityId(), tagLink.getEntityType().name());
        entityIdAppIdMap.put(tagLink.getEntityId(), tagLink.getAppId());

        if (entityIdTagsMap.get(tagLink.getEntityId()) == null) {
          List<NameValuePair> tags = new ArrayList<>();
          tags.add(NameValuePair.builder().name(tagLink.getKey()).value(tagLink.getValue()).build());
          entityIdTagsMap.put(tagLink.getEntityId(), tags);
        } else {
          entityIdTagsMap.get(tagLink.getEntityId())
              .add(NameValuePair.builder().name(tagLink.getKey()).value(tagLink.getValue()).build());
        }
      }
    }

    Map<String, ResourceLookup> resourceLookupMap =
        resourceLookupService.getResourceLookupMapWithResourceIds(accountId, tagLinkResourceIds);

    entityIdTagsMap.forEach((resourceId, tagsFromTagLinks) -> {
      ResourceLookup resourceLookup = resourceLookupMap.get(resourceId);

      if (resourceLookup != null) {
        if (resourceLookup.getTags() == null || resourceLookup.getTags().size() != tagsFromTagLinks.size()) {
          // update the whole tags list
          updateTagsList(accountId, resourceId, tagsFromTagLinks);
        } else {
          // compare each entry
          Map<String, NameValuePair> tagsNameMap =
              resourceLookup.getTags().stream().collect(Collectors.toMap(NameValuePair::getName, tagEntry -> tagEntry));

          for (NameValuePair tag : tagsFromTagLinks) {
            // tag does not exist or value not equal
            if (tagsNameMap.get(tag.getName()) == null
                || (!tagsNameMap.get(tag.getName()).getValue().equals(tag.getValue()))) {
              resourceLookupService.updateResourceLookupRecordWithTags(
                  accountId, resourceId, tag.getName(), tag.getValue(), true);
            }
          }
        }
      } else {
        ResourceLookup newResourceLookup =
            resourceLookupService.create(ResourceLookup.builder()
                                             .accountId(accountId)
                                             .appId(entityIdAppIdMap.get(resourceId))
                                             .resourceId(resourceId)
                                             .resourceType(entityIdEntityTypeMap.get(resourceId))
                                             .tags(tagsFromTagLinks)
                                             .build());

        log.info("ResourceLookupSyncHandler: Created new Resource Lookup with id: {}", newResourceLookup.getUuid());
      }
    });
  }

  private void updateTagsList(String accountId, String resourceId, List<NameValuePair> tagsFromTagLinks) {
    Query<ResourceLookup> query = wingsPersistence.createQuery(ResourceLookup.class)
                                      .filter(ResourceLookupKeys.accountId, accountId)
                                      .filter(ResourceLookupKeys.resourceId, resourceId)
                                      .disableValidation();

    UpdateOperations<ResourceLookup> updateOperations =
        wingsPersistence.createUpdateOperations(ResourceLookup.class).set(ResourceLookupKeys.tags, tagsFromTagLinks);

    wingsPersistence.findAndModify(query, updateOperations, WingsPersistence.returnNewOptions);
  }
}
