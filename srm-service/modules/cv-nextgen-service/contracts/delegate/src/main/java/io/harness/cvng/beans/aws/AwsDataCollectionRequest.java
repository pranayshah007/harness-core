/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.aws;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.utils.AwsUtils;
import io.harness.cvng.utils.AwsUtils.AwsAccessKeys;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

@JsonTypeName("AWS_GENERIC_DATA_COLLECTION_REQUEST")
@Data
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
public class AwsDataCollectionRequest extends DataCollectionRequest<AwsConnectorDTO> {
  public static final String DSL =
      DataCollectionRequest.readDSL("aws-generic.datacollection", AwsDataCollectionRequest.class);

  String region;
  String urlServicePrefix;
  String urlServiceSuffix;
  String awsService;
  Map<String, String> queryParameters;

  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public String getBaseUrl() {
    String baseURL = AwsUtils.getBaseUrl(region, urlServicePrefix);
    if (StringUtils.isNotBlank(urlServiceSuffix)) {
      baseURL = baseURL + "/" + urlServiceSuffix;
    }
    return baseURL;
  }

  @Override
  public Map<String, String> collectionHeaders() {
    return new HashMap<>();
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    AwsAccessKeys awsCredentials = AwsUtils.getAwsCredentials(getConnectorConfigDTO());
    Map<String, Object> dslEnvVariables = new HashMap<>();
    dslEnvVariables.put("serviceName", awsService);
    dslEnvVariables.put("region", region);
    dslEnvVariables.put("awsSecretKey", awsCredentials.getSecretAccessKey());
    dslEnvVariables.put("awsAccessKey", awsCredentials.getAccessKeyId());
    dslEnvVariables.put("awsSecurityToken", awsCredentials.getSessionToken());
    dslEnvVariables.put("url", getBaseUrl());
    dslEnvVariables.put("queryMap", queryParameters);
    return dslEnvVariables;
  }
}
