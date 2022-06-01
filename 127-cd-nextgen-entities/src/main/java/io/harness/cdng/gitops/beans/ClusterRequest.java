/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.beans;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(GITOPS)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(NON_NULL)
@Schema(name = "ClusterRequest", description = "This is the ClusterRequest entity defined in Harness")
public class ClusterRequest {
  @EntityIdentifier @Schema(description = "identifier of the cluster") String identifier;
  @Schema(description = "organization identifier of the cluster") String orgIdentifier;
  @Schema(description = "project identifier of the cluster") String projectIdentifier;
  @Schema(description = "environment identifier of the cluster") @NotEmpty String envRef;
}
