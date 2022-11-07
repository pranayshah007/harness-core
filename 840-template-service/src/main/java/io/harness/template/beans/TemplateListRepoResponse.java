package io.harness.template.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashSet;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.PL)
@Value
@Builder
@Hidden
@Schema(name = "TemplateListRepoResponse",
    description = "Contains the list of unique repositories for given scope of template listing")
public class TemplateListRepoResponse {
  @Hidden
  @Schema(description = "Contains the list of unique repositories for given scope of template listing")
  HashSet<String> repositories;
}
