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
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CE)
@Data
@Builder
@EqualsAndHashCode
@Schema(description =
            "Combination of attributes used as a group-by key for aggregating cost during billing-data-verification")
public class CCMBillingDataVerificationKey {
  String harnessAccountId;
  String connectorId;
  String cloudProvider;
  String cloudProviderAccountId; // awsusageaccountid, gcpbillingaccountid, azuresubscriptionguid
  LocalDate usageStartDate;
  LocalDate usageEndDate;
  String costType; // AWSUnblendedCost, AWSAmortisedCost, AWSNetAmortisedCost etc.
}
