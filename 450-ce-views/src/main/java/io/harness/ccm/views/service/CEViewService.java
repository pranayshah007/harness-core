/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service;

import io.harness.ccm.views.dto.CEViewShortHand;
import io.harness.ccm.views.dto.DefaultViewIdDto;
import io.harness.ccm.views.dto.LinkedPerspectives;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.CEViewFolder;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewState;
import io.harness.ccm.views.graphql.QLCEView;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface CEViewService {
  CEView save(CEView ceView, boolean clone);
  CEView clone(String accountId, String perspectiveId, String clonePerspectiveName);

  double getActualCostForPerspectiveBudget(String accountId, String perspectiveId);

  CEView get(String uuid);
  CEView update(CEView ceView);
  Set<String> getPerspectiveFolderIds(String accountId, List<String> ceViewIds);
  HashMap<String, String> getPerspectiveIdAndFolderId(String accountId, List<String> ceViewIds);
  void updateBusinessMappingName(String accountId, String buinessMappingUuid, String newBusinessMappingName);
  CEView updateTotalCost(CEView ceView);
  boolean delete(String uuid, String accountId);
  List<QLCEView> getAllViews(String accountId, boolean includeDefault, QLCEViewSortCriteria sortCriteria);
  List<QLCEView> getAllViews(
      String accountId, String folderId, boolean includeDefault, QLCEViewSortCriteria sortCriteria);
  List<CEView> getAllViews(String accountId);
  List<CEViewShortHand> getAllViewsShortHand(String accountId);
  List<CEView> getViewByState(String accountId, ViewState viewState);
  List<LinkedPerspectives> getViewsByBusinessMapping(String accountId, List<String> businessMappingUuids);
  void createDefaultView(String accountId, ViewFieldIdentifier viewFieldIdentifier);
  DefaultViewIdDto getDefaultViewIds(String accountId);

  Double getLastMonthCostForPerspective(String accountId, String perspectiveId);
  Double getForecastCostForPerspective(String accountId, String perspectiveId);

  void updateDefaultClusterViewVisualization(String viewId);
  Map<String, String> getPerspectiveIdToNameMapping(String accountId, List<String> perspectiveIds);

  String getDefaultFolderId(String accountId);
  String getSampleFolderId(String accountId);
  boolean setFolderId(
      CEView ceView, Set<String> allowedFolderIds, List<CEViewFolder> ceViewFolders, String defaultFolderId);
}
