/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filter.service;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;

import java.util.List;
import org.springframework.data.domain.Page;

@OwnedBy(DX)
public interface FilterService {
  FilterDTO create(String accountId, FilterDTO filterDTO);

  FilterDTO update(String accountId, FilterDTO filterDTO);

  boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String identifier, FilterType type);

  void deleteByScope(Scope scope);

  FilterDTO get(String accountId, String orgIdentifier, String projectIdentifier, String identifier, FilterType type);

  Page<FilterDTO> list(int page, int size, String accountId, String orgIdentifier, String projectIdentifier,
      List<String> filterIds, FilterType type);
}
