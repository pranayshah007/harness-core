/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.intfc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.AutoUpgrade;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroupDTO;
import io.harness.delegate.beans.DelegateGroupDetails;
import io.harness.delegate.beans.DelegateGroupListing;
import io.harness.delegate.beans.DelegateGroupTags;
import io.harness.delegate.beans.DelegateListResponse;
import io.harness.delegate.filter.DelegateFilterPropertiesDTO;
import io.harness.ng.beans.PageRequest;

import software.wings.beans.SelectorType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.DEL)
public interface DelegateSetupService {
  long getDelegateGroupCount(String accountId, String orgId, String projectId);

  DelegateGroupListing listDelegateGroupDetails(String accountId, String orgId, String projectId);

  DelegateGroupListing listDelegateGroupDetailsUpTheHierarchy(String accountId, String orgId, String projectId);

  DelegateGroupDetails getDelegateGroupDetails(String accountId, String delegateGroupId);

  DelegateGroupDetails getDelegateGroupDetailsV2(String accountId, String orgId, String projectId, String identifier);

  String getHostNameForGroupedDelegate(String hostname);

  Map<String, SelectorType> retrieveDelegateImplicitSelectors(Delegate delegate, boolean fetchFromCache);

  Map<String, SelectorType> retrieveDelegateGroupImplicitSelectors(DelegateGroup delegateGroup);

  List<Boolean> validateDelegateGroups(String accountId, String orgId, String projectId, List<String> identifiers);

  List<Boolean> validateDelegateConfigurations(
      String accountId, String orgId, String projectId, List<String> identifiers);

  DelegateGroupDetails updateDelegateGroup(
      String accountId, String delegateGroupId, DelegateGroupDetails delegateGroupDetails);

  DelegateGroupDetails updateDelegateGroup(
      String accountId, String orgId, String projectId, String identifier, DelegateGroupDetails delegateGroupDetails);

  DelegateGroupListing listDelegateGroupDetailsV2(String accountId, String orgId, String projectId,
      String filterIdentifier, String searchTerm, DelegateFilterPropertiesDTO delegateFilterPropertiesDTO,
      PageRequest pageRequest);

  List<DelegateListResponse> listDelegates(
      String accountId, String orgId, String projectId, DelegateFilterPropertiesDTO delegateFilterPropertiesDTO);

  DelegateGroupListing listDelegateGroupDetails(
      String accountId, String orgId, String projectId, String delegateTokenName);

  DelegateGroup updateDelegateGroupTags_old(
      String accountId, String orgId, String projectId, String delegateGroupName, Set<String> tags);

  Optional<DelegateGroupDTO> listDelegateGroupTags(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String groupIdentifier);

  Optional<DelegateGroupDTO> addDelegateGroupTags(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String groupIdentifier, DelegateGroupTags delegateGroupTags);

  Optional<DelegateGroupDTO> updateDelegateGroupTags(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String groupIdentifier, DelegateGroupTags delegateGroupTags);

  DelegateGroup getDelegateGroup(String accountId, String delegateGroupId);

  void deleteDelegateGroupsOnDeletingOwner(String accountId, DelegateEntityOwner owner);

  List<DelegateGroupDTO> listDelegateGroupsHavingTags(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, DelegateGroupTags tags);

  List<String> listDelegateImplicitSelectors(Delegate delegate);

  AutoUpgrade setAutoUpgrade(long upgraderLastUpdated, boolean immutableDelegate, long delegateCreationTime,
      String version, String delegateType);

  void updateDelegateGroupValidity(@NotNull String accountId, @NotNull String delegateGroupId);
  long getDelegateExpirationTime(String version, String delegateId);
}
