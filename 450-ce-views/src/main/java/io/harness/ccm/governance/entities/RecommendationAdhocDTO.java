/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.governance.entities;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AwsRecommendationAdhocDTO.class, name = "AWS")
  , @JsonSubTypes.Type(value = AzureRecommendationAdhocDTO.class, name = "AZURE")
})
public interface RecommendationAdhocDTO {
  String getRoleInfo(); // AWS: RoleArn ,Azure: null
  String getRoleId(); // AWS: ExternalID ,Azure: ClientId
  String getTargetInfo(); // AWS: AwsAccountId ,Azure: SubscriptionId
  String getTenantInfo(); // AWS: null ,Azure: TenantId
  String getCloudConnectorId(); // AWS: cloudConnectorId ,Azure: cloudConnectorId
}
