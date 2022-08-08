/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.verification.log;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import software.wings.beans.LogCollectionInfo;
import software.wings.beans.apm.Method;
import software.wings.verification.CVConfiguration;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "CustomLogCVServiceConfigurationKeys")
@EqualsAndHashCode(callSuper = true)
public class CustomLogCVServiceConfiguration extends LogsCVConfiguration {
  private LogCollectionInfo logCollectionInfo;

  public void setLogCollectionInfo(LogCollectionInfo info) {
    this.logCollectionInfo = info;
    this.query = logCollectionInfo.getCollectionUrl();
  }

  public void setQuery() {
    if (logCollectionInfo != null) {
      this.query = logCollectionInfo.getCollectionUrl();
    }
  }

  public boolean validateConfiguration() {
    if (logCollectionInfo != null) {
      if (logCollectionInfo.getMethod() == Method.POST && isEmpty(logCollectionInfo.getCollectionBody())) {
        return false;
      }
      boolean bodyContainsStartTime = isNotEmpty(logCollectionInfo.getCollectionBody())
          && (logCollectionInfo.getCollectionBody().contains("${start_time}")
              || logCollectionInfo.getCollectionBody().contains("${start_time_seconds}"));
      boolean urlContainsStartTime = logCollectionInfo.getCollectionUrl().contains("${start_time}")
          || logCollectionInfo.getCollectionUrl().contains("${start_time_seconds}");
      boolean bodyContainsEndTime = isNotEmpty(logCollectionInfo.getCollectionBody())
          && (logCollectionInfo.getCollectionBody().contains("${end_time}")
              || logCollectionInfo.getCollectionBody().contains("${end_time_seconds}"));
      boolean urlContainsEndTime = logCollectionInfo.getCollectionUrl().contains("${end_time}")
          || logCollectionInfo.getCollectionUrl().contains("${end_time_seconds}");

      if ((bodyContainsEndTime || urlContainsEndTime) && (bodyContainsStartTime || urlContainsStartTime)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public CVConfiguration deepCopy() {
    CustomLogCVServiceConfiguration clonedConfig = new CustomLogCVServiceConfiguration();
    super.copy(clonedConfig);
    clonedConfig.setLogCollectionInfo(this.getLogCollectionInfo());
    clonedConfig.setQuery(this.getQuery());
    return clonedConfig;
  }

  @Data
  @Builder
  @EqualsAndHashCode(callSuper = true)
  @JsonPropertyOrder({"type", "harnessApiVersion"})
  public static final class CustomLogsCVConfigurationYaml extends LogsCVConfigurationYaml {
    private LogCollectionInfo logCollectionInfo;
  }
}
