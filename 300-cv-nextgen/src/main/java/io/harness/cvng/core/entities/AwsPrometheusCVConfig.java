/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import io.harness.cvng.beans.DataSourceType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@JsonTypeName("AWS_PROMETHEUS")
@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "AwsPrometheusCVConfigKeys")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AwsPrometheusCVConfig extends PrometheusCVConfig {
  private String region;
  private String workspaceId;

  @Override
  public DataSourceType getType() {
    return DataSourceType.AWS_PROMETHEUS;
  }

  public static class AwsPrometheusUpdatableEntity
      extends MetricCVConfigUpdatableEntity<AwsPrometheusCVConfig, AwsPrometheusCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<AwsPrometheusCVConfig> updateOperations, AwsPrometheusCVConfig cvConfig) {
      setCommonOperations(updateOperations, cvConfig);
      updateOperations.set(PrometheusCVConfigKeys.groupName, cvConfig.getGroupName())
          .set(PrometheusCVConfigKeys.metricInfoList, cvConfig.getMetricInfoList())
          .set(AwsPrometheusCVConfigKeys.region, cvConfig.getRegion())
          .set(AwsPrometheusCVConfigKeys.workspaceId, cvConfig.getWorkspaceId());
    }
  }
}
