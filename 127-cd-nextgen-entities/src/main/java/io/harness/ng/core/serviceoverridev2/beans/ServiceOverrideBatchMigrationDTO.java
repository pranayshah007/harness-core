/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverridev2.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Value
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "ServiceOverrideBatchMigrationDTO", description = "This is the Service Override batch migration request")
public class ServiceOverrideBatchMigrationDTO {
  @NonNull List<String> accountIds;
}
