/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;

import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.CustomHealthLogDefinition.CustomHealthLogDefinitionKeys;
import io.harness.cvng.core.beans.CustomHealthRequestDefinition;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "CustomHealthLogCVConfigKeys")
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class CustomHealthLogCVConfig extends LogCVConfig {
  CustomHealthRequestDefinition requestDefinition;
  String logMessageJsonPath;
  String timestampJsonPath;
  String serviceInstanceJsonPath;
  static final String DATA_COLLECTION_DSL = readDSL("custom-log-fetch-data.datacollection");

  @Override
  protected void validateParams() {
    checkNotNull(getQueryName(), generateErrorMessageFromParam(LogCVConfigKeys.queryName));
    checkNotNull(getQuery(), generateErrorMessageFromParam(LogCVConfigKeys.query));
    checkNotNull(requestDefinition, generateErrorMessageFromParam(CustomHealthLogCVConfigKeys.requestDefinition));
    checkNotNull(logMessageJsonPath, generateErrorMessageFromParam(CustomHealthLogDefinitionKeys.logMessageJsonPath));
    checkNotNull(
        serviceInstanceJsonPath, generateErrorMessageFromParam(CustomHealthLogDefinitionKeys.serviceInstanceJsonPath));
    checkNotNull(timestampJsonPath, generateErrorMessageFromParam(CustomHealthLogDefinitionKeys.timestampJsonPath));
    requestDefinition.validateParams();
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.CUSTOM_HEALTH_LOG;
  }

  @Override
  public String getDataCollectionDsl() {
    return DATA_COLLECTION_DSL;
  }

  @Override
  public String getHostCollectionDSL() {
    return DATA_COLLECTION_DSL;
  }

  private static String readDSL(String fileName) {
    try {
      return Resources.toString(SplunkCVConfig.class.getResource(fileName), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static class CustomHealthLogCVConfigUpdatableEntity
      extends LogCVConfigUpdatableEntity<CustomHealthLogCVConfig, CustomHealthLogCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<CustomHealthLogCVConfig> updateOperations, CustomHealthLogCVConfig customLogCVConfig) {
      setCommonOperations(updateOperations, customLogCVConfig);
      updateOperations
          .set(CustomHealthLogCVConfigKeys.serviceInstanceJsonPath, customLogCVConfig.getServiceInstanceJsonPath())
          .set(CustomHealthLogCVConfigKeys.logMessageJsonPath, customLogCVConfig.getLogMessageJsonPath())
          .set(CustomHealthLogCVConfigKeys.timestampJsonPath, customLogCVConfig.getTimestampJsonPath())
          .set(CustomHealthLogCVConfigKeys.requestDefinition, customLogCVConfig.getRequestDefinition());
    }
  }
}
