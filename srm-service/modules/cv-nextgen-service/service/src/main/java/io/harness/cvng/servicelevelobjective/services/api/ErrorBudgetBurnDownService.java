/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetBurnDownDTO;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetBurnDownResponse;
import io.harness.cvng.servicelevelobjective.entities.ErrorBudgetBurnDown;

import java.util.List;

public interface ErrorBudgetBurnDownService {
  ErrorBudgetBurnDownResponse save(ProjectParams projectParams, ErrorBudgetBurnDownDTO errorBudgetBurnDownDTO);

  List<ErrorBudgetBurnDownDTO> getByStartTimeAndEndTimeDto(
      ProjectParams projectParams, String sloIdentifier, Long startTime, Long endTime);

  List<ErrorBudgetBurnDown> getByStartTimeAndEndTime(
      ProjectParams projectParams, String sloIdentifier, Long startTime, Long endTime);
}
