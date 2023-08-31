/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.metrics.beans;

import io.harness.metrics.AutoMetricContext;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
public class ProjectAuditMetricContext extends AutoMetricContext {
  public static final String ACCOUNT_ID = "accountId";
  public static final String ORG_ID = "orgId";
  public static final String PROJECT_ID = "projectId";
  public static final String IDENTIFIER = "identifier";
  public ProjectAuditMetricContext(String accountId, String orgId, String projectId, String identifier) {
    put(ACCOUNT_ID, accountId);
    put(ORG_ID, orgId);
    put(PROJECT_ID, projectId);
    put(IDENTIFIER, identifier);
  }
}
