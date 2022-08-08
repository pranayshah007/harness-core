/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.businessMapping.service.intf;

import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.entities.ViewField;

import java.util.List;

public interface BusinessMappingService {
  BusinessMapping save(BusinessMapping businessMapping);
  BusinessMapping get(String uuid, String accountId);
  BusinessMapping get(String uuid);
  BusinessMapping update(BusinessMapping businessMapping);
  boolean delete(String uuid, String accountIdentifier);
  List<BusinessMapping> list(String accountId);
  List<ViewField> getBusinessMappingViewFields(String accountId);
}
