/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import static io.harness.eventsframework.EventsFrameworkConstants.CI_ORCHESTRATION_NOTIFY_EVENT;

import io.harness.authorization.AuthorizationServiceHeader;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CIManagerConfigurationOverride {
  private AuthorizationServiceHeader serviceHeader = AuthorizationServiceHeader.CI_MANAGER;
  private String modulePrefix = "ci";

  private boolean usePrimaryVersionController = true;

  private boolean useBuildEnforcer = true;

  private String mongoUri = "";

  private String orchestrationEvent = CI_ORCHESTRATION_NOTIFY_EVENT;
}
