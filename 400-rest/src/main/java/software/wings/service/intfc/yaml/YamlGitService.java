/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.yaml;

import static io.harness.annotations.dev.HarnessModule._951_CG_GIT_SYNC;

import io.harness.alert.AlertData;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.rest.RestResponse;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.GitCommit;
import software.wings.beans.GitConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.alert.AlertType;
import software.wings.beans.yaml.FullSyncChangeset;
import software.wings.beans.yaml.GitFileChange;
import software.wings.exception.YamlProcessingException.ChangeWithErrorMsg;
import software.wings.service.impl.yaml.sync.GitSyncFailureAlertDetails;
import software.wings.yaml.errorhandling.GitSyncError;
import software.wings.yaml.gitSync.GitSyncWebhook;
import software.wings.yaml.gitSync.YamlChangeSet;
import software.wings.yaml.gitSync.YamlGitConfig;

import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.HttpHeaders;
import org.hibernate.validator.constraints.NotEmpty;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * The interface Yaml git sync service.
 */
@OwnedBy(HarnessTeam.DX)
@TargetModule(_951_CG_GIT_SYNC)
public interface YamlGitService {
  /**
   * Gets the yaml git sync info by entitytId
   *
   * @param accountId the account id
   * @param entityId  the entity id
   * @return the rest response
   */
  YamlGitConfig get(@NotEmpty String accountId, @NotEmpty String entityId, @Valid EntityType entityType);

  /**
   *
   * @param req
   * @return
   */
  PageResponse<YamlGitConfig> list(PageRequest<YamlGitConfig> req);

  /**
   * Creates a new yaml git sync info by object type and entitytId (uuid)
   *
   * @param yamlGitSync the yamlGitSync info
   * @return the rest response
   */
  @ValidationGroups(Create.class) YamlGitConfig save(@Valid YamlGitConfig yamlGitSync);

  /**
   * Updates the yaml git sync info by object type and entitytId (uuid)
   *
   * @param yamlGitSync the yamlGitSync info
   * @return the rest response
   */
  @ValidationGroups(Update.class) YamlGitConfig update(@Valid YamlGitConfig yamlGitSync);

  /**
   * Push directory.
   *
   * @param accountId the account id
   * @param forcePush force push
   */
  void fullSync(
      @NotEmpty String accountId, @NotEmpty String entityId, @NotNull EntityType entityType, boolean forcePush);

  YamlChangeSet obtainAppYamlChangeSet(String accountId, boolean forcePush, Application app);

  /**
   * Perform full sync dry run list.
   *
   * @param accountId the account id
   * @return the list
   */
  List<GitFileChange> performFullSyncDryRun(String accountId);

  void performFullSyncDryRunOnAllAccounts();

  List<String> getAllYamlErrorsForAccount(String accountId);
  List<String> getAllYamlErrorsForAllAccounts();

  YamlGitConfig getYamlGitConfigForHarnessToGitChangeSet(YamlChangeSet harnessToGitChangeSet);

  /**
   *  Handle change set boolean.
   * @param yamlChangeSet
   * @param accountId
   * @return
   */
  void handleHarnessChangeSet(YamlChangeSet yamlChangeSet, String accountId);

  List<YamlGitConfig> getYamlGitConfigsForGitToHarnessChangeSet(YamlChangeSet gitToHarnessChangeSet);

  List<YamlGitConfig> getYamlGitConfigs(
      String accountId, String gitConnectorId, String branchName, String repositoryName);

  List<String> getYamlGitConfigIds(String accountId, String gitConnectorId, String branchName, String repositoryName);

  void handleGitChangeSet(YamlChangeSet yamlChangeSets, String accountId);

  String validateAndQueueWebhookRequest(
      String accountId, String webhookToken, String yamlWebHookPayload, HttpHeaders headers);

  /**
   * Is commit already processed boolean.
   *
   * @param accountId  the account id
   * @param headCommit the head commit
   * @return the boolean
   */
  boolean isCommitAlreadyProcessed(String accountId, String headCommit);

  /**
   * Gets webhook.
   *
   * @param entityId  the entity id
   * @param accountId the account id
   * @return the webhook
   */
  GitSyncWebhook getWebhook(String entityId, String accountId);

  /**
   * Save commit git commit.
   *
   * @param gitCommit the git commit
   * @return the git commit
   */
  GitCommit saveCommit(GitCommit gitCommit);

  /**
   * Create git sync errors for failed changes
   * @param accountId
   * @param failedChangeErrorMsgMap
   */
  void processFailedChanges(
      String accountId, Map<String, ChangeWithErrorMsg> failedChangeErrorMsgMap, boolean gitToHarness);

  /**
   * Remove git sync errors.
   *
   * @param accountId         the account id
   * @param gitFileChangeList the git file change list
   */
  void removeGitSyncErrors(String accountId, List<GitFileChange> gitFileChangeList, boolean gitToHarness);

  /**
   * List git sync errors rest response.
   *
   * @param accountId the account id
   * @return the rest response
   */
  RestResponse<List<GitSyncError>> listGitSyncErrors(String accountId);

  /**
   * Discard git sync error rest response.
   *
   * @param accountId    the account id
   * @param errorId the yaml file path
   * @return the rest response
   */
  RestResponse discardGitSyncError(String accountId, String errorId);

  void raiseAlertForGitFailure(String accountId, String appId, GitSyncFailureAlertDetails gitSyncFailureAlertDetails);

  void closeAlertForGitFailureIfOpen(String accountId, String appId, AlertType alertType, AlertData alertData);

  RestResponse discardGitSyncErrorForFullSync(String accountId, String appId);

  RestResponse discardGitSyncErrorForFilePath(String accountId, String yamlFilePath);

  RestResponse discardGitSyncErrorsForGivenPaths(String accountId, List<String> yamlFilePaths);

  RestResponse discardAllGitSyncError(String accountId);

  SettingAttribute getAndDecryptSettingAttribute(String sshSettingId);

  List<YamlChangeSet> obtainChangeSetFromFullSyncDryRun(
      String accountId, boolean onlyGitSyncConfiguredEntities, boolean skipAppLevel);

  boolean checkApplicationChange(GitFileChange gitFileChange);

  String obtainAppNameFromGitFileChange(GitFileChange gitFileChange);

  boolean checkApplicationNameIsValid(GitFileChange gitFileChange);

  void delete(String accountId, String entityId, EntityType entityType);

  GitConfig getGitConfig(YamlGitConfig ygs);

  YamlGitConfig save(YamlGitConfig ygs, boolean performFullSync);

  void fullSyncForEntireAccount(String accountId);

  void asyncFullSyncForEntireAccount(String accountId);

  void syncForTemplates(String accountId, String appId);

  FullSyncChangeset obtainApplicationYamlGitFileChanges(String accountId, Application app);

  boolean retainYamlGitConfigsOfSelectedGitConnectorsAndDeleteRest(
      String accountId, List<String> gitConnectorsToRetain);

  RestResponse discardGitSyncErrorsForGivenIds(String accountId, List<String> errorIds);

  YamlGitConfig fetchYamlGitConfig(String appId, String accountId);
}
