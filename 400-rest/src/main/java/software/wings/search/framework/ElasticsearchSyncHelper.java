/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.changestreams.ChangeSubscriber;
import io.harness.mongo.changestreams.ChangeTracker;
import io.harness.mongo.changestreams.ChangeTrackingInfo;
import io.harness.persistence.PersistentEntity;

import software.wings.dl.WingsPersistence;
import software.wings.search.entities.deployment.DeploymentSearchEntity;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract template class for both
 * realtime and bulk synchronisation tasks.
 *
 * @author utkarsh
 */

@OwnedBy(PL)
@Slf4j
class ElasticsearchSyncHelper {
  @Inject private Set<SearchEntity<?>> searchEntities;
  @Inject private Set<TimeScaleEntity<?>> timeScaleEntities;
  @Inject private WingsPersistence wingsPersistence;
  @Inject @Named("Search") private ChangeTracker changeTracker;

  void startChangeListeners(ChangeSubscriber changeSubscriber) {
    Set<Class<? extends PersistentEntity>> subscribedClasses = new HashSet<>();
    searchEntities.forEach(searchEntity -> subscribedClasses.addAll(searchEntity.getSubscriptionEntities()));
    timeScaleEntities.forEach(timeScaleEntity -> subscribedClasses.add(timeScaleEntity.getSourceEntityClass()));

    subscribedClasses.remove(DeploymentSearchEntity.SOURCE_ENTITY_CLASS);

    Set<ChangeTrackingInfo<?>> changeTrackingInfos = new HashSet<>();

    for (Class<? extends PersistentEntity> subscribedClass : subscribedClasses) {
      ChangeTrackingInfo<?> changeTrackingInfo = getChangeTrackingInfo(subscribedClass, changeSubscriber);
      changeTrackingInfos.add(changeTrackingInfo);
    }

    log.info("Calling change tracker to start change listeners");
    changeTracker.start(changeTrackingInfos);
  }

  private <T extends PersistentEntity> ChangeTrackingInfo<T> getChangeTrackingInfo(
      Class<T> subscribedClass, ChangeSubscriber<T> changeSubscriber) {
    SearchSourceEntitySyncState searchSourceEntitySyncState =
        wingsPersistence.get(SearchSourceEntitySyncState.class, subscribedClass.getCanonicalName());
    String token = null;
    if (searchSourceEntitySyncState != null) {
      token = searchSourceEntitySyncState.getLastSyncedToken();
    }
    return new ChangeTrackingInfo<>(subscribedClass, changeSubscriber, token, null);
  }

  boolean checkIfAnyChangeListenerIsAlive() {
    return changeTracker.checkIfAnyChangeTrackerIsAlive();
  }

  void stopChangeListeners() {
    log.info("Stopping change listeners");
    changeTracker.stop();
  }
}
