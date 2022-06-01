/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.k8s.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PodStatus {
  public enum Status {
    RUNNING,
    PENDING,
    ERROR;
  }
  Status status;
  String errorMessage;
}
