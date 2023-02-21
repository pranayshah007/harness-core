/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.models;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.environment.beans.EnvironmentType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;

@Getter
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "Keys")
@OwnedBy(HarnessTeam.DX)
public class CountByServiceIdAndEnvType {
  private String serviceIdentifier;
  private EnvironmentType envType;
  private int count;

  public static ProjectionOperation getProjection() {
    return Aggregation.project()
        .andExpression("_id." + Keys.serviceIdentifier)
        .as(Keys.serviceIdentifier)
        .andExpression("_id." + Keys.envType)
        .as(Keys.envType)
        .andExpression(Keys.count)
        .as(Keys.count);
  }
}
