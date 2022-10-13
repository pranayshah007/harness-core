package io.harness.chaos.client.beans;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChaosErrorDTO {
  String message;
  List<String> paths;
}
