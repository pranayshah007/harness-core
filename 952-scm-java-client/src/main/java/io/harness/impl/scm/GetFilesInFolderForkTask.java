/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.impl.scm;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.product.ci.scm.proto.ContentType;
import io.harness.product.ci.scm.proto.FileChange;
import io.harness.product.ci.scm.proto.FindFilesInBranchRequest;
import io.harness.product.ci.scm.proto.FindFilesInBranchResponse;
import io.harness.product.ci.scm.proto.PageRequest;
import io.harness.product.ci.scm.proto.Provider;
import io.harness.product.ci.scm.proto.SCMGrpc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * This is a fork and join task which we are using to get all files belonging in
 * the folders.
 */
@Slf4j
@OwnedBy(DX)
public class GetFilesInFolderForkTask extends RecursiveTask<List<FileChange>> {
  SCMGrpc.SCMBlockingStub scmBlockingStub;
  private String folderPath;
  private Provider provider;
  private String ref;
  private String slug;

  @Builder
  public GetFilesInFolderForkTask(
      String folderPath, Provider provider, String ref, String slug, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    this.folderPath = folderPath;
    this.provider = provider;
    this.ref = ref;
    this.slug = slug;
    this.scmBlockingStub = scmBlockingStub;
  }

  /**
   * This is the main function which will be called by all the tasks, every task needs to
   * get files belonging to its folder.
   *
   * This function provides all the files belonging to this folder and then it creates
   * subtasks for every sub folder
   */
  @Override
  protected List<FileChange> compute() {
    List<FileChange> filesList = new ArrayList<>();
    String updatedFolderPath = folderPath.endsWith("/") ? folderPath.substring(0, folderPath.length() - 1) : folderPath;
    FindFilesInBranchRequest.Builder findFilesInBranchRequest = prepareRequestBuilder(updatedFolderPath);

    List<FileChange> filesInBranch = getAllFilesPresentInFolder(findFilesInBranchRequest);
    List<String> newFoldersToBeProcessed = getListOfNewFoldersToBeProcessed(filesInBranch);
    List<GetFilesInFolderForkTask> tasksForSubFolders = createTasksForSubFolders(newFoldersToBeProcessed);
    addFilesOfThisFolder(filesList, filesInBranch);
    addFilesOfSubfolders(filesList, tasksForSubFolders);
    return filesList;
  }

  List<FileChange> getAllFilesPresentInFolder(FindFilesInBranchRequest.Builder findFilesInBranchRequest) {
    FindFilesInBranchResponse filesInBranchResponse = null;
    List<FileChange> allFilesInThisFolder = new ArrayList<>();
    do {
      try {
        filesInBranchResponse = scmBlockingStub.findFilesInBranch(findFilesInBranchRequest.build());
        allFilesInThisFolder.addAll(filesInBranchResponse.getFileList());
        setNextPage(findFilesInBranchRequest, filesInBranchResponse);
      } catch (Exception ex) {
        log.error(
            "Error while getting files from git for the ref {} in slug {} for folder {}", ref, slug, folderPath, ex);
      }
    } while (hasMoreFiles(filesInBranchResponse));
    return allFilesInThisFolder;
  }

  private boolean hasMoreFiles(FindFilesInBranchResponse filesInBranchResponse) {
    return filesInBranchResponse != null && filesInBranchResponse.getPagination() != null
        && (filesInBranchResponse.getPagination().getNext() != 0
            || !"".equals(filesInBranchResponse.getPagination().getNextUrl()));
  }

  private List<GetFilesInFolderForkTask> createTasksForSubFolders(List<String> newFoldersToBeProcessed) {
    List<GetFilesInFolderForkTask> tasks = new ArrayList<>();
    for (String folder : newFoldersToBeProcessed) {
      GetFilesInFolderForkTask task = GetFilesInFolderForkTask.builder()
                                          .ref(ref)
                                          .folderPath(folder)
                                          .provider(provider)
                                          .scmBlockingStub(scmBlockingStub)
                                          .slug(slug)
                                          .build();
      task.fork();
      tasks.add(task);
    }
    return tasks;
  }

  private void addFilesOfSubfolders(List<FileChange> filesList, List<GetFilesInFolderForkTask> tasksForSubFolders) {
    for (GetFilesInFolderForkTask task : tasksForSubFolders) {
      filesList.addAll(task.join());
    }
  }

  private void addFilesOfThisFolder(List<FileChange> filesList, List<FileChange> filesInBranchResponse) {
    List<FileChange> fileChangesInThisFolder = emptyIfNull(filesInBranchResponse)
                                                   .stream()
                                                   .filter(change -> change.getContentType() == ContentType.FILE)
                                                   .collect(toList());
    filesList.addAll(fileChangesInThisFolder);
  }

  private List<String> getListOfNewFoldersToBeProcessed(List<FileChange> filesInBranch) {
    return emptyIfNull(filesInBranch)
        .stream()
        .filter(fileChange -> fileChange.getContentType() == ContentType.DIRECTORY)
        .map(FileChange::getPath)
        .collect(toList());
  }

  public List<FileChange> createForkJoinTask(Set<String> foldersList) {
    List<GetFilesInFolderForkTask> tasks = new ArrayList<>();
    ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
    for (String folder : foldersList) {
      GetFilesInFolderForkTask task = GetFilesInFolderForkTask.builder()
                                          .ref(ref)
                                          .folderPath(folder)
                                          .provider(provider)
                                          .scmBlockingStub(scmBlockingStub)
                                          .slug(slug)
                                          .build();
      forkJoinPool.execute(task);
      tasks.add(task);
    }
    List<FileChange> allFiles = new ArrayList<>();
    for (GetFilesInFolderForkTask task : tasks) {
      List<FileChange> fileChangesList = task.join();

      // Bitbucket onprem server was giving relative path for files, CI-5019
      if (provider.hasBitbucketServer()) {
        fileChangesList = appendFolderPath(fileChangesList, task.folderPath);
      }
      allFiles.addAll(emptyIfNull(fileChangesList));
    }
    return allFiles;
  }

  public List<FileChange> appendFolderPath(List<FileChange> files, String folderPath) {
    if (folderPath.endsWith("/")) {
      folderPath = StringUtils.chop(folderPath);
    }
    List<FileChange> modifiedFiles = new ArrayList<>();
    for (FileChange file : files) {
      FileChange modifiedFile = FileChange.newBuilder(file).setPath(folderPath + "/" + file.getPath()).build();
      modifiedFiles.add(modifiedFile);
    }
    return modifiedFiles;
  }

  private FindFilesInBranchRequest.Builder prepareRequestBuilder(String path) {
    return FindFilesInBranchRequest.newBuilder()
        .setRef(ref)
        .setSlug(slug)
        .setProvider(provider)
        .setPath(path)
        .setPagination(PageRequest.newBuilder().setPage(getInitialPageNum()).build());
  }

  private void setNextPage(FindFilesInBranchRequest.Builder requestBuilder, FindFilesInBranchResponse response) {
    requestBuilder.setPagination(PageRequest.newBuilder().setPage(response.getPagination().getNext()).build());
    if (provider.hasBitbucketCloud()) {
      requestBuilder.setPagination(PageRequest.newBuilder().setUrl(response.getPagination().getNextUrl()).build());
    }
  }

  private int getInitialPageNum() {
    int initialPageNum = 1;
    if (provider.hasBitbucketCloud()) {
      initialPageNum = 0;
    }
    return initialPageNum;
  }
}
