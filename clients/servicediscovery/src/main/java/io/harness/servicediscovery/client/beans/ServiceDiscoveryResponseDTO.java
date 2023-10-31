/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.servicediscovery.client.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CHAOS)
public class ServiceDiscoveryResponseDTO<T> {
  List<T> items;
  Page page;
  String correlationId;

  public static class Page {
    boolean all;
    int index;
    int limit;
    int totalPages;
    int totalItems;
  }
}
