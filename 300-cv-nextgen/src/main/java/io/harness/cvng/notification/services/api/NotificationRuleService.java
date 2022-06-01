/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.api;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleRef;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.ng.beans.PageResponse;

import java.util.List;

public interface NotificationRuleService {
  NotificationRuleResponse create(ProjectParams projectParams, NotificationRuleDTO notificationRuleDTO);
  List<NotificationRule> getEntities(ProjectParams projectParams, List<String> identifiers);
  NotificationRule getEntity(ProjectParams projectParams, String identifier);
  NotificationRuleResponse update(
      ProjectParams projectParams, String identifier, NotificationRuleDTO notificationRuleDTO);
  Boolean delete(ProjectParams projectParams, String identifier);
  void delete(ProjectParams projectParams, List<String> identifiers);
  PageResponse<NotificationRuleResponse> get(ProjectParams projectParams, Integer pageNumber, Integer pageSize);
  List<NotificationRuleRef> getNotificationRuleRefs(List<NotificationRuleRefDTO> notificationRuleRefDTOS);
  List<NotificationRuleRefDTO> getNotificationRuleRefDTOs(List<NotificationRuleRef> notificationRuleRefs);
  List<NotificationRuleResponse> getNotificationRuleResponse(
      ProjectParams projectParams, List<NotificationRuleRef> notificationRuleRefList);
}
