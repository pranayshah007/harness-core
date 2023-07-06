/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.git.model;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ListRemoteResult extends GitBaseResult {
  private Map<String, String> remoteList;

  @Builder
  public ListRemoteResult(String accountId, Map<String, String> remoteList) {
    super(accountId);
    this.remoteList = remoteList;
  }
}
