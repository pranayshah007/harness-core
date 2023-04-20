/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.trigger;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public enum ReleaseAction {
  CREATED("Created", "created"),
  PUBLISHED("Published", "published"),
  RELEASED("released", "released"),
  UNPUBLISHED("Unpublished", "unpublished"),
  EDITED("Edited", "edited"),
  DELETED("Deleted", "deleted"),
  PRE_RELEASED("Pre-Released", "prereleased");

  private String displayName;
  private String value;

  ReleaseAction(String displayName, String value) {
    this.displayName = displayName;
    this.value = value;
    ReleaseActionHolder.map.put(value, this);
  }

  private static class ReleaseActionHolder {
    static Map<String, ReleaseAction> map = new HashMap<>();
  }

  public static ReleaseAction find(String val) {
    ReleaseAction releaseAction = ReleaseActionHolder.map.get(val);
    if (releaseAction == null) {
      throw new InvalidRequestException(String.format("Unsupported Release action %s.", val));
    }
    return releaseAction;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getValue() {
    return value;
  }
}
