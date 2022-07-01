package io.harness.ng.overview.dto;

import lombok.Builder;

@Builder
public class InfraInstanceCount {
  private String infraIdentifier;
  private String infraName;
  private int count;
}
