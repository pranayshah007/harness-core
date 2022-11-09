/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;

import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.cvng.beans.DataSourceType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@JsonTypeName("DATADOG_LOG")
@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "DatadogLogCVConfigKeys")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DatadogLogCVConfig extends LogCVConfig {
  static final String DSL = readDSL("datadog-log-fetch-data.datacollection");

  List<String> indexes;
  String serviceInstanceIdentifier;

  public List<String> getIndexes() {
    return indexes != null ? indexes : new ArrayList<>();
  }

  @Override
  protected void validateParams() {
    checkNotNull(
        serviceInstanceIdentifier, generateErrorMessageFromParam(DatadogLogCVConfigKeys.serviceInstanceIdentifier));
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.DATADOG_LOG;
  }

  @Override
  public String getDataCollectionDsl() {
    return DSL;
  }

  @JsonIgnore
  @Override
  public String getHostCollectionDSL() {
    throw new RuntimeException("Not implemented");
  }

  private static String readDSL(String fileName) {
    try {
      return Resources.toString(DatadogLogCVConfig.class.getResource(fileName), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static class DatadogLogCVConfigUpdatableEntity
      extends LogCVConfigUpdatableEntity<DatadogLogCVConfig, DatadogLogCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<DatadogLogCVConfig> updateOperations, DatadogLogCVConfig datadogLogCVConfig) {
      setCommonOperations(updateOperations, datadogLogCVConfig);
      updateOperations.set(DatadogLogCVConfigKeys.indexes, datadogLogCVConfig.getIndexes());
      updateOperations.set(
          DatadogLogCVConfigKeys.serviceInstanceIdentifier, datadogLogCVConfig.getServiceInstanceIdentifier());
    }
  }
}
