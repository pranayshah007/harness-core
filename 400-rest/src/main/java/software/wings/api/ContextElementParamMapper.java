/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.sm.ExecutionContext;

import java.util.Map;

/**
 * An abstraction of a class that's responsible for creating a param map of a ContextElement for a given
 * ExecutionContext.
 */
@OwnedBy(CDC)
@TargetModule(_957_CG_BEANS)
public interface ContextElementParamMapper {
  /**
   * Creates a param map for the given ExecutionContext.
   *
   * @param context the ExecutionContext
   * @return the map
   */
  Map<String, Object> paramMap(ExecutionContext context);
}