/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.entities.SLOErrorBudgetReset;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface SLOErrorBudgetResetService {
  SLOErrorBudgetResetDTO resetErrorBudget(ProjectParams projectParams, SLOErrorBudgetResetDTO sloErrorBudgetResetDTO);

  SLOErrorBudgetReset getErrorBudgetResetByUuid(String uuid);

  List<SLOErrorBudgetReset> getErrorBudgetResetEntities(
      ProjectParams projectParams, String sloIdentifier, long startTime, long endTime);

  List<SLOErrorBudgetResetDTO> getErrorBudgetResets(ProjectParams projectParams, String sloIdentifier);

  Map<String, List<SLOErrorBudgetResetDTO>> getErrorBudgetResets(
      ProjectParams projectParams, Set<String> sloIdentifiers);

  void clearErrorBudgetResets(ProjectParams projectParams, String sloIdentifier);
}
