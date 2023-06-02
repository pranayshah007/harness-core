/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.intfc;

import io.harness.delegate.resources.DelegateStackDriverLog;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;

import java.util.List;

public interface DelegateStackdriverLogService {
  PageResponse<DelegateStackDriverLog> fetchPageLogs(
      String accountId, List<String> taskIds, PageRequest request, long start, long end);
}
