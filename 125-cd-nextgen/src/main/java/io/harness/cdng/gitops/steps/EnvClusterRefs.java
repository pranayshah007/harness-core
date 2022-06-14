package io.harness.cdng.gitops.steps;

import io.harness.annotation.RecasterAlias;

import java.util.Set;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("envClusterRefs")
@RecasterAlias("io.harness.cdng.gitops.steps.EnvClusterRefs")
public class EnvClusterRefs {
  private String envRef;
  private Set<String> clusterRefs;
  boolean deployToAll;
}
