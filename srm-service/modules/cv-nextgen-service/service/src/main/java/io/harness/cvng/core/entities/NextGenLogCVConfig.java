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
import io.harness.cvng.core.beans.healthsource.QueryParams;
import io.harness.cvng.exception.NotImplementedForHealthSourceException;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@JsonTypeName("NEXTGEN_LOG")
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "CVConfigKeys")
@EqualsAndHashCode(callSuper = true)
public class NextGenLogCVConfig extends LogCVConfig {
  QueryParams queryParams;
  DataSourceType dataSourceType;

  @Override
  protected void validateParams() {
    checkNotNull(queryParams, generateErrorMessageFromParam(CVConfigKeys.queryParams));
    checkNotNull(queryParams.getServiceInstanceField(),
        generateErrorMessageFromParam(QueryParams.QueryParamKeys.serviceInstanceField));
  }

  @Override
  public DataSourceType getType() {
    return dataSourceType;
  }

  @Override
  public String getDataCollectionDsl() {
    return readLogDSL(dataSourceType);
  }

  public static String readLogDSL(DataSourceType dataSourceType) {
    // TODO dont read repeatedly and also move it from here
    if (dataSourceType == DataSourceType.SUMOLOGIC_LOG) {
      try {
        return Resources.toString(
            NextGenLogCVConfig.class.getResource("sumologic-log.datacollection"), StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    } else {
      throw new NotImplementedForHealthSourceException("Not Implemented.");
    }
  }

  @Override
  public String getHostCollectionDSL() {
    throw new NotImplementedForHealthSourceException("Not implemented");
  }

  public static class ConfigUpdatableEntity extends LogCVConfigUpdatableEntity<NextGenLogCVConfig, NextGenLogCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<NextGenLogCVConfig> updateOperations, NextGenLogCVConfig nextGenLogCVConfig) {
      setCommonOperations(updateOperations, nextGenLogCVConfig);
      updateOperations.set(CVConfigKeys.queryParams, nextGenLogCVConfig.getQueryParams());
    }
  }
}