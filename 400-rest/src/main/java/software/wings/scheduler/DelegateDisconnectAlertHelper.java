/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.obfuscate.Obfuscator.obfuscate;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.alert.AlertData;
import io.harness.delegate.beans.Delegate;
import io.harness.service.intfc.DelegateCache;

import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.DelegatesDownAlert;
import software.wings.service.impl.DelegateDao;
import software.wings.service.impl.DelegateObserver;
import software.wings.service.intfc.AlertService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class DelegateDisconnectAlertHelper implements DelegateObserver {
  @Inject private AlertService alertService;
  @Inject DelegateCache delegateCache;
  @Inject private DelegateDao delegateDao;

  public void checkIfAnyDelegatesAreDown(String accountId, List<Delegate> delegates) {
    for (Delegate delegate : delegates) {
      // for cg and ecs delegates
      if (isNotEmpty(delegate.getDelegateGroupName())) {
        continue;
      }
      AlertData alertData = DelegatesDownAlert.builder()
                                .accountId(accountId)
                                .hostName(delegate.getHostName())
                                .obfuscatedIpAddress(obfuscate(delegate.getIp()))
                                .build();
      if (delegateDao.isDelegateHeartBeatUpToDate(delegate)) {
        alertService.closeAlert(accountId, GLOBAL_APP_ID, AlertType.DelegatesDown, alertData);
      } else {
        alertService.openAlert(accountId, GLOBAL_APP_ID, AlertType.DelegatesDown, alertData);
      }
    }
    // this is currently for ecs delegates only
    processDelegateWhichBelongsToGroup(accountId, delegates);
  }

  @VisibleForTesting
  protected void processDelegateWhichBelongsToGroup(String accountId, List<Delegate> delegates) {
    // for delegates that have grouping concept, dont send an alert unless all the delegates of a group are down
    Set<String> connectedScalingGroups = new HashSet<>();
    Set<String> allScalingGroups = new HashSet<>();
    for (Delegate delegate : delegates) {
      if (delegate.isNg() || isEmpty(delegate.getDelegateGroupName())) {
        continue;
      }
      allScalingGroups.add(delegate.getDelegateGroupName());
      if (delegateDao.isDelegateHeartBeatUpToDate(delegate)) {
        connectedScalingGroups.add(delegate.getDelegateGroupName());
      }
    }
    allScalingGroups.removeAll(connectedScalingGroups);
    for (String disconnectedScalingGroup : allScalingGroups) {
      AlertData alertData =
          DelegatesDownAlert.builder().accountId(accountId).delegateGroupName(disconnectedScalingGroup).build();
      alertService.openAlert(accountId, GLOBAL_APP_ID, AlertType.DelegatesDown, alertData);
    }
    for (String connectedScalingGroup : connectedScalingGroups) {
      AlertData alertData =
          DelegatesDownAlert.builder().accountId(accountId).delegateGroupName(connectedScalingGroup).build();
      alertService.closeAlert(accountId, GLOBAL_APP_ID, AlertType.DelegatesDown, alertData);
    }
  }

  @Override
  public void onAdded(Delegate delegate) {
    // not implemented
  }

  @Override
  public void onDisconnected(String accountId, String delegateId) {
    Delegate delegate = delegateCache.get(accountId, delegateId, true);
    if (delegate == null) {
      return;
    }
    checkIfAnyDelegatesAreDown(accountId, Collections.singletonList(delegate));
  }

  @Override
  public void onReconnected(Delegate delegate) {
    // not implemented
  }

  @Override
  public void onDelegateTagsUpdated(String accountId) {
    // not implemented
  }
}
