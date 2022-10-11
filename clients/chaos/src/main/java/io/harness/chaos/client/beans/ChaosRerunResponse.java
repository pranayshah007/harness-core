package io.harness.chaos.client.beans;

import io.harness.data.structure.EmptyPredicate;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
public class ChaosRerunResponse {
  @Data
  @Builder
  public static class ReRunChaosWorkflow {
    String notifyID;
  }

  ReRunChaosWorkflow reRunChaosWorkFlow;
  List<ChaosErrorDTO> errors;

  public boolean isSuccessful() {
    return reRunChaosWorkFlow != null && !EmptyPredicate.isEmpty(reRunChaosWorkFlow.getNotifyID());
  }

  public String getNotifyId() {
    return reRunChaosWorkFlow.getNotifyID();
  }
}
