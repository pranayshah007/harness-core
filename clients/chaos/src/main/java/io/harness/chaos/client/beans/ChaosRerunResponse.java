/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

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
