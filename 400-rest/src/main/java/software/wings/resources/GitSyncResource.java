/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.SearchFilter.Operator.EQ;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CONFIG_AS_CODE;
import static software.wings.security.PermissionAttribute.ResourceType.SETTING;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.rest.RestResponse;

import software.wings.beans.GitDetail;
import software.wings.beans.GitFileActivitySummary;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.yaml.GitToHarnessErrorCommitStats;
import software.wings.service.impl.yaml.gitsync.ChangeSetDTO;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.service.intfc.yaml.sync.GitSyncErrorService;
import software.wings.service.intfc.yaml.sync.GitSyncService;
import software.wings.yaml.errorhandling.GitProcessingError;
import software.wings.yaml.errorhandling.GitSyncError;
import software.wings.yaml.errorhandling.GitSyncError.GitSyncErrorKeys;
import software.wings.yaml.gitSync.GitFileActivity;
import software.wings.yaml.gitSync.GitFileActivity.GitFileActivityKeys;
import software.wings.yaml.gitSync.GitFileActivity.Status;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import javax.annotation.Nullable;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by vardanb
 */
@Api("git-sync")
@Path("git-sync")
@Produces(APPLICATION_JSON)
@Scope(SETTING)
@ApiKeyAuthorized(permissionType = LOGGED_IN)
@Slf4j
public class GitSyncResource {
  private GitSyncService gitSyncService;
  private GitSyncErrorService gitSyncErrorService;
  private YamlChangeSetService yamlChangeSetService;

  @Inject
  public GitSyncResource(GitSyncService gitSyncService, GitSyncErrorService gitSyncErrorService,
      YamlChangeSetService yamlChangeSetService) {
    this.gitSyncService = gitSyncService;
    this.gitSyncErrorService = gitSyncErrorService;
    this.yamlChangeSetService = yamlChangeSetService;
  }

  @GET
  @Path("errors/count")
  @Timed
  @ExceptionMetered
  public RestResponse<Long> gitSyncErrorCount(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(gitSyncErrorService.getTotalGitErrorsCount(accountId));
  }

  /**
   * List errors
   *
   * @param pageRequest the page request
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Path("errors")
  public RestResponse<PageResponse<GitSyncError>> listErrors(
      @BeanParam PageRequest<GitSyncError> pageRequest, @QueryParam("accountId") @NotEmpty String accountId) {
    pageRequest.addFilter(GitSyncErrorKeys.accountId, EQ, accountId);
    PageResponse<GitSyncError> pageResponse = gitSyncErrorService.fetchErrors(pageRequest);
    return new RestResponse<>(pageResponse);
  }

  /**
   * List all commits for git to harness errors
   *
   * @param pageRequest the page request
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Path("errors/gitToHarness/commits")
  public RestResponse<PageResponse<GitToHarnessErrorCommitStats>> listGitToHarnessErrorsCommits(
      @BeanParam PageRequest<GitToHarnessErrorCommitStats> pageRequest,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("appId") String appId,
      @QueryParam("numberOfErrorsInSummary") @DefaultValue("0") Integer numberOfErrorsInSummary) {
    pageRequest.addFilter(GitSyncErrorKeys.accountId, EQ, accountId);
    PageResponse<GitToHarnessErrorCommitStats> pageResponse =
        gitSyncErrorService.listGitToHarnessErrorsCommits(pageRequest, accountId, appId, numberOfErrorsInSummary);
    return new RestResponse<>(pageResponse);
  }

  /**
   * List all commits count for git to harness errors
   *
   * @param accountId   the account id
   * @param appId   the appId id
   * @return the rest response
   */
  @GET
  @Path("errors/gitToHarness/commits/count")
  public RestResponse<Integer> listGitToHarnessErrorsCommits(
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("appId") String appId) {
    Integer commitsCount = gitSyncErrorService.getTotalGitCommitsWithErrors(accountId, appId);
    return new RestResponse<>(commitsCount);
  }

  /**
   * List all  git to harness errors for fileView
   *
   * @param pageRequest the page request
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Path("errors/gitToHarness/listAllErrors")
  public RestResponse<PageResponse<GitSyncError>> listGitToHarnessErrors(
      @BeanParam PageRequest<GitSyncError> pageRequest, @QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("appId") String appId, @QueryParam("yamlFilePathPattern") String yamlFilePathPattern) {
    pageRequest.addFilter(GitSyncErrorKeys.accountId, EQ, accountId);
    PageResponse<GitSyncError> pageResponse =
        gitSyncErrorService.listAllGitToHarnessErrors(pageRequest, accountId, appId, yamlFilePathPattern);
    return new RestResponse<>(pageResponse);
  }

  /**
   * List git to harness errors for a particular Commit
   *
   * @param pageRequest the page request
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Path("errors/gitToHarness/{commitId}")
  public RestResponse<PageResponse<GitSyncError>> listGitToHarnessErrorsForACommit(
      @BeanParam PageRequest<GitSyncError> pageRequest, @PathParam("commitId") String commitId,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("appId") String appId,
      @QueryParam("includeData") List<String> includeDataList,
      @QueryParam("yamlFilePathPattern") String yamlFilePathPattern) {
    PageResponse<GitSyncError> pageResponse = gitSyncErrorService.fetchErrorsInEachCommits(
        pageRequest, commitId, accountId, appId, includeDataList, yamlFilePathPattern);
    return new RestResponse<>(pageResponse);
  }

  /**
   * List harness to git errors
   *
   * @param pageRequest the page request
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Path("errors/harnessToGit")
  public RestResponse<PageResponse<GitSyncError>> listHarnessToGitErrors(
      @BeanParam PageRequest<GitSyncError> pageRequest, @QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("appId") String appId) {
    PageResponse<GitSyncError> pageResponse =
        gitSyncErrorService.fetchHarnessToGitErrors(pageRequest, accountId, appId);
    return new RestResponse<>(pageResponse);
  }

  /**
   * List Processing Errors
   *
   * @param pageRequest the page request
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Path("errors/connectivityIssue")
  public RestResponse<PageResponse<GitProcessingError>> listGitConnectivityIssues(
      @BeanParam PageRequest<GitProcessingError> pageRequest, @QueryParam("accountId") @NotEmpty String accountId) {
    PageResponse<GitProcessingError> processingErrors =
        gitSyncErrorService.fetchGitConnectivityIssues(pageRequest, accountId);
    return new RestResponse<>(processingErrors);
  }

  /**
   * List activity
   *
   * @param pageRequest the page request
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Path("activities")
  public RestResponse<PageResponse<GitFileActivity>> listGitFileActivity(
      @BeanParam PageRequest<GitFileActivity> pageRequest, @QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("appId") String appId,
      @QueryParam("activityForFileHistory") @DefaultValue("false") boolean activityForFileHistory) {
    pageRequest.addFilter(GitFileActivityKeys.accountId, EQ, accountId);
    PageResponse<GitFileActivity> pageResponse =
        gitSyncService.fetchGitSyncActivity(pageRequest, accountId, appId, activityForFileHistory);
    return new RestResponse<>(pageResponse);
  }

  /**
   *
   * @param accountId
   * @param errors
   * @return
   */
  @POST
  @Path("errors/_discard")
  @AuthRule(permissionType = PermissionType.MANAGE_CONFIG_AS_CODE)
  @ApiKeyAuthorized(permissionType = MANAGE_CONFIG_AS_CODE)
  public RestResponse discardGitSyncErrorV2(@QueryParam("accountId") String accountId, List<String> errors) {
    gitSyncErrorService.deleteGitSyncErrorAndLogFileActivity(errors, Status.DISCARDED, accountId);
    return RestResponse.Builder.aRestResponse().build();
  }

  /**
   *
   * @param accountId
   * @return
   */
  @GET
  @Path("repos")
  @Timed
  @ExceptionMetered
  public RestResponse<List<GitDetail>> listRepositories(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(gitSyncService.fetchRepositoriesAccessibleToUser(accountId));
  }

  /**
   * List commits
   *
   * @param pageRequest the page request
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Path("commits")
  public RestResponse<PageResponse<GitFileActivitySummary>> listCommits(
      @BeanParam PageRequest<GitFileActivitySummary> pageRequest,
      @QueryParam("gitToharness") @Nullable Boolean gitToHarness, @QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("appId") @NotEmpty String appId) {
    PageResponse<GitFileActivitySummary> pageResponse =
        gitSyncService.fetchGitCommits(pageRequest, gitToHarness, accountId, appId);
    return new RestResponse<>(pageResponse);
  }

  @GET
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  @Path("commits/processing")
  public RestResponse<List<ChangeSetDTO>> listCommitsBeingProcessed(@QueryParam("accountId") String accountId,
      @QueryParam("count") int count, @QueryParam("appId") String appId,
      @QueryParam("gitToHarness") Boolean gitToHarness) {
    List<ChangeSetDTO> changeSetDTOList =
        gitSyncService.getCommitsWhichAreBeingProcessed(accountId, appId, count, gitToHarness);
    return new RestResponse<>(changeSetDTOList);
  }

  @GET
  @Path("queuedCount")
  public RestResponse<Long> getQueueCountByApplication(
      @QueryParam("accountId") String accountId, @QueryParam("appId") @DefaultValue("__GLOBAL_APP_ID__") String appId) {
    Long queueSize = yamlChangeSetService.getItemsInQueueKey(appId, accountId);
    return new RestResponse<>(queueSize);
  }
}
