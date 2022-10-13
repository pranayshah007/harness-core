package io.harness.chaos.client.beans;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChaosQuery {
  String query;
}
