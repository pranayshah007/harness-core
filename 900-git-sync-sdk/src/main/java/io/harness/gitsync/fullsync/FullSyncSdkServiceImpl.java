/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.fullsync;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.gitsync.FileChange;
import io.harness.gitsync.FileChanges;
import io.harness.gitsync.FullSyncChangeSet;
import io.harness.gitsync.ScopeDetails;
import io.harness.gitsync.common.GitSyncEntityOrderComparatorInMsvc;
import io.harness.gitsync.entityInfo.GitSdkEntityHandlerInterface;
import io.harness.gitsync.persistance.EntityLookupHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(DX)
public class FullSyncSdkServiceImpl implements FullSyncSdkService {
  Map<EntityType, GitSdkEntityHandlerInterface> gitPersistenceHelperServiceMap;
  EntityLookupHelper entityLookupHelper;
  GitSyncEntityOrderComparatorInMsvc gitSyncEntityOrderComparatorInMsvc;

  @Inject
  public FullSyncSdkServiceImpl(Map<EntityType, GitSdkEntityHandlerInterface> gitPersistenceHelperServiceMap,
      GitSyncEntityOrderComparatorInMsvc gitSyncEntityOrderComparatorInMsvc, EntityLookupHelper entityLookupHelper) {
    this.gitPersistenceHelperServiceMap = gitPersistenceHelperServiceMap;
    this.gitSyncEntityOrderComparatorInMsvc = gitSyncEntityOrderComparatorInMsvc;
    this.entityLookupHelper = entityLookupHelper;
  }

  @Override
  public FileChanges getEntitiesForFullSync(ScopeDetails scopeDetails) {
    List<FileChange> fileChangeList = new ArrayList<>();
    invalidateCacheOfGitEnabled(scopeDetails);

    gitPersistenceHelperServiceMap.forEach((entityType, gitPersistenceHelperService) -> {
      final List<FileChange> entitiesForFullSync = gitPersistenceHelperService.listAllEntities(scopeDetails);
      log.info("Entities for full sync for entityType [{}]: [{}]", entityType, entitiesForFullSync);
      fileChangeList.addAll(emptyIfNull(entitiesForFullSync));
    });
    sortFileChanges(fileChangeList);
    return FileChanges.newBuilder().addAllFileChanges(fileChangeList).build();
  }

  private void invalidateCacheOfGitEnabled(ScopeDetails scopeDetails) {
    final EntityScopeInfo entityScope = scopeDetails.getEntityScope();
    entityLookupHelper.updateKey(EntityScopeInfo.newBuilder()
                                     .setOrgId(entityScope.getOrgId())
                                     .setProjectId(entityScope.getProjectId())
                                     .setAccountId(entityScope.getAccountId())
                                     .build());
  }

  @Override
  public void doFullSyncForFile(FullSyncChangeSet fullSyncChangeSet) {
    final EntityDetailProtoDTO entityDetail = fullSyncChangeSet.getEntityDetail();
    final GitSdkEntityHandlerInterface gitSdkEntityHandlerInterface =
        gitPersistenceHelperServiceMap.get(EntityType.fromString(entityDetail.getType().name()));
    gitSdkEntityHandlerInterface.fullSyncEntity(fullSyncChangeSet);
  }

  private void sortFileChanges(List<FileChange> fileChanges) {
    // sorting the files for msv having diff dependent entities on their entityTypes
    fileChanges.sort(new Comparator<FileChange>() {
      @Override
      public int compare(FileChange o1, FileChange o2) {
        return gitSyncEntityOrderComparatorInMsvc.comparator().compare(o1.getEntityDetail(), o2.getEntityDetail());
      }
    });
  }
}
