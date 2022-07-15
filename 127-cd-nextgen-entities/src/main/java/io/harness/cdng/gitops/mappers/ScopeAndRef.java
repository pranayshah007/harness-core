package io.harness.cdng.gitops.mappers;

import io.harness.beans.ScopeLevel;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class ScopeAndRef {
  String originalRef;
  String ref;
  ScopeLevel scope;
}
