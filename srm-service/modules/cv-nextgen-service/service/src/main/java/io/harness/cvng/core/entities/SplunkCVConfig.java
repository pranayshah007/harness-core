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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@JsonTypeName("SPLUNK")
@Data
@FieldNameConstants(innerTypeName = "SplunkCVConfigKeys")
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SplunkCVConfig extends LogCVConfig {
  @VisibleForTesting static final String DSL = readDSL("splunk.datacollection");
  static final String HOST_COLLECTION_DSL = readDSL("splunk_host_collection.datacollection");
  private String serviceInstanceIdentifier;

  @Override
  public DataSourceType getType() {
    return DataSourceType.SPLUNK;
  }

  @Override
  protected void validateParams() {
    checkNotNull(
        serviceInstanceIdentifier, generateErrorMessageFromParam(SplunkCVConfigKeys.serviceInstanceIdentifier));
  }

  @JsonIgnore
  @Override
  public String getDataCollectionDsl() {
    // TODO: Need to define ownership of DSL properly. Currently for metric it is with Metric Pack and for log there is
    // no such concept.
    return DSL;
  }

  private static String readDSL(String fileName) {
    try {
      return Resources.toString(SplunkCVConfig.class.getResource(fileName), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public String getHostCollectionDSL() {
    return HOST_COLLECTION_DSL;
  }

  public static class SplunkCVConfigUpdatableEntity extends LogCVConfigUpdatableEntity<SplunkCVConfig, SplunkCVConfig> {
    @Override
    public void setUpdateOperations(UpdateOperations<SplunkCVConfig> updateOperations, SplunkCVConfig splunkCVConfig) {
      setCommonOperations(updateOperations, splunkCVConfig);
      updateOperations.set(SplunkCVConfigKeys.serviceInstanceIdentifier, splunkCVConfig.getServiceInstanceIdentifier());
    }
  }
}
