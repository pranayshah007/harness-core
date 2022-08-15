/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.notification.bean.NotificationRules;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class NotificationInstrumentationHelper {
  @Inject NotificationHelper notificationHelper;

  public List<NotificationRules> getNotificationRules(String planExecutionId, Ambiance ambiance) {
    String yaml = notificationHelper.obtainYaml(planExecutionId);
    List<NotificationRules> notificationRules = new ArrayList<>();
    if (EmptyPredicate.isEmpty(yaml)) {
      log.error("Empty yaml found in executionMetaData");
      return notificationRules;
    }
    try {
      notificationRules = notificationHelper.getNotificationRulesFromYaml(yaml, ambiance);
    } catch (IOException exception) {
      log.error("Unable to parse yaml to get notification objects", exception);
    }
    return notificationRules != null ? notificationRules : Collections.emptyList();
  }

  public Set<String> getNotificationMethodTypes(List<NotificationRules> notificationRules) {
    return notificationRules.stream()
        .map(o -> o.getNotificationChannelWrapper().getValue().getType())
        .collect(Collectors.toSet());
  }
}
