/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.beans;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.constraints.AssertTrue;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(GITOPS)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(NON_NULL)
@Schema(name = "ClusterBatchRequest", description = "This is the ClusterBatchRequest entity defined in Harness")
public class ClusterBatchRequest {
  @Schema(description = "organization identifier of the cluster") String orgIdentifier;
  @Schema(description = "project identifier of the cluster") String projectIdentifier;
  @Schema(description = "environment identifier of the cluster") @NotEmpty String envRef;
  @Schema(description = "link all clusters") boolean linkAllClusters;
  @Schema(description = "search term if applicable. only valid if linking all clusters") String searchTerm;
  @Schema(description = "list of cluster identifiers and names") List<ClusterBasicDTO> clusters;

  @Data
  public static class ClusterBasicDTO {
    @EntityIdentifier @Schema(description = "identifier of the cluster") String identifier;
    @Schema(description = "name of the cluster") @EntityName String name;
  }

  @AssertTrue(
      message =
          "Only one of linkAllClusters or clusters must be set. Search term cannot be set if not linking all clusters")
  private boolean
  isValid() {
    boolean c1 = linkAllClusters ^ isNotEmpty(clusters);
    boolean c2 = isEmpty(searchTerm) || linkAllClusters;
    return c1 && c2;
  }
}
