/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel(value = "AccessCheckRequest")
@Schema(name = "AccessCheckRequest")
@OwnedBy(HarnessTeam.PL)
public class AccessCheckRequestDTO {
  @Schema(description = "List of permission checks to perform", required = true)
  @Size(max = 10000, message = "The number of permission checks '${validatedValue.size()}' must be less than {max}")
  @Valid
  List<PermissionCheckDTO> permissions;
  @Schema(description = "Principal (user/service account) to check the access for")
  @Valid
  @Nullable
  Principal principal;
}
