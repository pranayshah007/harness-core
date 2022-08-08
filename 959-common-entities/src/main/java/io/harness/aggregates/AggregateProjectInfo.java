/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregates;

import io.harness.timescaledb.tables.pojos.PipelineExecutionSummaryCd;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class AggregateProjectInfo extends PipelineExecutionSummaryCd {
  private final long count;

  public AggregateProjectInfo(String orgIdentifier, String projectId, long count) {
    this.count = count;
    this.setOrgidentifier(orgIdentifier);
    this.setProjectidentifier(projectId);
  }

  public AggregateProjectInfo(String orgIdentifier, String projectId, String status, long count) {
    this.count = count;
    this.setOrgidentifier(orgIdentifier);
    this.setProjectidentifier(projectId);
    this.setStatus(status);
  }
}
