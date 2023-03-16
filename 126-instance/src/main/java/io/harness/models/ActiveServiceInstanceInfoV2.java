/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.models;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
@FieldNameConstants(innerTypeName = "Keys")
public class ActiveServiceInstanceInfoV2 {
  private String serviceIdentifier;
  private String serviceName;
  private String envIdentifier;
  private String envName;
  private String infraIdentifier;
  private String infraName;
  private String clusterIdentifier;
  private String agentIdentifier;
  private String lastPipelineExecutionId;
  private String lastPipelineExecutionName;
  private Long lastDeployedAt;
  private String tag;
  private String displayName;
  private Integer count;

  public static ProjectionOperation getProjection() {
    return Aggregation.project()
        .andExpression("_id." + Keys.serviceIdentifier)
        .as(Keys.serviceIdentifier)
        .andExpression("_id." + Keys.serviceName)
        .as(Keys.serviceName)
        .andExpression("_id." + Keys.envIdentifier)
        .as(Keys.envIdentifier)
        .andExpression("_id." + Keys.envName)
        .as(Keys.envName)
        .andExpression("_id." + Keys.infraIdentifier)
        .as(Keys.infraIdentifier)
        .andExpression("_id." + Keys.infraName)
        .as(Keys.infraName)
        .andExpression("_id." + Keys.clusterIdentifier)
        .as(Keys.clusterIdentifier)
        .andExpression("_id." + Keys.agentIdentifier)
        .as(Keys.agentIdentifier)
        .andExpression("_id." + Keys.lastPipelineExecutionId)
        .as(Keys.lastPipelineExecutionId)
        .andExpression("_id." + Keys.lastPipelineExecutionName)
        .as(Keys.lastPipelineExecutionName)
        .andExpression("_id." + Keys.lastDeployedAt)
        .as(Keys.lastDeployedAt)
        .andExpression("_id." + Keys.tag)
        .as(Keys.tag)
        .andExpression("_id." + Keys.displayName)
        .as(Keys.displayName)
        .andExpression(Keys.count)
        .as(Keys.count);
  }
}
