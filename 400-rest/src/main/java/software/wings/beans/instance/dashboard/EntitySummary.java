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
import lombok.NoArgsConstructor;

/**
 * General construct that could be used anywhere
 * @author rktummala on 08/13/17
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EntitySummary extends AbstractEntitySummary {
  @Builder
  public EntitySummary(String id, String name, String type) {
    super(id, name, type);
  }
}
