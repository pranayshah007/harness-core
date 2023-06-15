/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.services;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorCategory;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.filter.dto.FilterPropertiesDTO;

import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(DX)
public interface ConnectorFilterService {
  Criteria createCriteriaFromConnectorFilter(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String searchTerm, ConnectorType type, ConnectorCategory category, ConnectorCategory sourceCategory,
      boolean isBuiltInSMDisabled, String version);

  Criteria createCriteriaFromConnectorListQueryParams(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String filterIdentifier, String searchTerm, FilterPropertiesDTO filterProperties,
      Boolean includeAllConnectorsAccessibleAtScope, boolean isBuiltInSMDisabled, String version);
}
