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
import io.harness.cvng.core.beans.healthsource.QueryParamsDTO;
import io.harness.cvng.core.services.impl.DataCollectionDSLFactory;
import io.harness.cvng.exception.NotImplementedForHealthSourceException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.inject.Singleton;
import dev.morphia.query.UpdateOperations;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("NEXTGEN_LOG")
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "CVConfigKeys")
@EqualsAndHashCode(callSuper = true)
public class NextGenLogCVConfig extends LogCVConfig {
  @NotNull String queryIdentifier;

  HealthSourceParams healthSourceParams;
  QueryParams queryParams;
  @NotNull DataSourceType dataSourceType;

  @NotNull String groupName;

  @Override
  protected void validateParams() {
    checkNotNull(queryParams, generateErrorMessageFromParam(CVConfigKeys.queryParams));
    checkNotNull(queryParams.getServiceInstanceField(),
        generateErrorMessageFromParam(QueryParamsDTO.QueryParamKeys.serviceInstanceField));
  }

  @Override
  public DataSourceType getType() {
    return dataSourceType;
  }

  @Override
  public String getDataCollectionDsl() {
    return DataCollectionDSLFactory.readLogDSL(dataSourceType);
  }

  @JsonIgnore
  @Override
  public String getHostCollectionDSL() {
    throw new NotImplementedForHealthSourceException("Not implemented");
  }

  @Singleton
  public static class ConfigUpdatableEntity extends LogCVConfigUpdatableEntity<NextGenLogCVConfig, NextGenLogCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<NextGenLogCVConfig> updateOperations, NextGenLogCVConfig nextGenLogCVConfig) {
      setCommonOperations(updateOperations, nextGenLogCVConfig);
      updateOperations.set(CVConfigKeys.queryParams, nextGenLogCVConfig.getQueryParams());
    }
  }
}