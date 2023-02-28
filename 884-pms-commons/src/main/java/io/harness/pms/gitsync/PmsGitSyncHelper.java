/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.gitsync;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.gitsync.scm.beans.ScmGitMetaDataContext;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.manage.GlobalContextManager;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PmsGitSyncHelper {
  @Inject private KryoSerializer kryoSerializer;

  public EntityGitDetails getEntityGitDetailsFromBytes(ByteString gitSyncBranchContextBytes) {
    GitSyncBranchContext gitSyncBranchContext = deserializeGitSyncBranchContext(gitSyncBranchContextBytes);
    if (gitSyncBranchContext == null) {
      return null;
    }
    return gitSyncBranchContext.toEntityGitDetails();
  }

  public ByteString getGitSyncBranchContextBytesThreadLocal(
      GitSyncableEntity gitSyncableEntity, StoreType storeType, String repoName, String connectorRef) {
    GitSyncBranchContext gitSyncBranchContext = GlobalContextManager.get(GitSyncBranchContext.NG_GIT_SYNC_CONTEXT);
    ScmGitMetaDataContext gitMetaDataContext = GlobalContextManager.get(ScmGitMetaDataContext.NG_GIT_SYNC_CONTEXT);
    String branchNameFromSCMGitMetadata =
        gitMetaDataContext == null ? null : gitMetaDataContext.getScmGitMetaData().getBranchName();
    if (gitSyncBranchContext != null && gitSyncBranchContext.getGitBranchInfo() != null) {
      gitSyncBranchContext.getGitBranchInfo().setFilePath(gitSyncableEntity.getFilePath());
      gitSyncBranchContext.getGitBranchInfo().setFolderPath(gitSyncableEntity.getRootFolder());
      gitSyncBranchContext.getGitBranchInfo().setStoreType(storeType);
      gitSyncBranchContext.getGitBranchInfo().setRepoName(repoName);
      // ParentEntity info is added for Parent Pipeline in Pipeline Chaining. This will be required to fetch branch of
      // chained pipeline
      gitSyncBranchContext.getGitBranchInfo().setParentEntityRepoName(repoName);
      gitSyncBranchContext.getGitBranchInfo().setParentEntityConnectorRef(connectorRef);
      if (EmptyPredicate.isNotEmpty(branchNameFromSCMGitMetadata)) {
        // if API request does not have a branch, but the pipeline is of type remote, the git sdk figures out the
        // default branch and fills it in the SCM Git Metadata
        gitSyncBranchContext.getGitBranchInfo().setBranch(branchNameFromSCMGitMetadata);
      }
    }
    return serializeGitSyncBranchContext(gitSyncBranchContext);
  }

  public ByteString getGitSyncBranchContextBytesThreadLocal() {
    GitSyncBranchContext gitSyncBranchContext = GlobalContextManager.get(GitSyncBranchContext.NG_GIT_SYNC_CONTEXT);
    return serializeGitSyncBranchContext(gitSyncBranchContext);
  }

  public PmsGitSyncBranchContextGuard createGitSyncBranchContextGuard(
      Ambiance ambiance, boolean findDefaultFromOtherBranches) {
    return createGitSyncBranchContextGuardFromBytes(
        ambiance.getMetadata().getGitSyncBranchContext(), findDefaultFromOtherBranches);
  }

  public PmsGitSyncBranchContextGuard createGitSyncBranchContextGuardFromBytes(
      ByteString gitSyncBranchContextBytes, boolean findDefaultFromOtherBranches) {
    return new PmsGitSyncBranchContextGuard(
        deserializeGitSyncBranchContext(gitSyncBranchContextBytes), findDefaultFromOtherBranches);
  }

  public GitSyncBranchContext deserializeGitSyncBranchContext(ByteString byteString) {
    if (isEmpty(byteString)) {
      return null;
    }
    byte[] bytes = byteString.toByteArray();
    return isEmpty(bytes) ? null : (GitSyncBranchContext) kryoSerializer.asInflatedObject(bytes);
  }

  public ByteString serializeGitSyncBranchContext(GitSyncBranchContext gitSyncBranchContext) {
    if (gitSyncBranchContext == null) {
      return null;
    }
    return ByteString.copyFrom(kryoSerializer.asDeflatedBytes(gitSyncBranchContext));
  }
}
