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

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@JsonTypeName("SUMOLOGIC_LOG")
@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "SumologicLogCVConfigKeys")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SumologicLogCVConfig extends LogCVConfig {
  static final String DSL = readDSL();

  String serviceInstanceIdentifier;
  @Override
  protected void validateParams() {
    checkNotNull(
        serviceInstanceIdentifier, generateErrorMessageFromParam(SumologicLogCVConfigKeys.serviceInstanceIdentifier));
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.SUMOLOGIC_LOG;
  }

  @Override
  public String getDataCollectionDsl() {
    return DSL;
  }

  private static String readDSL() {
    try {
      return Resources.toString(
          SumologicLogCVConfig.class.getResource("sumologic-log.datacollection"), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public String getHostCollectionDSL() {
    throw new RuntimeException("Not implemented");
  }

  public static class SumologicLogCVConfigUpdatableEntity
      extends LogCVConfigUpdatableEntity<SumologicLogCVConfig, SumologicLogCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<SumologicLogCVConfig> updateOperations, SumologicLogCVConfig sumologicLogCVConfig) {
      setCommonOperations(updateOperations, sumologicLogCVConfig);
      updateOperations.set(SumologicLogCVConfig.SumologicLogCVConfigKeys.serviceInstanceIdentifier,
          sumologicLogCVConfig.getServiceInstanceIdentifier());
    }
  }
}