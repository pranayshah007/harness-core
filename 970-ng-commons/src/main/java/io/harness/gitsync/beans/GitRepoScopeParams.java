package io.harness.gitsync.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(PL)
public class GitRepoScopeParams {
  @Parameter(description = "Provide repository's project name in case of Azure repo Connector.") String gitProjectName;
}
