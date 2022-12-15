/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.type.permissions;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.QLEnum;

@OwnedBy(PL)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public enum QLActions implements QLEnum {
  CREATE,
  READ,
  UPDATE,
  DELETE,
  @Deprecated EXECUTE,
  EXECUTE_WORKFLOW,
  EXECUTE_PIPELINE,
  ROLLBACK_WORKFLOW,
  ABORT_WORKFLOW;
  @Override
  public String getStringValue() {
    return this.name();
  }
}
