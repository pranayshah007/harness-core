/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.gitsync;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.PmsCommonsTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.manage.GlobalContextManager;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import lombok.Data;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PmsGitSyncHelperTest extends PmsCommonsTestBase {
  @Inject PmsGitSyncHelper pmsGitSyncHelper;
  @Inject KryoSerializer kryoSerializer;

  GitSyncBranchContext gitSyncBranchContext;
  GlobalContext context;
  ByteString contextBytes;

  private static final String pipelineBranch = "master";
  private static final String pipelineRepoID = "be-repo-1";

  @Before
  public void setup() {
    gitSyncBranchContext =
        GitSyncBranchContext.builder()
            .gitBranchInfo(GitEntityInfo.builder().branch(pipelineBranch).yamlGitConfigId(pipelineRepoID).build())
            .build();
    context = new GlobalContext();
    context.setGlobalContextRecord(gitSyncBranchContext);
    GlobalContextManager.set(context);
    contextBytes = ByteString.copyFrom(kryoSerializer.asDeflatedBytes(gitSyncBranchContext));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetEntityGitDetailsFromBytes() {
    EntityGitDetails entityGitDetails = pmsGitSyncHelper.getEntityGitDetailsFromBytes(contextBytes);
    assertThat(entityGitDetails.getBranch()).isEqualTo(pipelineBranch);
    assertThat(entityGitDetails.getRepoIdentifier()).isEqualTo(pipelineRepoID);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetGitSyncBranchContextBytesThreadLocal() {
    ByteString gitSyncBranchContextBytesThreadLocal = pmsGitSyncHelper.getGitSyncBranchContextBytesThreadLocal();
    assertThat(gitSyncBranchContextBytesThreadLocal).isNotEmpty();
    assertThat(gitSyncBranchContextBytesThreadLocal).isEqualTo(contextBytes);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetGitSyncBranchContextBytesThreadLocalWithEntity() {
    ByteString gitSyncBranchContextBytesThreadLocal =
        pmsGitSyncHelper.getGitSyncBranchContextBytesThreadLocal(new DummyGitSyncableEntity(), null, null, null);
    assertThat(gitSyncBranchContextBytesThreadLocal).isNotEmpty();
    GitSyncBranchContext newGitSyncBranchContext =
        pmsGitSyncHelper.deserializeGitSyncBranchContext(gitSyncBranchContextBytesThreadLocal);
    assertThat(newGitSyncBranchContext.getGitBranchInfo().getFilePath()).isEqualTo("file.yml");
    assertThat(newGitSyncBranchContext.getGitBranchInfo().getFolderPath()).isEqualTo(".harness");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetGitSyncBranchContextBytesThreadLocalWithEntityWithStoreType() {
    GitSyncBranchContext gitSyncBranchContext =
        GitSyncBranchContext.builder().gitBranchInfo(GitEntityInfo.builder().branch(pipelineBranch).build()).build();
    context.setGlobalContextRecord(gitSyncBranchContext);
    ByteString gitSyncBranchContextBytesThreadLocal = pmsGitSyncHelper.getGitSyncBranchContextBytesThreadLocal(
        new DummyGitSyncableEntityButGitAware(), StoreType.REMOTE, "repoName", null);
    assertThat(gitSyncBranchContextBytesThreadLocal).isNotEmpty();
    GitSyncBranchContext newGitSyncBranchContext =
        pmsGitSyncHelper.deserializeGitSyncBranchContext(gitSyncBranchContextBytesThreadLocal);
    assertThat(newGitSyncBranchContext.getGitBranchInfo().getFilePath()).isEqualTo("file.yml");
    assertThat(newGitSyncBranchContext.getGitBranchInfo().getRepoName()).isEqualTo("repoName");
    assertThat(newGitSyncBranchContext.getGitBranchInfo().getBranch()).isEqualTo(pipelineBranch);
    assertThat(newGitSyncBranchContext.getGitBranchInfo().getYamlGitConfigId()).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeserializeGitSyncBranchContext() {
    assertThat(pmsGitSyncHelper.deserializeGitSyncBranchContext(null)).isNull();

    GitSyncBranchContext gitSyncBranchContext = pmsGitSyncHelper.deserializeGitSyncBranchContext(contextBytes);
    assertThat(gitSyncBranchContext.getGitBranchInfo().getBranch()).isEqualTo(pipelineBranch);
    assertThat(gitSyncBranchContext.getGitBranchInfo().getYamlGitConfigId()).isEqualTo(pipelineRepoID);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSerializeGitSyncBranchContext() {
    assertThat(pmsGitSyncHelper.serializeGitSyncBranchContext(null)).isNull();

    ByteString byteString =
        pmsGitSyncHelper.serializeGitSyncBranchContext(context.get(GitSyncBranchContext.NG_GIT_SYNC_CONTEXT));
    assertThat(byteString).isEqualTo(contextBytes);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCreateGitSyncBranchContextGuard() {
    validateGuard(pmsGitSyncHelper.createGitSyncBranchContextGuard(
                      Ambiance.newBuilder()
                          .setMetadata(ExecutionMetadata.newBuilder().setGitSyncBranchContext(contextBytes).build())
                          .build(),
                      true),
        true);
    validateGuard(pmsGitSyncHelper.createGitSyncBranchContextGuard(
                      Ambiance.newBuilder()
                          .setMetadata(ExecutionMetadata.newBuilder().setGitSyncBranchContext(contextBytes).build())
                          .build(),
                      false),
        false);
  }

  private void validateGuard(PmsGitSyncBranchContextGuard guard, boolean findDefaultFromOtherBranches) {
    try (PmsGitSyncBranchContextGuard ignored = guard) {
      GitSyncBranchContext branchContext = GlobalContextManager.get(GitSyncBranchContext.NG_GIT_SYNC_CONTEXT);
      assertThat(branchContext).isNotNull();
      assertThat(branchContext.getGitBranchInfo().getBranch()).isEqualTo(pipelineBranch);
      assertThat(branchContext.getGitBranchInfo().getYamlGitConfigId()).isEqualTo(pipelineRepoID);
      assertThat(branchContext.getGitBranchInfo().isFindDefaultFromOtherRepos())
          .isEqualTo(findDefaultFromOtherBranches);
    }
  }

  @Data
  public static class DummyGitSyncableEntity implements GitSyncableEntity {
    String uuid = "uuid";
    String accountIdentifier = "aid";
    String orgIdentifier = "oid";
    String projectIdentifier = "pid";
    String identifier = "id";
    String objectIdOfYaml = "oid";
    Boolean isFromDefaultBranch = true;
    String branch = pipelineBranch;
    String yamlGitConfigRef = pipelineRepoID;
    String filePath = "file.yml";
    String rootFolder = ".harness";
    String yaml = null;
    boolean isEntityInvalid = false;

    @Override
    public String getInvalidYamlString() {
      return yaml;
    }
  }

  @Data
  public static class DummyGitSyncableEntityButGitAware implements GitSyncableEntity {
    String uuid = "uuid";
    String accountIdentifier = "aid";
    String orgIdentifier = "oid";
    String projectIdentifier = "pid";
    String identifier = "id";
    String objectIdOfYaml = null;
    Boolean isFromDefaultBranch = false;
    String branch = null;
    String yamlGitConfigRef = null;
    String filePath = "file.yml";
    String rootFolder = null;
    String yaml = null;
    boolean isEntityInvalid = false;

    @Override
    public String getInvalidYamlString() {
      return yaml;
    }
  }
}
