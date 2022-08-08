/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.fqn;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FQNNode {
  public enum NodeType { KEY, KEY_WITH_UUID, PARALLEL, UUID }

  private NodeType nodeType;
  private String key;
  private String uuidKey;
  private String uuidValue;
}
