/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLNumberFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentTagFilter;
import software.wings.graphql.schema.type.aggregation.environment.QLEnvironmentTypeFilter;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@OwnedBy(CDC)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLExecutionFilter extends QLBaseExecutionFilter {
  QLIdFilter pipelineExecutionId;

  @Builder
  public QLExecutionFilter(QLIdFilter execution, QLIdFilter application, QLIdFilter service, QLIdFilter cloudProvider,
      QLIdFilter environment, QLEnvironmentTypeFilter environmentType, QLIdFilter status, QLTimeFilter endTime,
      QLTimeFilter startTime, QLNumberFilter duration, QLIdFilter triggeredBy, QLIdFilter trigger, QLIdFilter workflow,
      QLIdFilter pipeline, QLTimeFilter creationTime, QLDeploymentTagFilter tag, QLIdFilter pipelineExecutionId,
      String applicationId) {
    super(execution, application, service, cloudProvider, environment, environmentType, status, endTime, startTime,
        duration, triggeredBy, trigger, workflow, pipeline, creationTime, tag, applicationId);
    this.pipelineExecutionId = pipelineExecutionId;
  }
}
