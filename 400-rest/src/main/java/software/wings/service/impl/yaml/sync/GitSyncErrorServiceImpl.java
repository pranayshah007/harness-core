/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.sync;

import static io.harness.beans.PageRequest.DEFAULT_UNLIMITED;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.beans.SortOrder.OrderType.DESC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.CreatedAtAware.CREATED_AT_KEY;

import static software.wings.alerts.AlertStatus.Open;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.EntityType.APPLICATION;
import static software.wings.beans.template.Template.APP_ID_KEY;
import static software.wings.beans.yaml.Change.Builder.aFileChange;
import static software.wings.beans.yaml.YamlConstants.APPLICATIONS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.beans.yaml.YamlConstants.SETUP_FOLDER;
import static software.wings.service.impl.yaml.sync.GitSyncErrorUtils.getCommitIdOfError;
import static software.wings.yaml.errorhandling.GitSyncError.GitSyncDirection.GIT_TO_HARNESS;
import static software.wings.yaml.errorhandling.GitSyncError.GitSyncDirection.HARNESS_TO_GIT;

import static dev.morphia.aggregation.Accumulator.accumulator;
import static dev.morphia.aggregation.Group.first;
import static dev.morphia.aggregation.Group.grouping;
import static dev.morphia.aggregation.Projection.projection;
import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.git.model.ChangeType;
import io.harness.persistence.CreatedAtAware;

import software.wings.beans.GitConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.User;
import software.wings.beans.alert.Alert;
import software.wings.beans.alert.Alert.AlertKeys;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.GitConnectionErrorAlert;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.GitFileChange;
import software.wings.dl.WingsPersistence;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.yaml.GitSyncErrorStatus;
import software.wings.service.impl.yaml.GitToHarnessErrorCommitStats;
import software.wings.service.impl.yaml.GitToHarnessErrorCommitStats.GitToHarnessErrorCommitStatsKeys;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.sync.GitSyncErrorService;
import software.wings.service.intfc.yaml.sync.GitSyncService;
import software.wings.service.intfc.yaml.sync.YamlGitConfigService;
import software.wings.service.intfc.yaml.sync.YamlService;
import software.wings.utils.AlertsUtils;
import software.wings.utils.Utils;
import software.wings.yaml.errorhandling.GitProcessingError;
import software.wings.yaml.errorhandling.GitSyncError;
import software.wings.yaml.errorhandling.GitSyncError.GitSyncErrorKeys;
import software.wings.yaml.errorhandling.GitToHarnessErrorDetails;
import software.wings.yaml.errorhandling.HarnessToGitErrorDetails;
import software.wings.yaml.gitSync.GitFileActivity;
import software.wings.yaml.gitSync.beans.YamlGitConfig;
import software.wings.yaml.gitSync.beans.YamlGitConfig.YamlGitConfigKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.AggregationOptions;
import dev.morphia.aggregation.AggregationPipeline;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import io.fabric8.utils.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
public class GitSyncErrorServiceImpl implements GitSyncErrorService {
  public static final String EMPTY_STR = "";
  public static final Long DEFAULT_COMMIT_TIME = 0L;

  private static final EnumSet<GitFileActivity.Status> TERMINATING_STATUSES =
      EnumSet.of(GitFileActivity.Status.EXPIRED, GitFileActivity.Status.DISCARDED);

  private WingsPersistence wingsPersistence;
  private YamlGitService yamlGitService;
  private YamlService yamlService;
  private GitSyncService gitSyncService;
  private AlertsUtils alertsUtils;
  private GitConfigHelperService gitConfigHelperService;
  private AuthService authService;
  private YamlGitConfigService yamlGitConfigService;
  private GitSyncRBACHelper gitSyncRBACHelper;

  @Inject
  public GitSyncErrorServiceImpl(WingsPersistence wingsPersistence, YamlGitService yamlGitService,
      YamlService yamlService, GitSyncService gitSyncService, AlertsUtils alertsUtils, AuthService authService,
      YamlGitConfigService yamlGitConfigService, GitConfigHelperService gitConfigHelperService,
      GitSyncRBACHelper gitSyncRBACHelper) {
    this.wingsPersistence = wingsPersistence;
    this.yamlGitService = yamlGitService;
    this.yamlService = yamlService;
    this.gitSyncService = gitSyncService;
    this.alertsUtils = alertsUtils;
    this.gitConfigHelperService = gitConfigHelperService;
    this.authService = authService;
    this.yamlGitConfigService = yamlGitConfigService;
    this.gitSyncRBACHelper = gitSyncRBACHelper;
  }

  private Query<GitSyncError> addGitToHarnessErrorFilter(Query<GitSyncError> gitSyncErrorQuery) {
    return gitSyncErrorQuery.filter(GitSyncErrorKeys.gitSyncDirection, GIT_TO_HARNESS.name());
  }

  private Query<GitSyncError> addHarnessToGitErrorFilter(Query<GitSyncError> gitSyncErrorQuery) {
    return gitSyncErrorQuery.filter(GitSyncErrorKeys.gitSyncDirection, HARNESS_TO_GIT.name());
  }

  @Override
  public long getTotalGitErrorsCount(String accountId) {
    List<Alert> alerts = getGitConnectionAlert(accountId);
    long connectivityIssueCount = alerts == null ? 0 : alerts.size();
    return getGitSyncErrorCount(accountId, true) + connectivityIssueCount;
  }

  @Override
  public long getGitSyncErrorCount(String accountId, boolean followRBAC) {
    Query<GitSyncError> query =
        wingsPersistence.createQuery(GitSyncError.class).filter(GitSyncError.ACCOUNT_ID_KEY2, accountId);
    if (followRBAC) {
      Boolean userHasAtleastOneGitConfigPerm = addAppFilterAndReturnTrueIfUserHasAnyAppAccess(query, null, accountId);
      if (!userHasAtleastOneGitConfigPerm) {
        return 0;
      }
    }
    return query.count();
  }

  private Boolean noRepoFilterGiven(String appId) {
    return isBlank(appId);
  }

  private Boolean userHasAllAppAccess(String accountId) {
    User user = UserThreadLocal.get();
    if (user == null) {
      throw new UnexpectedException("No user variable is not set in the thread");
    }
    UserPermissionInfo userPermissionInfo = authService.getUserPermissionInfo(accountId, user, false);
    return userPermissionInfo.isHasAllAppAccess();
  }

  private <T> void populateAppFilterForGitSyncErrors(Query<T> query, Set<String> appIds, String accountId) {
    if (userHasAllAppAccess(accountId)) {
      return;
    }
    query.field(APP_ID_KEY).in(appIds);
  }

  private <T> Boolean addAppFilterAndReturnTrueIfUserHasAnyAppAccess(Query<T> query, String appId, String accountId) {
    if (noRepoFilterGiven(appId)) {
      return addAppsAllowedToUserAndReturnTrueIfUserHasAnyAppAccess(query, accountId);
    } else {
      query.filter(APP_ID_KEY, appId);
    }
    return true;
  }

  private <T> Boolean addAppsAllowedToUserAndReturnTrueIfUserHasAnyAppAccess(Query<T> query, String accountId) {
    List<YamlGitConfig> yamlGitConfigs = yamlGitConfigService.getYamlGitConfigAccessibleToUserWithEntityName(accountId);
    if (isEmpty(yamlGitConfigs)) {
      // User doesn't has access to any yamlGitConfig
      return false;
    } else {
      Set<String> appIdsAccessibleToUser =
          yamlGitConfigs.stream().map(YamlGitConfig::getAppId).collect(Collectors.toSet());
      populateAppFilterForGitSyncErrors(query, appIdsAccessibleToUser, accountId);
    }
    return true;
  }

  @Override
  public PageResponse<GitToHarnessErrorCommitStats> listGitToHarnessErrorsCommits(
      PageRequest<GitToHarnessErrorCommitStats> req, String accountId, String appId, Integer numberOfErrorsInSummary) {
    // Creating a page request to get the commits
    int limit = isBlank(req.getLimit()) ? DEFAULT_UNLIMITED : Integer.parseInt(req.getLimit());
    int offset = isBlank(req.getOffset()) ? 0 : Integer.parseInt(req.getOffset());

    final Query<GitSyncError> query =
        wingsPersistence.createQuery(GitSyncError.class).filter(GitSyncErrorKeys.accountId, accountId);

    Boolean userHasAtleastOneGitConfigAccess = addAppFilterAndReturnTrueIfUserHasAnyAppAccess(query, appId, accountId);
    if (!userHasAtleastOneGitConfigAccess) {
      return aPageResponse().withTotal(0).build();
    }

    addGitToHarnessErrorFilter(query);

    List<GitToHarnessErrorCommitStats> commitWiseErrorDetails = new ArrayList<>();
    wingsPersistence.getDatastore(GitSyncError.class)
        .createAggregation(GitSyncError.class)
        .match(query)
        .unwind(GitSyncErrorKeys.additionalErrorDetails)
        .group(GitSyncErrorKeys.gitCommitId, grouping("failedCount", accumulator("$sum", 1)),
            grouping(GitToHarnessErrorCommitStatsKeys.commitTime, first(GitSyncErrorKeys.commitTime)),
            grouping(GitSyncErrorKeys.gitConnectorId, first(GitSyncErrorKeys.gitConnectorId)),
            grouping(GitSyncErrorKeys.repositoryName, first(GitSyncErrorKeys.repositoryName)),
            grouping(GitSyncErrorKeys.branchName, first(GitSyncErrorKeys.branchName)),
            grouping(GitToHarnessErrorCommitStatsKeys.commitMessage, first(GitSyncErrorKeys.commitMessage)),
            grouping(GitToHarnessErrorCommitStatsKeys.errorsForSummaryView,
                grouping("$push", projection(GitSyncErrorKeys.yamlFilePath, GitSyncErrorKeys.yamlFilePath),
                    projection(GitSyncErrorKeys.failureReason, GitSyncErrorKeys.failureReason),
                    projection(APP_ID_KEY, APP_ID_KEY))))
        .project(projection("gitCommitId", "_id"), projection("failedCount"),
            projection(GitToHarnessErrorCommitStatsKeys.commitTime), projection(GitSyncErrorKeys.gitConnectorId),
            projection(GitSyncErrorKeys.repositoryName), projection(GitSyncErrorKeys.branchName),
            projection(GitToHarnessErrorCommitStatsKeys.commitMessage),
            projection(GitToHarnessErrorCommitStatsKeys.errorsForSummaryView))
        .sort(Sort.descending(GitToHarnessErrorCommitStatsKeys.commitTime))
        .skip(offset)
        .limit(limit)
        .aggregate(GitToHarnessErrorCommitStats.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(GitSyncError.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(commitWiseErrorDetails::add);

    List<GitToHarnessErrorCommitStats> gitDetailsAfterRemovingNewAppCommits =
        removeNewAppCommitsInCaseOfSetupFilter(commitWiseErrorDetails, appId);
    List<GitToHarnessErrorCommitStats> filteredCommitDetails =
        filterCommitsWithValidConnectorAndPopulateConnectorName(gitDetailsAfterRemovingNewAppCommits, accountId);
    removeExtraErrorsFromCommitDetails(filteredCommitDetails, numberOfErrorsInSummary);
    return aPageResponse()
        .withTotal(filteredCommitDetails.size())
        .withLimit(String.valueOf(limit))
        .withOffset(String.valueOf(offset))
        .withResponse(filteredCommitDetails)
        .build();
  }

  @Override
  public Integer getTotalGitCommitsWithErrors(String accountId, String appId) {
    final Query<GitSyncError> query =
        wingsPersistence.createQuery(GitSyncError.class).filter(GitSyncErrorKeys.accountId, accountId);

    Boolean userHasAtleastOneGitConfigAccess = addAppFilterAndReturnTrueIfUserHasAnyAppAccess(query, appId, accountId);
    if (!userHasAtleastOneGitConfigAccess) {
      return 0;
    }

    addGitToHarnessErrorFilter(query);
    List<GitToHarnessErrorCommitStats> commitsDetails = new ArrayList<>();
    AggregationPipeline aggregationPipeline =
        wingsPersistence.getDatastore(GitSyncError.class)
            .createAggregation(GitSyncError.class)
            .match(query)
            .group(GitSyncErrorKeys.gitCommitId,
                grouping(GitToHarnessErrorCommitStatsKeys.errorsForSummaryView,
                    grouping("$push", projection(GitSyncErrorKeys.yamlFilePath, GitSyncErrorKeys.yamlFilePath),
                        projection(APP_ID_KEY, APP_ID_KEY))))
            .project(
                projection("gitCommitId", "_id"), projection(GitToHarnessErrorCommitStatsKeys.errorsForSummaryView));
    int limit = wingsPersistence.getMaxDocumentLimit(GitSyncError.class);
    if (limit > 0) {
      aggregationPipeline.limit(limit);
    }
    aggregationPipeline
        .aggregate(GitToHarnessErrorCommitStats.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(GitSyncError.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(commit -> commitsDetails.add(commit));
    List<GitToHarnessErrorCommitStats> actualCommitsForThatApp =
        removeNewAppCommitsInCaseOfSetupFilter(commitsDetails, appId);
    return isEmpty(actualCommitsForThatApp) ? 0 : actualCommitsForThatApp.size();
  }

  private List<GitToHarnessErrorCommitStats> removeNewAppCommitsInCaseOfSetupFilter(
      List<GitToHarnessErrorCommitStats> gitCommitsDetails, String appId) {
    if (isEmpty(gitCommitsDetails) || !GLOBAL_APP_ID.equals(appId)) {
      return new ArrayList<>(gitCommitsDetails);
    }

    return gitCommitsDetails.stream()
        .map(commitDetail -> removeNewAppError(commitDetail))
        .filter(commitDetail -> isNotEmpty(commitDetail.getErrorsForSummaryView()))
        .collect(toList());
  }

  private GitToHarnessErrorCommitStats removeNewAppError(GitToHarnessErrorCommitStats commitDetail) {
    List<GitSyncError> gitSyncErrors = commitDetail.getErrorsForSummaryView();
    // We will remove the new app Error from here
    if (isEmpty(gitSyncErrors)) {
      return commitDetail;
    }
    List<GitSyncError> gitSyncErrorsForAccountLevel =
        gitSyncErrors.stream().filter(error -> !isNewAppError(error)).collect(toList());
    commitDetail.setErrorsForSummaryView(gitSyncErrorsForAccountLevel);
    commitDetail.setFailedCount(emptyIfNull(gitSyncErrorsForAccountLevel).size());
    return commitDetail;
  }

  private void removeExtraErrorsFromCommitDetails(
      List<GitToHarnessErrorCommitStats> commitDetails, Integer numberOfErrorsInSummary) {
    if (numberOfErrorsInSummary == null || numberOfErrorsInSummary == 0) {
      return;
    }

    for (GitToHarnessErrorCommitStats commitDetail : commitDetails) {
      List<GitSyncError> allErrors = commitDetail.getErrorsForSummaryView();
      if (isNotEmpty(allErrors) && allErrors.size() > numberOfErrorsInSummary) {
        sortErrorsByFileProcessingOrder(allErrors);
        commitDetail.setErrorsForSummaryView(allErrors.subList(0, numberOfErrorsInSummary));
      }
    }
  }

  private List<GitToHarnessErrorCommitStats> filterCommitsWithValidConnectorAndPopulateConnectorName(
      List<GitToHarnessErrorCommitStats> commitWiseErrorDetails, String accountId) {
    if (isEmpty(commitWiseErrorDetails)) {
      return Collections.emptyList();
    }
    List<String> connectorIdList =
        commitWiseErrorDetails.stream().map(GitToHarnessErrorCommitStats::getGitConnectorId).collect(toList());
    Map<String, SettingAttribute> connectorMap = gitSyncService.getGitConnectorMap(connectorIdList, accountId);
    List<GitToHarnessErrorCommitStats> filteredCommitDetails = new ArrayList<>();
    for (GitToHarnessErrorCommitStats commitStats : commitWiseErrorDetails) {
      String gitConnectorId = commitStats.getGitConnectorId();
      String connectorName = gitSyncService.getConnectorNameFromConnectorMap(gitConnectorId, connectorMap);
      GitConfig gitConfig = gitSyncService.getGitConfigFromConnectorMap(gitConnectorId, connectorMap);
      commitStats.setGitConnectorName(connectorName);
      commitStats.setRepositoryInfo(
          gitConfigHelperService.createRepositoryInfo(gitConfig, commitStats.getRepositoryName()));
      filteredCommitDetails.add(commitStats);
    }
    return filteredCommitDetails;
  }

  private <T> void addAppIdFilterToQuery(Query<T> query, String appId) {
    if (isBlank(appId)) {
      return;
    }
    query.filter(APP_ID_KEY, appId);
  }

  @Override
  public PageResponse<GitSyncError> fetchErrorsInEachCommits(PageRequest<GitSyncError> req, String gitCommitId,
      String accountId, String appId, List<String> includeDataList, String yamlFilePathPattern) {
    int limit = isBlank(req.getLimit()) ? DEFAULT_UNLIMITED : Integer.parseInt(req.getLimit());
    int offset = isBlank(req.getOffset()) ? 0 : Integer.parseInt(req.getOffset());

    Query<GitSyncError> query =
        wingsPersistence.createQuery(GitSyncError.class).filter(GitSyncErrorKeys.accountId, accountId);
    addAppIdFilterToQuery(query, appId);
    addGitToHarnessErrorFilter(query);
    boolean returnPreviousErrors = false;
    if (isNotEmpty(includeDataList)) {
      returnPreviousErrors = includeDataList.contains("obsoleteErrorFiles");
    }

    if (returnPreviousErrors) {
      query.disableValidation().or(query.criteria(GitSyncErrorKeys.gitCommitId).equal(gitCommitId),
          query.criteria(GitSyncErrorKeys.previousCommitIds).hasAnyOf(Collections.singletonList(gitCommitId)));
    } else {
      query.disableValidation().filter(GitSyncErrorKeys.gitCommitId, gitCommitId);
    }

    if (isNotBlank(yamlFilePathPattern)) {
      query.field(GitSyncErrorKeys.yamlFilePath).containsIgnoreCase(yamlFilePathPattern);
    }
    List<GitSyncError> allGitSyncErrors = query.asList(new FindOptions().skip(offset).limit(limit));
    List<GitSyncError> filteredErrorsAfterRemovingNewAppsIfNotRequired =
        removeNewAppErrorsInCaseOfSetupFilter(allGitSyncErrors, appId);
    List<GitSyncError> errorFilesAccessibleToUser = filteredErrorsAfterRemovingNewAppsIfNotRequired;
    if (whetherWeCanHaveAccountLevelFile(appId)) {
      errorFilesAccessibleToUser = gitSyncRBACHelper.populateUserHasPermissionForFileFieldInErrors(
          filteredErrorsAfterRemovingNewAppsIfNotRequired, accountId);
    }
    if (isEmpty(errorFilesAccessibleToUser)) {
      // Ideally this case won't happen, but if somehow the number of errors
      // in a commit becomes 0, we will return a empty response
      log.info("The gitcommitId {} of account {} has zero git sync errors", gitCommitId, accountId);
      return aPageResponse()
          .withTotal(0)
          .withLimit(String.valueOf(limit))
          .withOffset(String.valueOf(offset))
          .withResponse(Collections.emptyList())
          .build();
    }
    List<GitSyncError> allErrosGeneratedInThatCommits =
        filteredErrorsAfterRemovingNewAppsIfNotRequired.stream()
            .map(error -> getActualErrorForThatCommit(error, gitCommitId))
            .collect(toList());
    sortErrorsByFileProcessingOrder(allErrosGeneratedInThatCommits);

    return aPageResponse()
        .withTotal(allErrosGeneratedInThatCommits.size())
        .withLimit(String.valueOf(limit))
        .withOffset(String.valueOf(offset))
        .withResponse(allErrosGeneratedInThatCommits)
        .build();
  }

  private boolean whetherWeCanHaveAccountLevelFile(String appId) {
    return isEmpty(appId) || GLOBAL_APP_ID.equals(appId);
  }

  @Override
  public PageResponse<GitSyncError> listAllGitToHarnessErrors(
      PageRequest<GitSyncError> req, String accountId, String appId, String yamlFilePathPattern) {
    int limit = isBlank(req.getLimit()) ? DEFAULT_UNLIMITED : Integer.parseInt(req.getLimit());
    int offset = isBlank(req.getOffset()) ? 0 : Integer.parseInt(req.getOffset());
    Query<GitSyncError> query =
        wingsPersistence.createQuery(GitSyncError.class).filter(GitSyncErrorKeys.accountId, accountId);
    Boolean userHasAtleastOneGitConfigAccess = addAppFilterAndReturnTrueIfUserHasAnyAppAccess(query, appId, accountId);
    if (!userHasAtleastOneGitConfigAccess) {
      return aPageResponse().withTotal(0).build();
    }
    addGitToHarnessErrorFilter(query);
    if (isNotBlank(yamlFilePathPattern)) {
      query.field(GitSyncErrorKeys.yamlFilePath).containsIgnoreCase(yamlFilePathPattern);
    }
    query.disableValidation().order(Sort.descending(GitSyncErrorKeys.commitTime));
    PageResponse<GitSyncError> allErrorsResponse = aPageResponse()
                                                       .withLimit(String.valueOf(limit))
                                                       .withOffset(String.valueOf(offset))
                                                       .withTotal(query.count())
                                                       .build();
    List<GitSyncError> errorsReturnedInRequest = query.asList(new FindOptions().skip(offset).limit(limit));
    List<GitSyncError> filteredErrorsAfterRemovingNewAppsIfNotRequired =
        removeNewAppErrorsInCaseOfSetupFilter(errorsReturnedInRequest, appId);
    if (isEmpty(errorsReturnedInRequest)) {
      allErrorsResponse.setResponse(Collections.emptyList());
      return allErrorsResponse;
    }
    List<GitSyncError> errorsWithoutPreviousErrorsFields = filteredErrorsAfterRemovingNewAppsIfNotRequired.stream()
                                                               .map(error -> removePreviousErrorField(error))
                                                               .collect(toList());
    List<GitSyncError> filteredErrorsWithConnectorName =
        filterErrorsWithValidConnectorAndPopulateConnectorName(errorsWithoutPreviousErrorsFields, accountId);
    List<GitSyncError> errorsAccessibleToUser = filteredErrorsWithConnectorName;
    if (whetherWeCanHaveAccountLevelFile(appId)) {
      errorsAccessibleToUser =
          gitSyncRBACHelper.populateUserHasPermissionForFileFieldInErrors(filteredErrorsWithConnectorName, accountId);
    }
    allErrorsResponse.setResponse(errorsAccessibleToUser);
    return allErrorsResponse;
  }

  private List<GitSyncError> removeNewAppErrorsInCaseOfSetupFilter(List<GitSyncError> gitSyncErrors, String appId) {
    if (isEmpty(gitSyncErrors) || !GLOBAL_APP_ID.equals(appId)) {
      return gitSyncErrors;
    }
    return gitSyncErrors.stream().filter(error -> !isNewAppError(error)).collect(Collectors.toList());
  }

  private GitSyncError removePreviousErrorField(GitSyncError error) {
    GitToHarnessErrorDetails gitToHarnessErrorDetails;
    try {
      gitToHarnessErrorDetails = (GitToHarnessErrorDetails) error.getAdditionalErrorDetails();
    } catch (Exception e) {
      throw new UnexpectedException("The previous errors fields should only be queried for git to harness errros ", e);
    }
    gitToHarnessErrorDetails.setPreviousErrors(Collections.emptyList());
    return error;
  }

  private void sortErrorsByFileProcessingOrder(List<GitSyncError> gitSyncErrors) {
    if (isEmpty(gitSyncErrors)) {
      log.info("The errors list given for the sorting is empty");
      return;
    }
    final List<String> filePathsSortedInProcessingOrder = getFilePathsSortedInProcessingOrder(gitSyncErrors);
    gitSyncErrors.sort(getProcessingOrderComparator(filePathsSortedInProcessingOrder));
  }

  private Comparator<GitSyncError> getProcessingOrderComparator(List<String> filePathsSortedInProcessingOrder) {
    final Map<String, Integer> filePathToSortedIndexMap = getFilePathToIndexMap(filePathsSortedInProcessingOrder);
    return comparingInt(gitSyncError -> filePathToSortedIndexMap.get(gitSyncError.getYamlFilePath()));
  }

  @NotNull
  private HashMap<String, Integer> getFilePathToIndexMap(@NotNull List<String> filePathsSortedInProcessingOrder) {
    final HashMap<String, Integer> filePathToSortedIndexMap = new HashMap<>();
    for (int index = 0; index < filePathsSortedInProcessingOrder.size(); index++) {
      filePathToSortedIndexMap.put(filePathsSortedInProcessingOrder.get(index), index);
    }
    return filePathToSortedIndexMap;
  }

  private List<String> getFilePathsSortedInProcessingOrder(List<GitSyncError> gitSyncErrors) {
    if (isEmpty(gitSyncErrors)) {
      return Collections.emptyList();
    }
    final List<Change> changesConstructedFromErrors =
        gitSyncErrors.stream().map(this::convertToChange).collect(toList());
    yamlService.sortByProcessingOrder(changesConstructedFromErrors);

    return changesConstructedFromErrors.stream().map(Change::getFilePath).collect(toList());
  }

  private Change convertToChange(GitSyncError gitSyncError) {
    return aFileChange()
        .withFilePath(gitSyncError.getYamlFilePath())
        .withChangeType(Utils.getEnumFromString(ChangeType.class, gitSyncError.getChangeType()))
        .withAccountId(gitSyncError.getAccountId())
        .withSyncFromGit(true)
        .build();
  }

  private GitSyncError getActualErrorForThatCommit(GitSyncError gitSyncError, String gitCommitId) {
    if (getCommitIdOfError(gitSyncError).equals(gitCommitId)) {
      ((GitToHarnessErrorDetails) gitSyncError.getAdditionalErrorDetails()).setLatestErrorDetailForFile(null);
      // not required by UI so not sending the previous errors list
      ((GitToHarnessErrorDetails) gitSyncError.getAdditionalErrorDetails()).setPreviousErrors(Collections.emptyList());
      return gitSyncError;
    }
    return getPreviousGitSyncError(gitSyncError, gitCommitId);
  }

  private GitSyncError getPreviousGitSyncError(GitSyncError gitSyncError, String gitCommitId) {
    GitToHarnessErrorDetails gitToHarnessErrorDetails =
        (GitToHarnessErrorDetails) gitSyncError.getAdditionalErrorDetails();
    List<GitSyncError> previousGitSyncErrors = gitToHarnessErrorDetails.getPreviousErrors();
    if (isEmpty(previousGitSyncErrors)) {
      throw new UnexpectedException("The given git sync error doesn't contain the previous commits information");
    }

    GitSyncError previousGitSyncError =
        previousGitSyncErrors.stream()
            .filter(error
                -> ((GitToHarnessErrorDetails) error.getAdditionalErrorDetails()).getGitCommitId().equals(gitCommitId))
            .findFirst()
            .orElseThrow(()
                             -> new UnexpectedException(
                                 "The given git sync error doesn't contain the previous commits information"));
    ((GitToHarnessErrorDetails) previousGitSyncError.getAdditionalErrorDetails())
        .populateCommitWithLatestErrorDetails(gitSyncError);
    return previousGitSyncError;
  }

  @Override
  public <T extends Change> void upsertGitSyncErrors(
      T failedChange, String errorMessage, boolean fullSyncPath, boolean gitToHarness) {
    if (gitToHarness) {
      upsertGitToHarnessError(failedChange, errorMessage);
    } else {
      upsertHarnessToGitError(failedChange, errorMessage, fullSyncPath);
    }
  }

  private <T extends Change> void upsertHarnessToGitError(T failedChange, String errorMessage, boolean fullSyncPath) {
    Query<GitSyncError> fetchQuery = wingsPersistence.createQuery(GitSyncError.class)
                                         .filter(GitSyncError.ACCOUNT_ID_KEY2, failedChange.getAccountId())
                                         .filter(GitSyncErrorKeys.yamlFilePath, failedChange.getFilePath());
    addHarnessToGitErrorFilter(fetchQuery);

    String appId = yamlService.obtainAppIdFromGitFileChange(failedChange.getAccountId(), failedChange.getFilePath());
    log.info(String.format("Upsert haress to git issue for file: %s", failedChange.getFilePath()));
    GitFileChange failedGitFileChange = (GitFileChange) failedChange;
    UpdateOperations<GitSyncError> failedUpdateOperations =
        wingsPersistence.createUpdateOperations(GitSyncError.class)
            .setOnInsert(GitSyncError.ID_KEY2, generateUuid())
            .set(GitSyncError.ACCOUNT_ID_KEY2, failedChange.getAccountId())
            .set(GitSyncErrorKeys.gitSyncDirection, HARNESS_TO_GIT.name())
            .set(GitSyncErrorKeys.yamlFilePath, failedChange.getFilePath())
            .set(GitSyncErrorKeys.changeType, failedChange.getChangeType().name())
            .set(GitSyncErrorKeys.failureReason,
                errorMessage != null ? errorMessage : "Reason could not be captured. Logs might have some info")
            .set(
                GitSyncErrorKeys.additionalErrorDetails, getHarnessToGitErrorDetails(failedGitFileChange, fullSyncPath))
            .set(APP_ID_KEY, appId);
    populateGitDetails(failedUpdateOperations, failedGitFileChange, appId);
    fetchQuery.project(GitSyncError.ID_KEY2, true);
    wingsPersistence.upsert(fetchQuery, failedUpdateOperations);
  }

  private <T extends Change> void upsertGitToHarnessError(T failedChange, String errorMessage) {
    Query<GitSyncError> fetchQuery = wingsPersistence.createQuery(GitSyncError.class)
                                         .filter(GitSyncError.ACCOUNT_ID_KEY2, failedChange.getAccountId())
                                         .filter(GitSyncErrorKeys.yamlFilePath, failedChange.getFilePath());
    addGitToHarnessErrorFilter(fetchQuery);

    GitFileChange failedGitFileChange = (GitFileChange) failedChange;
    String appId = yamlService.obtainAppIdFromGitFileChange(failedChange.getAccountId(), failedChange.getFilePath());
    log.info("Upsert git to harness sync issue for file: [{}]", failedChange.getFilePath());

    UpdateOperations<GitSyncError> failedUpdateOperations =
        wingsPersistence.createUpdateOperations(GitSyncError.class)
            .setOnInsert(GitSyncError.ID_KEY2, generateUuid())
            .set(GitSyncError.ACCOUNT_ID_KEY2, failedChange.getAccountId())
            .set(GitSyncErrorKeys.gitSyncDirection, GIT_TO_HARNESS.name())
            .set(GitSyncErrorKeys.yamlFilePath, failedChange.getFilePath())
            .set(GitSyncErrorKeys.changeType, failedChange.getChangeType().name())
            .set(GitSyncErrorKeys.failureReason,
                errorMessage != null ? errorMessage : "Reason could not be captured. Logs might have some info")
            .set(APP_ID_KEY, appId);

    populateGitDetails(failedUpdateOperations, failedGitFileChange, appId);

    GitToHarnessErrorDetails gitToHarnessErrorDetails = getGitToHarnessErrorDetails(failedGitFileChange);
    final GitSyncError previousGitSyncError = fetchQuery.get();
    if (previousGitSyncError != null) {
      if (!failedGitFileChange.isChangeFromAnotherCommit()) {
        GitToHarnessErrorDetails oldGitToHarnessErrorDetails =
            (GitToHarnessErrorDetails) previousGitSyncError.getAdditionalErrorDetails();
        // Reading the previous errors of the file
        List<GitSyncError> previousGitSyncErrors =
            new ArrayList<>(emptyIfNull(oldGitToHarnessErrorDetails.getPreviousErrors()));
        List<String> previousCommitIds =
            new ArrayList<>(emptyIfNull(oldGitToHarnessErrorDetails.getPreviousCommitIdsWithError()));
        log.info("Adding the error with the commitId [{}] to the previous commit list of file [{}]",
            getCommitIdOfError(previousGitSyncError), failedChange.getFilePath());
        // Setting the value of the previous details as empty as this record will not go to the previous list
        previousGitSyncError.setUuid(generateUuid());
        ((GitToHarnessErrorDetails) previousGitSyncError.getAdditionalErrorDetails())
            .setPreviousErrors(Collections.emptyList());
        ((GitToHarnessErrorDetails) previousGitSyncError.getAdditionalErrorDetails())
            .setPreviousCommitIdsWithError(Collections.emptyList());
        // adding the new entry to the list
        previousGitSyncErrors.add(previousGitSyncError);
        previousCommitIds.add(getCommitIdOfError(previousGitSyncError));
        // For the new error updating all its fields with the appropriate values
        gitToHarnessErrorDetails.setPreviousErrors(previousGitSyncErrors);
        gitToHarnessErrorDetails.setPreviousCommitIdsWithError(previousCommitIds);
      }
    } else {
      log.info("Creating a new error record for the file [{}] in account", failedChange.getFilePath());
      gitToHarnessErrorDetails.setPreviousErrors(Collections.emptyList());
      gitToHarnessErrorDetails.setPreviousCommitIdsWithError(Collections.emptyList());
    }
    failedUpdateOperations.set(GitSyncErrorKeys.additionalErrorDetails, gitToHarnessErrorDetails);
    fetchQuery.project(GitSyncError.ID_KEY2, true);
    wingsPersistence.upsert(fetchQuery, failedUpdateOperations);
  }

  private HarnessToGitErrorDetails getHarnessToGitErrorDetails(GitFileChange failedChange, boolean fullSyncPath) {
    return HarnessToGitErrorDetails.builder().fullSyncPath(fullSyncPath).build();
  }

  private GitToHarnessErrorDetails getGitToHarnessErrorDetails(GitFileChange failedGitFileChange) {
    String failedCommitId = failedGitFileChange.getCommitId() != null ? failedGitFileChange.getCommitId() : "";
    if (failedCommitId.equals("")) {
      log.info("Unexpected behaviour: The git commitId is null for the git to harness error");
    }
    return GitToHarnessErrorDetails.builder()
        .gitCommitId(failedCommitId)
        .yamlContent(failedGitFileChange.getFileContent())
        .commitTime(failedGitFileChange.getCommitTimeMs())
        .commitMessage(failedGitFileChange.getCommitMessage())
        .build();
  }

  private <T extends Change> void populateGitDetails(
      UpdateOperations<GitSyncError> failedUpdateOperations, GitFileChange failedGitFileChange, String appId) {
    final software.wings.yaml.gitSync.YamlGitConfig yamlGitConfig = failedGitFileChange.getYamlGitConfig() != null
        ? failedGitFileChange.getYamlGitConfig()
        : yamlGitService.fetchYamlGitConfig(appId, failedGitFileChange.getAccountId()).toDTO();

    if (yamlGitConfig != null) {
      final String gitConnectorId = Strings.emptyIfNull(yamlGitConfig.getGitConnectorId());
      final String branchName = Strings.emptyIfNull(yamlGitConfig.getBranchName());
      final String repositoryName = Strings.nullIfEmpty(yamlGitConfig.getRepositoryName());
      failedUpdateOperations.set(GitSyncErrorKeys.gitConnectorId, gitConnectorId);
      failedUpdateOperations.set(GitSyncErrorKeys.branchName, branchName);
      failedUpdateOperations.set(GitSyncErrorKeys.yamlGitConfigId, yamlGitConfig.getUuid());
      if (null != repositoryName) {
        failedUpdateOperations.set(GitSyncErrorKeys.repositoryName, repositoryName);
      }
    }
  }

  @Override
  public PageResponse<GitSyncError> fetchErrors(PageRequest<GitSyncError> req) {
    return wingsPersistence.query(GitSyncError.class, req);
  }

  @Override
  public PageResponse<GitSyncError> fetchHarnessToGitErrors(
      PageRequest<GitSyncError> req, String accountId, String appId) {
    int limit = isBlank(req.getLimit()) ? DEFAULT_UNLIMITED : Integer.parseInt(req.getLimit());
    int offset = isBlank(req.getOffset()) ? 0 : Integer.parseInt(req.getOffset());
    Query<GitSyncError> query =
        wingsPersistence.createQuery(GitSyncError.class).filter(GitSyncErrorKeys.accountId, accountId);
    Boolean userHasAtleastOneGitConfigPerm = addAppFilterAndReturnTrueIfUserHasAnyAppAccess(query, appId, accountId);
    if (!userHasAtleastOneGitConfigPerm) {
      return aPageResponse().withTotal(0).build();
    }
    query.order(Sort.descending(CREATED_AT_KEY));
    addHarnessToGitErrorFilter(query);
    List<GitSyncError> allGitSyncErrors = emptyIfNull(query.asList(new FindOptions().skip(offset).limit(limit)));
    List<GitSyncError> filteredErrors =
        filterErrorsWithValidConnectorAndPopulateConnectorName(allGitSyncErrors, accountId);
    List<GitSyncError> errorsAccessibleToUser =
        gitSyncRBACHelper.populateUserHasPermissionForFileFieldInErrors(filteredErrors, accountId);
    return aPageResponse()
        .withTotal(query.count())
        .withResponse(errorsAccessibleToUser)
        .withLimit(String.valueOf(limit))
        .withOffset(String.valueOf(offset))
        .build();
  }

  private List<GitSyncError> filterErrorsWithValidConnectorAndPopulateConnectorName(
      List<GitSyncError> gitSyncErrors, String accountId) {
    if (isEmpty(gitSyncErrors)) {
      return Collections.emptyList();
    }
    List<String> connectorIdList = gitSyncErrors.stream().map(error -> error.getGitConnectorId()).collect(toList());
    Map<String, SettingAttribute> connectorMap = gitSyncService.getGitConnectorMap(connectorIdList, accountId);
    List<GitSyncError> filteredErrors = new ArrayList<>();

    for (GitSyncError error : gitSyncErrors) {
      String gitConnectorId = error.getGitConnectorId();
      String connectorName = gitSyncService.getConnectorNameFromConnectorMap(gitConnectorId, connectorMap);
      GitConfig gitConfig = gitSyncService.getGitConfigFromConnectorMap(gitConnectorId, connectorMap);
      error.setGitConnectorName(connectorName);
      error.setRepositoryInfo(gitConfigHelperService.createRepositoryInfo(gitConfig, error.getRepositoryName()));
      filteredErrors.add(error);
    }
    return filteredErrors;
  }

  @Override
  public List<GitSyncError> getActiveGitToHarnessSyncErrors(
      String accountId, String gitConnectorId, String branchName, String repositoryName, long fromTimestamp) {
    //  get all app ids sharing same repo and branch name
    final Set<String> allowedAppIdSet = appIdsSharingRepoBranch(accountId, gitConnectorId, branchName, repositoryName);
    if (isEmpty(allowedAppIdSet)) {
      return Collections.emptyList();
    }
    final Query<GitSyncError> query = wingsPersistence.createQuery(GitSyncError.class)
                                          .filter(GitSyncErrorKeys.accountId, accountId)
                                          .field(CreatedAtAware.CREATED_AT_KEY)
                                          .greaterThan(fromTimestamp);

    addGitToHarnessErrorFilter(query);
    addGitRepositoryFilter(query, gitConnectorId, branchName, repositoryName);
    addActiveErrorFilter(query);

    final List<GitSyncError> gitSyncErrorList = emptyIfNull(query.asList());

    return gitSyncErrorList.stream()
        .filter(gitSyncError -> errorOfAllowedApp(gitSyncError, allowedAppIdSet) || isNewAppError(gitSyncError))
        .collect(toList());
  }
  private Query<GitSyncError> addActiveErrorFilter(Query<GitSyncError> query) {
    query.or(query.criteria(GitSyncErrorKeys.status).doesNotExist(),
        query.criteria(GitSyncErrorKeys.status).equal(GitSyncErrorStatus.ACTIVE));
    return query;
  }

  private Query<GitSyncError> addGitRepositoryFilter(
      Query<GitSyncError> query, String gitConnectorId, String branchName, String repositoryName) {
    return query.filter(GitSyncErrorKeys.branchName, branchName)
        .filter(GitSyncErrorKeys.gitConnectorId, gitConnectorId)
        .filter(GitSyncErrorKeys.repositoryName, repositoryName);
  }

  private boolean isNewAppError(GitSyncError error) {
    return GLOBAL_APP_ID.equals(error.getAppId())
        && error.getYamlFilePath().startsWith(SETUP_FOLDER + PATH_DELIMITER + APPLICATIONS_FOLDER);
  }
  private boolean errorOfAllowedApp(GitSyncError error, Set<String> allowedAppIdSet) {
    return allowedAppIdSet.contains(error.getAppId());
  }

  private Set<String> appIdsSharingRepoBranch(
      String accountId, String gitConnectorId, String branchName, String repositoryName) {
    final List<YamlGitConfig> yamlGitConfigList = wingsPersistence.createQuery(YamlGitConfig.class)
                                                      .project(YamlGitConfigKeys.entityId, true)
                                                      .project(YamlGitConfigKeys.entityType, true)
                                                      .filter(GitSyncErrorKeys.accountId, accountId)
                                                      .filter(YamlGitConfigKeys.enabled, Boolean.TRUE)
                                                      .filter(YamlGitConfigKeys.gitConnectorId, gitConnectorId)
                                                      .filter(YamlGitConfigKeys.branchName, branchName)
                                                      .filter(YamlGitConfigKeys.repositoryName, repositoryName)
                                                      .asList();

    return emptyIfNull(yamlGitConfigList)
        .stream()
        .map(
            yamlGitConfig -> yamlGitConfig.getEntityType() == APPLICATION ? yamlGitConfig.getEntityId() : GLOBAL_APP_ID)
        .collect(Collectors.toSet());
  }

  @Override
  public void deleteGitSyncErrorAndLogFileActivity(
      List<String> gitSyncErrorsIds, GitFileActivity.Status status, String accountId) {
    if (isEmpty(gitSyncErrorsIds)) {
      throw new InvalidRequestException("No git sync error Id provided in the discard api");
    }
    PageRequest<GitSyncError> req = aPageRequest()
                                        .addFieldsIncluded("_id")
                                        .addFilter(GitSyncErrorKeys.accountId, SearchFilter.Operator.EQ, accountId)
                                        .addFilter("_id", IN, gitSyncErrorsIds.toArray())
                                        .build();

    List<GitSyncError> gitSyncErrors = wingsPersistence.query(GitSyncError.class, req).getResponse();
    if (isEmpty(gitSyncErrors)) {
      log.info(" None of the errors provided for the discard are latest git sync error");
      return;
    }
    wingsPersistence.save(gitSyncService.getActivitiesForGitSyncErrors(gitSyncErrors, status));
    if (TERMINATING_STATUSES.contains(status)) {
      final List<String> discardErrorIds =
          gitSyncErrors.stream().map(error -> error.getUuid()).collect(Collectors.toList());
      log.info(String.format("Marking errors with ids: %s as %s", discardErrorIds, status.name()));
      deleteGitSyncErrors(discardErrorIds, accountId);
    }
  }

  @Override
  public boolean deleteGitSyncErrors(List<String> errorIds, String accountId) {
    Query query = wingsPersistence.createQuery(GitSyncError.class);
    query.filter(GitSyncErrorKeys.accountId, accountId);
    query.field(ID_KEY).in(errorIds);
    boolean deleted = wingsPersistence.delete(query);
    alertsUtils.closeAlertIfApplicable(accountId);
    return deleted;
  }

  @Override
  public boolean deleteGitSyncErrorsBeforeTime(long time, String accountId) {
    Query<GitSyncError> query = wingsPersistence.createQuery(GitSyncError.class);
    query.filter(GitSyncErrorKeys.accountId, accountId);
    query.field(CREATED_AT_KEY).lessThan(time);
    boolean deleted = wingsPersistence.delete(query);
    alertsUtils.closeAlertIfApplicable(accountId);
    return deleted;
  }

  private GitProcessingError getGitProcessingError(Alert alert) {
    GitConnectionErrorAlert alertData = (GitConnectionErrorAlert) alert.getAlertData();
    if (alertData == null) {
      throw new UnexpectedException("The alert data is null in the  alert with id " + alert.getUuid());
    }
    return GitProcessingError.builder()
        .message(alert.getTitle())
        .accountId(alert.getAccountId())
        .createdAt(alert.getCreatedAt())
        .branchName(alertData.getBranchName())
        .gitConnectorId(alertData.getGitConnectorId())
        .repositoryName(alertData.getRepositoryName())
        .build();
  }

  private void populateGitProcessingErrorWithConnectorName(
      GitProcessingError gitProcessingError, Map<String, SettingAttribute> connectorMap) {
    if (gitProcessingError == null) {
      return;
    }
    String gitConnectorId = gitProcessingError.getGitConnectorId();
    if (isBlank(gitProcessingError.getGitConnectorId()) || !connectorMap.containsKey(gitConnectorId)) {
      gitProcessingError.setConnectorName(null);
      gitProcessingError.setBranchName(null);
      gitProcessingError.setRepositoryName(null);
    } else {
      String connectorName = gitSyncService.getConnectorNameFromConnectorMap(gitConnectorId, connectorMap);
      GitConfig gitConfig = gitSyncService.getGitConfigFromConnectorMap(gitConnectorId, connectorMap);
      gitProcessingError.setConnectorName(connectorName);
      gitProcessingError.setRepositoryInfo(
          gitConfigHelperService.createRepositoryInfo(gitConfig, gitProcessingError.getRepositoryName()));
    }
  }

  private void populateTheConnectorName(
      List<GitProcessingError> gitProcessingErrors, Map<String, SettingAttribute> connectorMap) {
    if (isEmpty(gitProcessingErrors)) {
      return;
    }
    // We need to filter because maybe someone deleted the connector and
    // alert still uses that gitConnectorId
    gitProcessingErrors.forEach(e -> populateGitProcessingErrorWithConnectorName(e, connectorMap));
  }

  private List<Alert> getGitConnectionAlert(String accountId) {
    PageRequest<Alert> gitAlertRequest = aPageRequest()
                                             .addFilter(AlertKeys.accountId, EQ, accountId)
                                             .addFilter(AlertKeys.type, EQ, AlertType.GitConnectionError)
                                             .addFilter(AlertKeys.status, EQ, Open)
                                             .addOrder(AlertKeys.createdAt, DESC)
                                             .build();
    return wingsPersistence.query(Alert.class, gitAlertRequest).getResponse();
  }

  @Override
  public PageResponse<GitProcessingError> fetchGitConnectivityIssues(
      PageRequest<GitProcessingError> req, String accountId) {
    List<Alert> gitProcessingAlerts = getGitConnectionAlert(accountId);
    if (isEmpty(gitProcessingAlerts)) {
      return aPageResponse().withTotal(0).withResponse(Collections.emptyList()).build();
    }
    List<GitProcessingError> gitProcessingErrors =
        gitProcessingAlerts.stream().map(this::getGitProcessingError).collect(toList());
    List<String> connectorIds =
        gitProcessingErrors.stream().map(GitProcessingError::getGitConnectorId).distinct().collect(toList());
    Map<String, SettingAttribute> connectorMap = gitSyncService.getGitConnectorMap(connectorIds, accountId);
    populateTheConnectorName(gitProcessingErrors, connectorMap);
    return aPageResponse().withTotal(gitProcessingErrors.size()).withResponse(gitProcessingErrors).build();
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(
        wingsPersistence.createQuery(GitSyncError.class).filter(GitSyncErrorKeys.accountId, accountId));
  }
}
