package io.harness.chaos.client.beans;

import io.harness.data.structure.EmptyPredicate;

import java.util.List;
import lombok.Value;

@Value
public class ChaosRerunResponse {
  String notifyId;
  List<ChaosErrorDTO> errors;
  String data;

  public boolean isSuccessful() {
    return EmptyPredicate.isEmpty(errors);
  }
}
