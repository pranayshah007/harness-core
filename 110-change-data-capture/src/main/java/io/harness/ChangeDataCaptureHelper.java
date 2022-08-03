/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeSubscriber;
import io.harness.changestreamsframework.ChangeTracker;
import io.harness.changestreamsframework.ChangeTrackingInfo;
import io.harness.entities.CDCEntity;
import io.harness.persistence.PersistentEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CE)
class ChangeDataCaptureHelper {
  @Inject private ChangeTracker changeTracker;
  @Inject private Set<CDCEntity<?>> cdcEntities;

  void startChangeListeners(ChangeSubscriber changeSubscriber) {
    Set<ChangeTrackingInfo<?>> changeTrackingInfos = new HashSet<>();
    Set<Class<? extends PersistentEntity>> subscribedClasses = new HashSet<>();
    cdcEntities.forEach(cdcEntity -> subscribedClasses.add(cdcEntity.getSubscriptionEntity()));

    for (Class<? extends PersistentEntity> subscribedClass : subscribedClasses) {
      ChangeTrackingInfo<?> changeTrackingInfo = getChangeTrackingInfo(subscribedClass, changeSubscriber);
      changeTrackingInfos.add(changeTrackingInfo);
    }

    log.info("Calling change tracker to start change listeners");
    changeTracker.start(changeTrackingInfos);
  }

  private <T extends PersistentEntity> ChangeTrackingInfo<T> getChangeTrackingInfo(
      Class<T> subscribedClass, ChangeSubscriber<T> changeSubscriber) {
    return new ChangeTrackingInfo<>(subscribedClass, changeSubscriber);
  }

  boolean checkIfAnyChangeListenerIsAlive() {
    return changeTracker.checkIfAnyChangeTrackerIsAlive();
  }

  void stopChangeListeners() {
    log.info("Stopping change listeners");
    changeTracker.stop();
  }
}
