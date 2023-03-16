/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.mongo.changestreams.ChangeEvent;
import io.harness.mongo.changestreams.ChangeSubscriber;

import com.google.inject.Inject;
import java.util.Queue;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * This job performs realtime sync of the changes
 * to elasticsearch for the specified classes.
 *
 * @author utkarsh
 */

@OwnedBy(PL)
@Slf4j
public class ElasticsearchRealtimeSyncTask {
  @Inject private ElasticsearchSyncHelper elasticsearchSyncHelper;
  @Inject private ChangeEventProcessor changeEventProcessor;
  @Inject private FeatureFlagService featureFlagService;

  private void processChanges(Queue<ChangeEvent<?>> changeEvents) {
    while (!changeEvents.isEmpty()) {
      ChangeEvent<?> changeEvent = changeEvents.poll();
      changeEventProcessor.processChangeEvent(changeEvent);
    }
  }

  private ChangeSubscriber<?> getChangeSubscriber() {
    return changeEvent -> {
      boolean isRunningSuccessfully = changeEventProcessor.processChangeEvent(changeEvent);
      if (!isRunningSuccessfully) {
        stop();
      }
    };
  }

  public boolean run(Queue<ChangeEvent<?>> pendingChangeEvents) throws InterruptedException {
    log.info("Initializing change listeners for search entities");
    processChanges(pendingChangeEvents);
    elasticsearchSyncHelper.startChangeListeners(getChangeSubscriber());
    Set<String> accountIdsToSyncToTimescale = featureFlagService.getAccountIds(FeatureName.TIME_SCALE_CG_SYNC);
    changeEventProcessor.startProcessingChangeEvents(accountIdsToSyncToTimescale);
    boolean isAlive = true;
    while (!Thread.currentThread().isInterrupted() && isAlive) {
      Thread.sleep(2000);
      isAlive = elasticsearchSyncHelper.checkIfAnyChangeListenerIsAlive();
      isAlive = isAlive && changeEventProcessor.isAlive();
    }
    return false;
  }

  public void stop() {
    elasticsearchSyncHelper.stopChangeListeners();
    changeEventProcessor.shutdown();
  }
}
