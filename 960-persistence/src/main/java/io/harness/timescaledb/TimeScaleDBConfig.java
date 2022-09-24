/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.timescaledb;

import io.harness.secret.ConfigSecret;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

@Value
@Builder
@FieldNameConstants(innerTypeName = "TimeScaleDBConfigFields")
@FieldDefaults(makeFinal = false)
public class TimeScaleDBConfig {
  @JsonProperty(defaultValue = "jdbc:postgresql://localhost:5432/harness") @NotEmpty private String timescaledbUrl;
  @ConfigSecret private String timescaledbUsername;
  @ConfigSecret private String timescaledbPassword;
  int connectTimeout;
  int socketTimeout;
  boolean logUnclosedConnections;
  private String loggerLevel;
  private int instanceDataRetentionDays;
  private int instanceStatsMigrationEventsLimit;
  private int instanceStatsMigrationQueryBatchSize;
  private int deploymentDataMigrationRowLimit;
  private int deploymentDataMigrationQueryBatchSize;
  boolean isHealthCheckNeeded;
}
