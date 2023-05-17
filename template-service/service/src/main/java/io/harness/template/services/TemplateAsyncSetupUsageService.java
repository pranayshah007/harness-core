/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import io.harness.template.async.beans.Action;
import io.harness.template.async.beans.SetupUsageEventStatus;
import io.harness.template.async.beans.TemplateSetupUsageEvent;
import io.harness.template.entity.TemplateEntity;

public interface TemplateAsyncSetupUsageService {
  TemplateSetupUsageEvent startEvent(TemplateEntity entity, String branch, Action action);

  TemplateSetupUsageEvent updateEvent(String uuid, SetupUsageEventStatus status);
}
