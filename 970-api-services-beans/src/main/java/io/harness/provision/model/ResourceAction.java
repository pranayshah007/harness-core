/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.provision.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public enum ResourceAction {
  NO_OP("no-op"),
  CREATE("create"),
  READ("read"),
  UPDATE("update"),
  DELETE("delete");

  private String name;

  ResourceAction(String name) {
    this.name = name;
  }

  public String getName() {
    return this.name;
  }

  static ResourceAction getResourceActionByName(String name) {
    for (ResourceAction resourceAction : ResourceAction.values()) {
      if (resourceAction.getName().equals(name)) {
        return resourceAction;
      }
    }
    return null;
  }
}
