/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.resolver.outputs;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.Resolver;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;

@OwnedBy(CDC)
public interface ExecutionSweepingOutputService extends Resolver<ExecutionSweepingOutput> {
  OptionalSweepingOutput resolveOptional(Ambiance ambiance, RefObject refObject);
  Optional<Map<String, Object>> resolveFromJsonAsMap(Ambiance ambiance, RefObject refObject);
  List<OptionalSweepingOutput> listOutputsWithGivenNameAndSetupIds(
      Ambiance ambiance, String name, List<String> nodeIds);
  List<OptionalSweepingOutput> listOutputsWithGivenNameAndRuntimeIds(
      Ambiance ambiance, String name, List<String> nodeIds);
  // Consume value only if resolveOptional is not found
  String consumeOptional(
      @NotNull Ambiance ambiance, @NotNull String name, ExecutionSweepingOutput value, String groupName);
}
