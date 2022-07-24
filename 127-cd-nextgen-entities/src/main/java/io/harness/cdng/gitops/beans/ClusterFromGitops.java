package io.harness.cdng.gitops.beans;

import io.harness.beans.ScopeLevel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ClusterFromGitops {
  String identifier;
  String name;
  ScopeLevel scopeLevel;
}
