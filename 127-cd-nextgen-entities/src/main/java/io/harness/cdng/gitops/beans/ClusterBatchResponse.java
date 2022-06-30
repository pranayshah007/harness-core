package io.harness.cdng.gitops.beans;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(GITOPS)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(NON_NULL)
@Schema(name = "ClusterBatchResponse", description = "This is the Cluster Batch Response defined in Harness")
public class ClusterBatchResponse {
  @Schema(description = "number of clusters linked") long linked;
}
