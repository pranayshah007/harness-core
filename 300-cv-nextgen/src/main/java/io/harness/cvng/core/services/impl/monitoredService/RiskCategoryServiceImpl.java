/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.core.services.impl.monitoredService;

import io.harness.cvng.core.beans.RiskCategory;
import io.harness.cvng.core.beans.monitoredService.RiskCategoryDTO;
import io.harness.cvng.core.services.api.RiskCategoryService;

import java.util.ArrayList;
import java.util.List;

public class RiskCategoryServiceImpl implements RiskCategoryService {
  @Override
  public List<RiskCategoryDTO> getRiskCategoriesDTO() {
    List<RiskCategoryDTO> riskCategoryDTOList = new ArrayList<>();

    for (RiskCategory riskCategory : RiskCategory.values()) {
      riskCategoryDTOList.add(new RiskCategoryDTO(riskCategory, riskCategory.getDisplayName(),
          riskCategory.getTimeSeriesMetricType(), riskCategory.getCvMonitoringCategory()));
    }

    return riskCategoryDTOList;
  }
}
