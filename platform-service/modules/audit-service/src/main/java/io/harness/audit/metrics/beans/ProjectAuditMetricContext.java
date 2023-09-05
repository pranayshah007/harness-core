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
  public ProjectAuditMetricContext(String projectId, String orgId, String accountId, String identifier) {
    put("projectId", projectId);
    put("orgId", orgId);
    put("accountId", accountId);
    put("identifier", identifier);
  }
}
