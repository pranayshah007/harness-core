/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.references;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.logging.AutoLogContext;

import software.wings.beans.EntityType;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineSetupUsageLogContext extends AutoLogContext {
  public static final String ENTITY_TYPE = "entityType";
  public static final String ENTITY_ID = "entityId";
  public static final String ACCOUNT_ID = "accountId";
  public static final String ORG_ID = "orgId";
  public static final String PROJECT_ID = "projectId";
  public static final String USE_CASE = "useCase";

  public PipelineSetupUsageLogContext(
      Scope scope, String entityId, AutoLogContext.OverrideBehavior behavior, String useCase) {
    super(NullSafeImmutableMap.<String, String>builder()
              .put(ENTITY_TYPE, EntityType.PIPELINE.name())
              .putIfNotNull(ENTITY_ID, entityId)
              .putIfNotNull(ACCOUNT_ID, scope.getAccountIdentifier())
              .putIfNotNull(ORG_ID, scope.getOrgIdentifier())
              .putIfNotNull(PROJECT_ID, scope.getProjectIdentifier())
              .putIfNotNull(USE_CASE, useCase)
              .build(),
        behavior);
  }
}
