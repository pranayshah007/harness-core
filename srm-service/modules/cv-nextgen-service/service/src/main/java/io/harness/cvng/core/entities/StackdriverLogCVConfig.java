/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.cvng.beans.DataSourceType.STACKDRIVER_LOG;
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

@JsonTypeName("STACKDRIVER_LOG")
@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "StackdriverLogCVConfigKeys")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StackdriverLogCVConfig extends LogCVConfig {
  static final String DSL = readDSL("stackdriver-log-fetch-data.datacollection");

  String messageIdentifier;
  String serviceInstanceIdentifier;

  @Override
  public String getHostCollectionDSL() {
    // TODO: To be done with CVNG-2630
    throw new RuntimeException("Not implemented");
  }

  @Override
  protected void validateParams() {
    checkNotNull(messageIdentifier, generateErrorMessageFromParam(StackdriverLogCVConfigKeys.messageIdentifier));
    checkNotNull(
        serviceInstanceIdentifier, generateErrorMessageFromParam(StackdriverLogCVConfigKeys.serviceInstanceIdentifier));
  }

  @Override
  public DataSourceType getType() {
    return STACKDRIVER_LOG;
  }

  @Override
  public String getDataCollectionDsl() {
    return DSL;
  }

  private static String readDSL(String fileName) {
    try {
      return Resources.toString(StackdriverLogCVConfig.class.getResource(fileName), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static class StackdriverLogCVConfigUpdatableEntity
      extends LogCVConfigUpdatableEntity<StackdriverLogCVConfig, StackdriverLogCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<StackdriverLogCVConfig> updateOperations, StackdriverLogCVConfig stackdriverLogCVConfig) {
      setCommonOperations(updateOperations, stackdriverLogCVConfig);
      updateOperations.set(StackdriverLogCVConfigKeys.messageIdentifier, stackdriverLogCVConfig.getMessageIdentifier());
      updateOperations.set(
          StackdriverLogCVConfigKeys.serviceInstanceIdentifier, stackdriverLogCVConfig.getServiceInstanceIdentifier());
    }
  }
}
