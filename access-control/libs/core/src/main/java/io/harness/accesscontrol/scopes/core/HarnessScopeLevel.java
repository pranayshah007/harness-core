/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.scopes.harness;

import static io.harness.accesscontrol.scopes.harness.HarnessScopeParams.ACCOUNT_LEVEL_PARAM_NAME;
import static io.harness.accesscontrol.scopes.harness.HarnessScopeParams.ORG_LEVEL_PARAM_NAME;
import static io.harness.accesscontrol.scopes.harness.HarnessScopeParams.PROJECT_LEVEL_PARAM_NAME;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;

import io.harness.accesscontrol.scopes.core.ScopeLevel;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Getter;

@OwnedBy(HarnessTeam.PL)
@Getter
public enum HarnessScopeLevel implements ScopeLevel {
  ACCOUNT("account", 0, ACCOUNT_LEVEL_PARAM_NAME, ACCOUNT_ENTITY, "ACCOUNT"),
  ORGANIZATION("organization", 1, ORG_LEVEL_PARAM_NAME, ORGANIZATION_ENTITY, "ORGANIZATION"),
  PROJECT("project", 2, PROJECT_LEVEL_PARAM_NAME, PROJECT_ENTITY, "PROJECT");

  private final String name;
  private final int rank;
  private final String paramName;
  private final String eventEntityName;
  private final String resourceType;

  HarnessScopeLevel(String name, int rank, String paramName, String eventEntityName, String resourceType) {
    this.name = name;
    this.rank = rank;
    this.paramName = paramName;
    this.eventEntityName = eventEntityName;
    this.resourceType = resourceType;
  }

  @Override
  public String toString() {
    return name;
  }
}
