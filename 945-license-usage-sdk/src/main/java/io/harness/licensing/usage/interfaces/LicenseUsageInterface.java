/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.usage.interfaces;

import io.harness.ModuleType;
import io.harness.licensing.usage.beans.LicenseUsageDTO;
import io.harness.licensing.usage.params.PageableUsageRequestParams;
import io.harness.licensing.usage.params.UsageRequestParams;

import org.springframework.data.domain.Page;

public interface LicenseUsageInterface<T extends LicenseUsageDTO, K extends UsageRequestParams> {
  T getLicenseUsage(String accountIdentifier, ModuleType module, long timestamp, K usageRequest);

  /**
   * List active license usage
   *
   * @param accountIdentifier the account identifier
   * @param module the module
   * @param currentTS the current timestamp in ms
   * @param usageRequest pageable usage request
   * @param <S> license usage type
   * @return the page of
   */
  <S extends LicenseUsageDTO> Page<S> listLicenseUsage(
      String accountIdentifier, ModuleType module, long currentTS, PageableUsageRequestParams usageRequest);
}
