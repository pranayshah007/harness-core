/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.dao;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.FileProcessingResponse;
import io.harness.gitsync.FileProcessingStatus;
import io.harness.gitsync.GitToHarnessInfo;
import io.harness.gitsync.GitToHarnessProcessRequest;
import io.harness.gitsync.beans.GitProcessRequest;
import io.harness.gitsync.beans.GitProcessRequest.GitProcessingRequestKeys;
import io.harness.gitsync.common.beans.FileProcessStatus;
import io.harness.gitsync.common.beans.FileStatus;
import io.harness.gitsync.common.beans.FileStatus.FileStatusKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.StringValue;
import com.mongodb.client.result.UpdateResult;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(DX)
@Slf4j
public class GitProcessingRequestServiceImpl implements GitProcessingRequestService {
  private final MongoTemplate mongoTemplate;

  @Override
  public Map<String, FileProcessingResponse> upsert(GitToHarnessProcessRequest gitToHarnessProcessRequest) {
    final GitToHarnessInfo gitToHarnessBranchInfo = gitToHarnessProcessRequest.getGitToHarnessBranchInfo();
    final GitProcessRequest processRequest =
        get(gitToHarnessProcessRequest.getCommitId().getValue(), gitToHarnessProcessRequest.getAccountId(),
            gitToHarnessBranchInfo.getRepoUrl(), gitToHarnessBranchInfo.getBranch());

    if (processRequest != null) {
      log.info("Processing request for existing commit id file Statuses {}.", processRequest.getFileStatuses());
      return getFileProcessingResponse(processRequest.getFileStatuses());
    }

    final List<FileStatus> fileProcessingResponseMap = getFileProcessingResponseMap(gitToHarnessProcessRequest);

    final GitProcessRequest gitProcessRequest = GitProcessRequest.builder()
                                                    .repoUrl(gitToHarnessBranchInfo.getRepoUrl())
                                                    .branch(gitToHarnessBranchInfo.getBranch())
                                                    .accountId(gitToHarnessProcessRequest.getAccountId())
                                                    .fileStatuses(fileProcessingResponseMap)
                                                    .commitId(gitToHarnessProcessRequest.getCommitId().getValue())
                                                    .build();
    mongoTemplate.save(gitProcessRequest);
    log.info("Processing request received for new commit id file statuses map {}.", fileProcessingResponseMap);
    return getFileProcessingResponse(fileProcessingResponseMap);
  }

  private List<FileStatus> getFileProcessingResponseMap(@Valid GitToHarnessProcessRequest gitToHarnessProcessRequest) {
    if (gitToHarnessProcessRequest.getChangeSets() == null
        || isEmpty(gitToHarnessProcessRequest.getChangeSets().getChangeSetList())) {
      return Collections.emptyList();
    }
    return gitToHarnessProcessRequest.getChangeSets()
        .getChangeSetList()
        .stream()
        .map(changeSet
            -> FileStatus.builder()
                   .status(FileProcessStatus.UNPROCESSED)
                   .entityType(changeSet.getEntityType().name())
                   .changeType(changeSet.getChangeType().name())
                   .filePath(changeSet.getFilePath())
                   .fileContent(changeSet.getYaml())
                   .build())
        .collect(Collectors.toList());
  }

  @Override
  public UpdateResult updateFileStatus(
      String commitId, String filePath, FileProcessingStatus status, String errorMsg, String accountId) {
    final Criteria criteria = Criteria.where(GitProcessingRequestKeys.commitId)
                                  .is(commitId)
                                  .and(GitProcessingRequestKeys.accountId)
                                  .is(accountId)
                                  .and(GitProcessingRequestKeys.fileStatuses + "." + FileStatusKeys.filePath)
                                  .is(filePath);

    final Update update =
        new Update()
            .set(GitProcessingRequestKeys.fileStatuses + ".$." + FileStatusKeys.errorMessage, errorMsg)
            .set(GitProcessingRequestKeys.fileStatuses + ".$." + FileStatusKeys.status, getPojoStatus(status));
    return mongoTemplate.updateFirst(query(criteria), update, GitProcessRequest.class);
  }

  @Override
  public GitProcessRequest get(String commitId, String accountId, String repo, String branch) {
    final Criteria criteria = Criteria.where(GitProcessingRequestKeys.branch)
                                  .is(branch)
                                  .and(GitProcessingRequestKeys.repoUrl)
                                  .is(repo)
                                  .and(GitProcessingRequestKeys.commitId)
                                  .is(commitId)
                                  .and(GitProcessingRequestKeys.accountId)
                                  .is(accountId);
    return mongoTemplate.findOne(query(criteria), GitProcessRequest.class);
  }

  @Override
  public boolean deleteByAccount(String accountId) {
    Criteria criteria = new Criteria();
    criteria.and(GitProcessingRequestKeys.accountId).is(accountId);
    mongoTemplate.remove(new Query(criteria), GitProcessRequest.class);
    return true;
  }

  private Map<String, FileProcessingResponse> getFileProcessingResponse(List<FileStatus> fileStatuses) {
    Map<String, FileProcessingResponse> responseMap = new HashMap<>();
    fileStatuses.forEach(fileStatus -> {
      final FileProcessingStatus status = getProtoStatus(fileStatus.getStatus());
      final String errorMsg = fileStatus.getErrorMessage();
      final FileProcessingResponse.Builder responseBuilder =
          FileProcessingResponse.newBuilder().setStatus(status).setFilePath(fileStatus.getFilePath());
      if (isNotEmpty(errorMsg)) {
        responseBuilder.setErrorMsg(StringValue.of(errorMsg));
      }
      responseMap.put(fileStatus.getFilePath(), responseBuilder.build());
    });
    return responseMap;
  }

  private FileProcessingStatus getProtoStatus(FileProcessStatus fileProcessStatus) {
    return FileProcessingStatus.valueOf(fileProcessStatus.name());
  }

  private FileProcessStatus getPojoStatus(FileProcessingStatus fileProcessStatus) {
    return FileProcessStatus.valueOf(fileProcessStatus.name());
  }
}
