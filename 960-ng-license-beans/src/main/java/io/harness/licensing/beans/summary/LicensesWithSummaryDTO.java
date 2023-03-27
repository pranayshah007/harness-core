/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.beans.summary;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@OwnedBy(HarnessTeam.GTM)
@SuperBuilder
@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "moduleType", visible = true)
@JsonSubTypes(value =
    {
      @JsonSubTypes.Type(value = CDLicenseSummaryDTO.class, name = "CD")
      , @JsonSubTypes.Type(value = CILicenseSummaryDTO.class, name = "CI"),
          @JsonSubTypes.Type(value = CELicenseSummaryDTO.class, name = "CE"),
          @JsonSubTypes.Type(value = CVLicenseSummaryDTO.class, name = "CV"),
          @JsonSubTypes.Type(value = CFLicenseSummaryDTO.class, name = "CF"),
          @JsonSubTypes.Type(value = STOLicenseSummaryDTO.class, name = "STO"),
          @JsonSubTypes.Type(value = IACMLicenseSummaryDTO.class, name = "IACM"),
    })
@Schema(
    name = "LicensesWithSummary", description = "This contains details of the License With Summary defined in Harness")
public abstract class LicensesWithSummaryDTO {
  Edition edition;
  LicenseType licenseType;
  ModuleType moduleType;
  long maxExpiryTime;
}
