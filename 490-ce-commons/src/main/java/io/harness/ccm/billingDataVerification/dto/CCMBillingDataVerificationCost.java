/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.billingDataVerification.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import java.sql.Timestamp;
import java.util.Date;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CE)
@Data
@Builder
@Schema(description = "Cost values as obtained from different sources")
public class CCMBillingDataVerificationCost {
  Double costFromRawBillingTable;
  Double costFromUnifiedTable;
  Double costFromCloudProviderAPI;
}
