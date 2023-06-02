/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.services;

import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;
import io.harness.ng.core.variable.entity.Variable;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface VariableService {
  Variable create(String accountIdentifier, VariableDTO variableDTO);
  PageResponse<VariableResponseDTO> list(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String searchTerm, boolean includeVariablesFromEverySubScope, Pageable pageable);
  List<VariableDTO> list(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  Optional<VariableResponseDTO> get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);
  Variable update(String accountIdentifier, VariableDTO variableDTO);
  boolean delete(String accountIdentifier, String orgIdentifier, String projectIdentifier, String variableIdentifier);
  void deleteBatch(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> variableIdentifiersList);

  List<String> getExpressions(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  Long countVariables(String accountIdentifier);
}
