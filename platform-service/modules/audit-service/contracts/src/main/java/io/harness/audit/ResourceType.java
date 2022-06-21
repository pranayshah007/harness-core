/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PL)
public enum ResourceType {
  ORGANIZATION(ResourceTypeConstants.ORGANIZATION),
  PROJECT(ResourceTypeConstants.PROJECT),
  USER_GROUP(ResourceTypeConstants.USER_GROUP),
  SECRET(ResourceTypeConstants.SECRET),
  RESOURCE_GROUP(ResourceTypeConstants.RESOURCE_GROUP),
  USER(ResourceTypeConstants.USER),
  ROLE(ResourceTypeConstants.ROLE),
  ROLE_ASSIGNMENT(ResourceTypeConstants.ROLE_ASSIGNMENT),
  PIPELINE(ResourceTypeConstants.PIPELINE),
  TRIGGER(ResourceTypeConstants.TRIGGER),
  TEMPLATE(ResourceTypeConstants.TEMPLATE),
  INPUT_SET(ResourceTypeConstants.INPUT_SET),
  DELEGATE_CONFIGURATION(ResourceTypeConstants.DELEGATE_CONFIGURATION),
  DELEGATE_GROUPS(ResourceTypeConstants.DELEGATE_GROUPS),
  SERVICE(ResourceTypeConstants.SERVICE),
  ENVIRONMENT(ResourceTypeConstants.ENVIRONMENT),
  DELEGATE(ResourceTypeConstants.DELEGATE),
  SERVICE_ACCOUNT(ResourceTypeConstants.SERVICE_ACCOUNT),
  CONNECTOR(ResourceTypeConstants.CONNECTOR),
  API_KEY(ResourceTypeConstants.API_KEY),
  TOKEN(ResourceTypeConstants.TOKEN),
  DELEGATE_TOKEN(ResourceTypeConstants.DELEGATE_TOKEN),
  GOVERNANCE_POLICY(ResourceTypeConstants.GOVERNANCE_POLICY),
  GOVERNANCE_POLICY_SET(ResourceTypeConstants.GOVERNANCE_POLICY_SET),
  VARIABLE(ResourceTypeConstants.VARIABLE),
  CHAOS_HUB(ResourceTypeConstants.CHAOS_HUB),
  MONITORED_SERVICE(ResourceTypeConstants.MONITORED_SERVICE),
  CHAOS_AGENT(ResourceTypeConstants.CHAOS_AGENT),
  CHAOS_WORKFLOW(ResourceTypeConstants.CHAOS_WORKFLOW);

  ResourceType(String resourceType) {
    if (!this.name().equals(resourceType)) {
      throw new IllegalArgumentException(
          String.format("ResourceType enum: %s doesn't match constant: %s", this.name(), resourceType));
    }
  }
}
