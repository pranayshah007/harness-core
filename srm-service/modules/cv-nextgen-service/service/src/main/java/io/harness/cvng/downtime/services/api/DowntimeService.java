/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.services.api;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.DeleteEntityByHandler;
import io.harness.cvng.downtime.beans.DowntimeDTO;
import io.harness.cvng.downtime.beans.DowntimeHistoryView;
import io.harness.cvng.downtime.beans.DowntimeListView;
import io.harness.cvng.downtime.beans.DowntimeResponse;
import io.harness.cvng.downtime.entities.Downtime;
import io.harness.ng.beans.PageResponse;

public interface DowntimeService extends DeleteEntityByHandler<Downtime> {
  DowntimeResponse create(ProjectParams projectParams, DowntimeDTO downtimeDTO);

  DowntimeResponse get(ProjectParams projectParams, String identifier);

  DowntimeResponse update(ProjectParams projectParams, String identifier, DowntimeDTO downtimeDTO);

  boolean delete(ProjectParams projectParams, String identifier);

  PageResponse<DowntimeListView> list(ProjectParams projectParams, Integer offset, Integer pageSize);

  PageResponse<DowntimeHistoryView> history(ProjectParams projectParams, Integer offset, Integer pageSize);
}
