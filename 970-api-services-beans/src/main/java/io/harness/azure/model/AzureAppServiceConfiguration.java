/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.model;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AzureAppServiceConfiguration {
  private String appSettingsJSON;
  private String connStringsJSON;

  public List<AzureAppServiceApplicationSetting> getAppSettings() {
    if (isBlank(appSettingsJSON)) {
      return Collections.emptyList();
    }

    return JsonUtils.asObject(appSettingsJSON, new TypeReference<List<AzureAppServiceApplicationSetting>>() {});
  }

  public List<AzureAppServiceConnectionString> getConnStrings() {
    if (isBlank(connStringsJSON)) {
      return Collections.emptyList();
    }

    return JsonUtils.asObject(connStringsJSON, new TypeReference<List<AzureAppServiceConnectionString>>() {});
  }
}
