package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashSet;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("PMSPipelineListRepoResponse")
@Schema(name = "PMSPipelineListRepoResponse", description = "This is the list of repositories for pipelines created.")
@OwnedBy(PL)
public class PMSPipelineListRepoResponse {
  @Hidden @Schema(description = "Contains the list of repositories from the execution") HashSet<String> repositories;
}
