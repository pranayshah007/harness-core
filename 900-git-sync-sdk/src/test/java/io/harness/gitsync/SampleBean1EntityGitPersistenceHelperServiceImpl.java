/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.beans.IdentifierRef;
import io.harness.beans.SampleBean1;
import io.harness.gitsync.entityInfo.GitSdkEntityHandlerInterface;
import io.harness.ng.core.EntityDetail;

import com.google.inject.Singleton;
import java.util.List;
import java.util.function.Supplier;

@Singleton
@OwnedBy(DX)
public class SampleBean1EntityGitPersistenceHelperServiceImpl
    implements GitSdkEntityHandlerInterface<SampleBean1, SampleBean1> {
  @Override
  public Supplier<SampleBean1> getYamlFromEntity(SampleBean1 entity) {
    return null;
  }

  @Override
  public EntityType getEntityType() {
    return EntityType.CONNECTORS;
  }

  @Override
  public Supplier<SampleBean1> getEntityFromYaml(SampleBean1 yaml, String accountIdentifier) {
    return null;
  }

  @Override
  public EntityDetail getEntityDetail(SampleBean1 entity) {
    return EntityDetail.builder()
        .name(entity.getName())
        .entityRef(IdentifierRef.builder()
                       .accountIdentifier(entity.getAccountIdentifier())
                       .identifier(entity.getIdentifier())
                       .orgIdentifier(entity.getOrgIdentifier())
                       .projectIdentifier(entity.getProjectIdentifier())
                       .build())
        .build();
  }

  @Override
  public SampleBean1 save(String accountIdentifier, String yaml) {
    return null;
  }

  @Override
  public SampleBean1 update(String accountIdentifier, String yaml, io.harness.git.model.ChangeType changeType) {
    return null;
  }

  @Override
  public boolean markEntityInvalid(String accountIdentifier, EntityReference entityReference, String erroneousYaml) {
    return true;
  }

  @Override
  public boolean delete(EntityReference entityReference) {
    return false;
  }

  @Override
  public String getObjectIdOfYamlKey() {
    return "objectIdOfYaml";
  }

  @Override
  public String getIsFromDefaultBranchKey() {
    return "isFromDefaultBranch";
  }

  @Override
  public String getYamlGitConfigRefKey() {
    return "yamlGitConfigRef";
  }

  @Override
  public String getUuidKey() {
    return "uuid";
  }

  @Override
  public String getBranchKey() {
    return "branch";
  }

  @Override
  public SampleBean1 upsert(String accountIdentifier, String yaml, String filePath) {
    return null;
  }

  @Override
  public SampleBean1 fullSyncEntity(FullSyncChangeSet fullSyncChangeSet) {
    return null;
  }

  @Override
  public List<FileChange> listAllEntities(ScopeDetails scopeDetails) {
    return null;
  }

  @Override
  public void updateFilePath(String accountIdentifier, String yaml, String prevFilePath, String newFilePath) {}
}
