/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@OwnedBy(HarnessTeam.CE)
public class CcmConnectorFilter {
  List<CEFeatures> featuresEnabled;
  List<CEFeatures> featuresDisabled;
  String awsAccountId; // For backword compatibility with UI
  List<String> awsAccountIds;
  String azureSubscriptionId;
  String azureTenantId;
  String gcpProjectId;
  List<String> k8sConnectorRef;
}
