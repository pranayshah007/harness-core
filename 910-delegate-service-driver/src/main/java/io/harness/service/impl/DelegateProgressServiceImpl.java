/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.DelegateTaskProgressResponse.DelegateTaskProgressResponseKeys;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.lang.System.currentTimeMillis;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.persistence.HPersistence;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateProgressService;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ProgressData;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateProgressServiceImpl implements DelegateProgressService {
  @Inject private HPersistence persistence;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject @Named("disableDeserialization") private boolean disableDeserialization;
  private static final int DELETE_TRESHOLD = 1000;
  private static final long MAX_PROCESSING_DURATION_MILLIS = 60000L;

  @Override
  public void run() {
    Set<String> responsesToBeDeleted = new HashSet<>();

    while (true) {
      try {
        Query<DelegateTaskProgressResponse> taskResponseQuery =
            persistence.createQuery(DelegateTaskProgressResponse.class, excludeAuthority)
                .field(DelegateTaskProgressResponseKeys.processAfter)
                .lessThan(currentTimeMillis() - MAX_PROCESSING_DURATION_MILLIS);

        UpdateOperations<DelegateTaskProgressResponse> updateOperations =
            persistence.createUpdateOperations(DelegateTaskProgressResponse.class)
                .set(DelegateTaskProgressResponseKeys.processAfter, currentTimeMillis());

        DelegateTaskProgressResponse lockedTaskProgressResponse =
            persistence.findAndModify(taskResponseQuery, updateOperations, HPersistence.returnNewOptions);

        if (lockedTaskProgressResponse == null) {
          break;
        }

        log.info("Process won the task progress response {}.", lockedTaskProgressResponse.getUuid());
        ProgressData data = disableDeserialization
            ? BinaryResponseData.builder().data(lockedTaskProgressResponse.getProgressData()).build()
            : (ProgressData) kryoSerializer.asInflatedObject(lockedTaskProgressResponse.getProgressData());

        waitNotifyEngine.progressOn(lockedTaskProgressResponse.getCorrelationId(), data);

        responsesToBeDeleted.add(lockedTaskProgressResponse.getUuid());
        if (responsesToBeDeleted.size() >= DELETE_TRESHOLD) {
          deleteProcessedResponses(responsesToBeDeleted);
        }
      } catch (Exception e) {
        log.error("Exception occurred why processing the progress response", e);
      }
    }

    deleteProcessedResponses(responsesToBeDeleted);
  }

  private boolean deleteProcessedResponses(Set<String> responsesToBeDeleted) {
    if (isEmpty(responsesToBeDeleted)) {
      return true;
    }

    boolean deleteSuccessful =
        persistence.deleteOnServer(persistence.createQuery(DelegateTaskProgressResponse.class, excludeAuthority)
                                       .field(DelegateTaskProgressResponseKeys.uuid)
                                       .in(responsesToBeDeleted));

    if (deleteSuccessful) {
      responsesToBeDeleted.clear();
    }

    return deleteSuccessful;
  }
}
