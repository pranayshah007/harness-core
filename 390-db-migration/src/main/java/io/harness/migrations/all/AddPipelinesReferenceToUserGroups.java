/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.mongo.MongoUtils.setUnset;

import io.harness.migrations.Migration;
import io.harness.mongo.MongoPersistence;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.beans.entity.UserGroupEntityReference;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddPipelinesReferenceToUserGroups implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Inject private UserGroupService userGroupService;

  @Inject private PipelineService pipelineService;

  @Inject private MongoPersistence mongoPersistence;

  private static class pipelineMetadata {
    String pipelineId;
    String appId;
    pipelineMetadata(String pipelineId, String appId) {
      this.pipelineId = pipelineId;
      this.appId = appId;
    }
  }

  @Override
  public void migrate() {
    log.info("Starting to add pipelines reference in userGroups.");
    List<Account> allAccounts = new ArrayList<>();
    try (HIterator<Account> accounts = new HIterator<>(wingsPersistence.createQuery(Account.class).fetch())) {
      while (accounts.hasNext()) {
        allAccounts.add(accounts.next());
      }
    } catch (Exception ex) {
      log.error("Exception while fetching Accounts");
    }
    for (Account account : allAccounts) {
      log.info("Starting to add pipeline reference for account {}.", account.getAccountName());
      migratePipelinesForAccount(account);
    }
  }

  private void migratePipelinesForAccount(Account account) {
    Map<String, List<pipelineMetadata>> metadataMap = new HashMap<>();
    try (HIterator<Pipeline> pipelines = new HIterator<>(
             wingsPersistence.createQuery(Pipeline.class).filter(PipelineKeys.accountId, account.getUuid()).fetch())) {
      while (pipelines.hasNext()) {
        Pipeline pipeline = pipelines.next();
        Set<String> existingUserGroups = pipelineService.getUserGroups(pipeline);
        for (String id : existingUserGroups) {
          if (!metadataMap.containsKey(id)) {
            metadataMap.put(id, new ArrayList<>());
          }
          metadataMap.get(id).add(new pipelineMetadata(pipeline.getUuid(), pipeline.getAppId()));
        }
      }
    } catch (Exception ex) {
      log.error("Exception while fetching pipelines for Account {} ", account.getUuid());
    }
    for (Map.Entry<String, List<pipelineMetadata>> entry : metadataMap.entrySet()) {
      UserGroup userGroup = Optional.ofNullable(mongoPersistence.get(UserGroup.class, entry.getKey())).orElse(null);
      if (userGroup == null) {
        // log statement to for userGroups which deleted but are being referenced in a pipeline
        log.error("UserGroup with id {} does not exist but some pipelines are referring them", entry.getKey());
        continue;
      }
      for (pipelineMetadata pipelineMetadata : entry.getValue()) {
        userGroup.addParent(UserGroupEntityReference.builder()
                                .entityType("PIPELINE")
                                .id(pipelineMetadata.pipelineId)
                                .appId(pipelineMetadata.appId)
                                .accountId(account.getUuid())
                                .build());
      }
      UpdateOperations<UserGroup> ops = mongoPersistence.createUpdateOperations(UserGroup.class);
      setUnset(ops, "parents", userGroup.getParents());
      mongoPersistence.update(userGroup, ops);
    }
  }
}
