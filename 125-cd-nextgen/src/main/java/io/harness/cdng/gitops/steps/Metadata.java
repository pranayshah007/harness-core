package io.harness.cdng.gitops.steps;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class Metadata {
  String identifier;
  String name;
}
