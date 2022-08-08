/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import io.harness.cvng.beans.DataSourceType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@JsonTypeName("ERROR_TRACKING")
@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "ErrorTrackingCVConfigKeys")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ErrorTrackingCVConfig extends LogCVConfig {
  @VisibleForTesting static final String DSL = readDSL("overops.datacollection");
  @NotNull private String serviceIdentifier;
  @NotNull private String envIdentifier;
  @Override
  protected void validateParams() {
    // No parameters to be validated
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.ERROR_TRACKING;
  }

  @Override
  public String getDataCollectionDsl() {
    return DSL;
  }

  @Override
  public String getHostCollectionDSL() {
    return null;
  }

  private static String readDSL(String fileName) {
    try {
      return Resources.toString(ErrorTrackingCVConfig.class.getResource(fileName), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static class ErrorTrackingCVConfigUpdatableEntity
      extends LogCVConfigUpdatableEntity<ErrorTrackingCVConfig, ErrorTrackingCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<ErrorTrackingCVConfig> updateOperations, ErrorTrackingCVConfig cvConfig) {
      setCommonOperations(updateOperations, cvConfig);
    }
  }
}
