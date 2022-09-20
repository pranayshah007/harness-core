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

@JsonTypeName("ELK_LOG")
@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "ELKCVConfigKeys")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ELKCVConfig extends LogCVConfig {
  static final String DSL = readDSL("elk-log-fetch-data.datacollection");
  String index;
  String serviceInstanceIdentifier;
  String timeStampIdentifier;
  String timeStampFormat;
  String messageIdentifier;

  @Override
  protected void validateParams() {
    checkNotNull(index, generateErrorMessageFromParam(ELKCVConfigKeys.index));
    checkNotNull(serviceInstanceIdentifier, generateErrorMessageFromParam(ELKCVConfigKeys.serviceInstanceIdentifier));
    checkNotNull(timeStampIdentifier, generateErrorMessageFromParam(ELKCVConfigKeys.timeStampIdentifier));
    checkNotNull(timeStampFormat, generateErrorMessageFromParam(ELKCVConfigKeys.timeStampFormat));
    checkNotNull(messageIdentifier, generateErrorMessageFromParam(ELKCVConfigKeys.messageIdentifier));
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.ELK_LOG;
  }

  @Override
  public String getDataCollectionDsl() {
    return DSL;
  }

  @Override
  public String getHostCollectionDSL() {
    throw new RuntimeException("Not implemented");
  }

  private static String readDSL(String fileName) {
    try {
      return Resources.toString(ELKCVConfig.class.getResource(fileName), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static class ELKCVConfigUpdatableEntity extends LogCVConfigUpdatableEntity<ELKCVConfig, ELKCVConfig> {
    @Override
    public void setUpdateOperations(UpdateOperations<ELKCVConfig> updateOperations, ELKCVConfig elkLogCVConfig) {
      setCommonOperations(updateOperations, elkLogCVConfig);
      updateOperations.set(ELKCVConfigKeys.index, elkLogCVConfig.getIndex());
      updateOperations.set(ELKCVConfigKeys.serviceInstanceIdentifier, elkLogCVConfig.getServiceInstanceIdentifier());
      updateOperations.set(ELKCVConfigKeys.timeStampIdentifier, elkLogCVConfig.getTimeStampIdentifier());
      updateOperations.set(ELKCVConfigKeys.timeStampFormat, elkLogCVConfig.getTimeStampFormat());
      updateOperations.set(ELKCVConfigKeys.messageIdentifier, elkLogCVConfig.getMessageIdentifier());
    }
  }
}
