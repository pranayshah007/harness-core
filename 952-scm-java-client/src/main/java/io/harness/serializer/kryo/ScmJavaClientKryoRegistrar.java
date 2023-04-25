/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CommitDetails;
import io.harness.beans.ContentType;
import io.harness.beans.FileGitDetails;
import io.harness.beans.GetBatchFileRequestIdentifier;
import io.harness.beans.HookEventType;
import io.harness.beans.Repository;
import io.harness.beans.Scope;
import io.harness.beans.WebhookGitUser;
import io.harness.beans.gitsync.GitFileDetails;
import io.harness.beans.gitsync.GitFilePathDetails;
import io.harness.beans.gitsync.GitPRCreateRequest;
import io.harness.beans.gitsync.GitWebhookDetails;
import io.harness.beans.response.GitFileResponse;
import io.harness.beans.response.ListFilesInCommitResponse;
import io.harness.exception.ScmException;
import io.harness.gitsync.common.dtos.gitAccess.AzureRepoAccessDTO;
import io.harness.gitsync.common.dtos.gitAccess.GitAccessDTO;
import io.harness.gitsync.common.dtos.gitAccess.GithubAccessDTO;
import io.harness.gitsync.common.dtos.gitAccess.GithubAccessTokenDTO;
import io.harness.gitsync.common.dtos.gitAccess.GithubAppAccessDTO;
import io.harness.gitsync.common.dtos.gitAccess.GitlabAccessDTO;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.CreateWebhookResponse;
import io.harness.product.ci.scm.proto.DeleteFileResponse;
import io.harness.product.ci.scm.proto.DeleteWebhookResponse;
import io.harness.product.ci.scm.proto.ListWebhooksResponse;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(DX)
public class ScmJavaClientKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(CommitDetails.class, 955000);
    kryo.register(Repository.class, 955001);
    kryo.register(WebhookGitUser.class, 955002);
    kryo.register(GitFileDetails.class, 955003);
    kryo.register(CreateFileResponse.class, 955004);
    kryo.register(UpdateFileResponse.class, 955005);
    kryo.register(DeleteFileResponse.class, 955006);
    kryo.register(ScmException.class, 955007);
    kryo.register(GitPRCreateRequest.class, 955008);
    kryo.register(CreatePRResponse.class, 955009);
    kryo.register(CreateWebhookResponse.class, 955010);
    kryo.register(DeleteWebhookResponse.class, 955011);
    kryo.register(ListWebhooksResponse.class, 955012);
    kryo.register(GitWebhookDetails.class, 955013);
    kryo.register(HookEventType.class, 955014);
    kryo.register(GitFilePathDetails.class, 955015);
    kryo.register(GitFileResponse.class, 955016);
    kryo.register(ListFilesInCommitResponse.class, 955017);
    kryo.register(FileGitDetails.class, 955018);
    kryo.register(ContentType.class, 955019);
    kryo.register(GetBatchFileRequestIdentifier.class, 955021);
    kryo.register(GitAccessDTO.class, 955022);
    kryo.register(GithubAccessDTO.class, 955023);
    kryo.register(GitlabAccessDTO.class, 955024);
    kryo.register(GithubAccessTokenDTO.class, 955025);
    kryo.register(AzureRepoAccessDTO.class, 955026);
    kryo.register(GithubAppAccessDTO.class, 955027);
    kryo.register(Scope.class, 955028);
  }
}
