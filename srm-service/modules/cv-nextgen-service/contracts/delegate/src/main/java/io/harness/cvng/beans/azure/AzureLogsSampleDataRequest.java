/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.azure;

import static io.harness.cvng.utils.AzureUtils.AZURE_TOKEN_URL_FORMAT;

import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.utils.AzureUtils;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Instant;
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
@JsonTypeName("AZURE_LOGS_SAMPLE_DATA")
@FieldNameConstants(innerTypeName = "AzureSampleDataRequestKeys")
public class AzureLogsSampleDataRequest extends AbstractAzureDataRequest {
  String query;
  String dsl;
  Instant from;
  Instant to;
  String resourceId;

  @Override
  public String getDSL() {
    return dsl;
  }

  @Override
  public String getBaseUrl() {
    return AzureUtils.getBaseUrl(VerificationType.LOG);
  }

  @Override
  public DataCollectionRequestType getType() {
    return DataCollectionRequestType.AZURE_LOGS_SAMPLE_DATA;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    AzureManualDetailsDTO azureManualDetailsDTO =
        AzureUtils.validateConnectorConfigurationType(getConnectorConfigDTO().getCredential().getConfig());
    AzureClientSecretKeyDTO azureClientSecretKeyDTO =
        AzureUtils.validateConnectorAuthenticationType(azureManualDetailsDTO.getAuthDTO().getCredentials());
    Map<String, Object> dslEnvVariables = super.fetchDslEnvVariables();
    dslEnvVariables.put("clientId", azureManualDetailsDTO.getClientId());
    dslEnvVariables.put("clientSecret", String.valueOf(azureClientSecretKeyDTO.getSecretKey().getDecryptedValue()));
    dslEnvVariables.put("azureTokenUrl", String.format(AZURE_TOKEN_URL_FORMAT, azureManualDetailsDTO.getTenantId()));
    dslEnvVariables.put("query", query);
    dslEnvVariables.put(
        "url", String.format("%sv1%s/query?scope=hierarchy&timespan=%s/%s", getBaseUrl(), resourceId, from, to));
    return dslEnvVariables;
  }
}
