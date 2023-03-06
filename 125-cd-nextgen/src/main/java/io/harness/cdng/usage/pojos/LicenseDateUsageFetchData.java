/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.pojos;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.CDLicenseType;
import io.harness.licensing.usage.params.filter.LicenseDateUsageReportType;

import java.time.LocalDate;
import javax.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDP)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LicenseDateUsageFetchData {
  @NotBlank String accountIdentifier;
  @NotBlank private LocalDate fromDate;
  @NotBlank private LocalDate toDate;
  @NotBlank private CDLicenseType licenseType;
  private LicenseDateUsageReportType reportType = LicenseDateUsageReportType.DAILY;
}
