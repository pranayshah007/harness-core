package io.harness.cdng.pipeline.executions.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.HashSet;
import java.util.Set;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.CDC)
@NoArgsConstructor
public class GitOpsExecutionSummary {
  @Getter private final Set<Environment> environments = new HashSet<>();

  public void addSingleEnvironment(@NotEmpty String envId, @NotEmpty String envName) {
    environments.add(Environment.builder().identifier(envId).name(envName).build());
  }

  public void addSingleEnvironmentWithinEnvGroup(
      @NotEmpty String envGroupId, @NotEmpty String envGroupName, @NotEmpty String envId, @NotEmpty String envName) {
    environments.add(Environment.builder()
                         .identifier(envId)
                         .name(envName)
                         .envGroupName(envGroupName)
                         .envGroupIdentifier(envGroupId)
                         .build());
  }

  @Data
  @Builder
  public static class Environment {
    String name;
    String identifier;
    String envGroupIdentifier;
    String envGroupName;
  }
}
