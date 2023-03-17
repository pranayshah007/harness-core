/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.businessMapping.service.intf;

import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.BusinessMappingListDTO;
import io.harness.ccm.views.businessMapping.entities.CostCategorySortType;
import io.harness.ccm.views.entities.ViewField;

import java.util.List;

public interface BusinessMappingService {
  BusinessMapping save(BusinessMapping businessMapping);
  BusinessMapping get(String uuid, String accountId);
  boolean isInvalidBusinessMappingUnallocatedCostLabel(BusinessMapping businessMapping);
  BusinessMapping get(String uuid);
  BusinessMapping update(BusinessMapping newBusinessMapping, BusinessMapping oldBusinessMapping);
  boolean delete(String uuid, String accountIdentifier);
  BusinessMappingListDTO list(String accountId, String searchKey, CostCategorySortType sortType, CCMSortOrder sortOrder,
      Integer limit, Integer offset);
  List<ViewField> getBusinessMappingViewFields(String accountId);
  List<String> getCostTargetNames(String businessMappingId, String accountId, String searchString);
}
