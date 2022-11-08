/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.instance.dashboard;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * General construct that could be used anywhere
 * @author rktummala on 08/13/17
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EntitySummary extends AbstractEntitySummary {
  private long lastDeployedAt;
  private String detail;

  @Builder
  public EntitySummary(String id, String name, String type) {
    super(id, name, type);
  }

  @Builder
  public EntitySummary(String id, String name, String type, long lastDeployedAt, String detail) {
    super(id, name, type);
    this.lastDeployedAt = lastDeployedAt;
    this.detail = detail;
  }
}
