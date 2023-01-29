/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.graphql.dto.currency;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.currency.Currency;
import io.harness.ccm.graphql.dto.common.CloudServiceProvider;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "CurrencyConversionFactorData", description = "Currency conversion factor")
@OwnedBy(CE)
public class CurrencyConversionFactorData {
  @Schema(description = "Account id") @JsonIgnore String accountId;
  @Schema(description = "Cloud service provider") CloudServiceProvider cloudServiceProvider;
  @Schema(description = "Source currency") Currency sourceCurrency;
  @Schema(description = "Destination currency") Currency destinationCurrency;
  @Schema(description = "Conversion factor") Double conversionFactor;
  @Schema(description = "Month") @JsonIgnore Date month;
  @Schema(description = "Conversion type") ConversionType conversionType;
  @Schema(description = "Conversion Source") @JsonIgnore ConversionSource conversionSource;
  @Schema(description = "Created at") @JsonIgnore Long createdAt;
  @Schema(description = "Updated at") @JsonIgnore Long updatedAt;
}