/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.services.impl;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.downtime.beans.DowntimeDTO;
import io.harness.cvng.downtime.beans.DowntimeHistoryView;
import io.harness.cvng.downtime.beans.DowntimeListView;
import io.harness.cvng.downtime.beans.DowntimeResponse;
import io.harness.cvng.downtime.entities.Downtime;
import io.harness.cvng.downtime.entities.Downtime.DowntimeKeys;
import io.harness.cvng.downtime.services.api.DowntimeService;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.List;

public class DowntimeServiceImpl implements DowntimeService {
  @Inject private HPersistence hPersistence;
  @Inject private EntityUnavailabilityStatusesService entityUnavailabilityStatusesService;

  @Override
  public DowntimeResponse create(ProjectParams projectParams, DowntimeDTO downtimeDTO) {
    return null;
  }

  @Override
  public DowntimeResponse get(ProjectParams projectParams, String identifier) {
    return null;
  }

  @Override
  public DowntimeResponse update(ProjectParams projectParams, String identifier, DowntimeDTO downtimeDTO) {
    return null;
  }

  @Override
  public boolean delete(ProjectParams projectParams, String identifier) {
    return false;
  }

  @Override
  public PageResponse<DowntimeListView> list(ProjectParams projectParams, Integer offset, Integer pageSize) {
    return null;
  }

  @Override
  public PageResponse<DowntimeHistoryView> history(ProjectParams projectParams, Integer offset, Integer pageSize) {
    return null;
  }

  @Override
  public void deleteByProjectIdentifier(
      Class<Downtime> clazz, String accountId, String orgIdentifier, String projectIdentifier) {
    List<Downtime> downtimes = hPersistence.createQuery(Downtime.class)
                                   .filter(DowntimeKeys.accountId, accountId)
                                   .filter(DowntimeKeys.orgIdentifier, orgIdentifier)
                                   .filter(DowntimeKeys.projectIdentifier, projectIdentifier)
                                   .asList();
    downtimes.forEach(downtime
        -> delete(ProjectParams.builder()
                      .accountIdentifier(downtime.getAccountId())
                      .orgIdentifier(downtime.getOrgIdentifier())
                      .projectIdentifier(downtime.getProjectIdentifier())
                      .build(),
            downtime.getIdentifier()));
  }

  @Override
  public void deleteByOrgIdentifier(Class<Downtime> clazz, String accountId, String orgIdentifier) {
    List<Downtime> downtimes = hPersistence.createQuery(Downtime.class)
                                   .filter(DowntimeKeys.accountId, accountId)
                                   .filter(DowntimeKeys.orgIdentifier, orgIdentifier)
                                   .asList();
    downtimes.forEach(downtime
        -> delete(ProjectParams.builder()
                      .accountIdentifier(downtime.getAccountId())
                      .orgIdentifier(downtime.getOrgIdentifier())
                      .projectIdentifier(downtime.getProjectIdentifier())
                      .build(),
            downtime.getIdentifier()));
  }

  @Override
  public void deleteByAccountIdentifier(Class<Downtime> clazz, String accountId) {
    List<Downtime> downtimes =
        hPersistence.createQuery(Downtime.class).filter(DowntimeKeys.accountId, accountId).asList();
    downtimes.forEach(downtime
        -> delete(ProjectParams.builder()
                      .accountIdentifier(downtime.getAccountId())
                      .orgIdentifier(downtime.getOrgIdentifier())
                      .projectIdentifier(downtime.getProjectIdentifier())
                      .build(),
            downtime.getIdentifier()));
  }
}
