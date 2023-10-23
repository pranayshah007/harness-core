/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.gcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
@Schema(name = "GcpOidcTokenRequest", description = "This contains GCP OIDC Token request details")
public class GcpOidcTokenRequestDTO {
  @NotNull @NotEmpty @Schema(description = "This specifies the Workload Pool Id") private String workloadPoolId;
  @NotNull @NotEmpty @Schema(description = "This specifies the OIDC ID Provider") private String providerId;
  @NotNull @NotEmpty @Schema(description = "This specifies the GCP Project Id") private String gcpProjectId;
  @Schema(description = "This specifies the GCP Service Account Email") private String serviceAccountEmail;
}
