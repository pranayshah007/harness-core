/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.azure;

import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.utils.AzureUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("AZURE_SERVICE_INSTANCE_FIELD_DATA")
@FieldNameConstants(innerTypeName = "AzureServiceInstanceFieldDataRequestKeys")
public class AzureServiceInstanceFieldDataRequest extends AbstractAzureDataRequest {
  String dsl;
  String resourceId;
  String metricName;
  String metricNamespace;

  @Override
  public String getDSL() {
    return dsl;
  }

  @Override
  public String getBaseUrl() {
    return AzureUtils.getBaseUrl(VerificationType.TIME_SERIES);
  }

  @Override
  public DataCollectionRequestType getType() {
    return DataCollectionRequestType.AZURE_SERVICE_INSTANCE_FIELD_DATA;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> dslEnvVariables = super.fetchDslEnvVariables();
    dslEnvVariables.put("url",
        String.format(
            "%s%s/providers/Microsoft.Insights/metricDefinitions?api-version=2021-05-01&metricnamespace=%s&metricnames=%s",
            getBaseUrl(), resourceId, metricNamespace, metricName));
    return dslEnvVariables;
  }
}
