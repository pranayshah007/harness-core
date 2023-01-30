/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.moduleversioninfo.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "MicroservicesVersionInfoKeys")
@Schema(name = "MicroservicesVersionInfo",
    description = "This is the view of the Microservices Version Info of the entity.")
public class MicroservicesVersionInfo {
  @NotNull @Schema(description = "Microservices name") private String name;
  @NotNull @Schema(description = "Microservices versionUrl") private String versionUrl;
  @NotNull @Schema(description = "Microservices version") private String version;
}
