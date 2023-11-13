/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetBurnDownDTO;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetBurnDownResponse;
import io.harness.cvng.servicelevelobjective.entities.ErrorBudgetBurnDown;
import io.harness.cvng.servicelevelobjective.entities.ErrorBudgetBurnDown.ErrorBudgetBurnDownKeys;
import io.harness.cvng.servicelevelobjective.services.api.ErrorBudgetBurnDownService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Sort;
import java.util.List;
import java.util.stream.Collectors;

public class ErrorBudgetBurnDownServiceImpl implements ErrorBudgetBurnDownService {
  @Inject HPersistence hPersistence;
  @Override
  public ErrorBudgetBurnDownResponse save(ProjectParams projectParams, ErrorBudgetBurnDownDTO errorBudgetBurnDownDTO) {
    ErrorBudgetBurnDown errorBudgetBurnDown = dtoToEntity(projectParams, errorBudgetBurnDownDTO);
    hPersistence.save(errorBudgetBurnDown);
    ErrorBudgetBurnDown savedErrorBudgetBurnDown =
        getErrorBudgetBurnDown(projectParams, errorBudgetBurnDownDTO.getSloIdentifier(),
            errorBudgetBurnDownDTO.getStartTime(), errorBudgetBurnDownDTO.getEndTime())
            .get(0);
    return ErrorBudgetBurnDownResponse.builder()
        .errorBudgetBurnDownDTO(entityToDto(savedErrorBudgetBurnDown))
        .createdAt(savedErrorBudgetBurnDown.getCreatedAt())
        .lastModifiedAt(savedErrorBudgetBurnDown.getLastUpdatedAt())
        .build();
  }

  @Override
  public List<ErrorBudgetBurnDownDTO> getByStartTimeAndEndTimeDto(
      ProjectParams projectParams, String sloIdentifier, Long startTime, Long endTime) {
    return getErrorBudgetBurnDown(projectParams, sloIdentifier, startTime, endTime)
        .stream()
        .map(this::entityToDto)
        .collect(Collectors.toList());
  }

  @Override
  public List<ErrorBudgetBurnDown> getByStartTimeAndEndTime(
      ProjectParams projectParams, String sloIdentifier, Long startTime, Long endTime) {
    return getErrorBudgetBurnDown(projectParams, sloIdentifier, startTime, endTime);
  }

  private List<ErrorBudgetBurnDown> getErrorBudgetBurnDown(
      ProjectParams projectParams, String sloIdentifier, Long startTime, Long endTime) {
    return hPersistence.createQuery(ErrorBudgetBurnDown.class)
        .filter(ErrorBudgetBurnDownKeys.accountId, projectParams.getAccountIdentifier())
        .filter(ErrorBudgetBurnDownKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(ErrorBudgetBurnDownKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(ErrorBudgetBurnDownKeys.sloIdentifier, sloIdentifier)
        .field(ErrorBudgetBurnDownKeys.startTime)
        .greaterThanOrEq(startTime)
        .field(ErrorBudgetBurnDownKeys.endTime)
        .lessThanOrEq(endTime)
        .order(Sort.ascending(ErrorBudgetBurnDownKeys.createdAt))
        .asList();
  }

  private ErrorBudgetBurnDownDTO entityToDto(ErrorBudgetBurnDown errorBudgetBurnDown) {
    return ErrorBudgetBurnDownDTO.builder()
        .orgIdentifier(errorBudgetBurnDown.getOrgIdentifier())
        .projectIdentifier(errorBudgetBurnDown.getProjectIdentifier())
        .sloIdentifier(errorBudgetBurnDown.getSloIdentifier())
        .startTime(errorBudgetBurnDown.getStartTime())
        .endTime(errorBudgetBurnDown.getEndTime())
        .message(errorBudgetBurnDown.getMessage())
        .build();
  }

  private ErrorBudgetBurnDown dtoToEntity(ProjectParams projectParams, ErrorBudgetBurnDownDTO errorBudgetBurnDownDTO) {
    return ErrorBudgetBurnDown.builder()
        .accountId(projectParams.getAccountIdentifier())
        .orgIdentifier(errorBudgetBurnDownDTO.getOrgIdentifier())
        .projectIdentifier(errorBudgetBurnDownDTO.getProjectIdentifier())
        .sloIdentifier(errorBudgetBurnDownDTO.getSloIdentifier())
        .message(errorBudgetBurnDownDTO.getMessage())
        .startTime(errorBudgetBurnDownDTO.getStartTime())
        .endTime(errorBudgetBurnDownDTO.getEndTime())
        .build();
  }
}
