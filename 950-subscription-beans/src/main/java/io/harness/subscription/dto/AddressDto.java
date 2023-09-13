/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.GTM)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddressDto {
  private static final String ADDRESS_PART_REGEX = "^[a-zA-Z0-9 \\-.,'&#()@!]+$";

  @NotNull @Pattern(regexp = ADDRESS_PART_REGEX) @Size(max = 46) private String line1;
  @Pattern(regexp = ADDRESS_PART_REGEX) @Size(max = 46) private String line2;
  @NotNull @Pattern(regexp = ADDRESS_PART_REGEX) @Size(max = 50) private String city;
  @NotNull @Pattern(regexp = ADDRESS_PART_REGEX) @Size(max = 50) private String state;
  @NotNull @Pattern(regexp = ADDRESS_PART_REGEX) @Size(max = 50) private String country;
  @NotNull @Pattern(regexp = ADDRESS_PART_REGEX) @Size(max = 10) private String postalCode;
}
