/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.GITHUB_WEBHOOK_AUTHENTICATION;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.trigger.WebhookSource.BITBUCKET;
import static software.wings.beans.trigger.WebhookSource.GITHUB;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.SettingAttribute;
import software.wings.beans.trigger.GithubAction;
import software.wings.beans.trigger.ReleaseAction;
import software.wings.beans.trigger.TriggerCondition;
import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.beans.trigger.WebhookEventType;
import software.wings.beans.trigger.WebhookSource;
import software.wings.beans.trigger.WebhookSource.BitBucketEventType;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.yaml.handler.trigger.TriggerConditionYamlHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.yaml.trigger.WebhookEventTriggerConditionYaml.WebhookEventTriggerConditionYamlBuilder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Singleton
@Slf4j
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class WebhookTriggerConditionHandler extends TriggerConditionYamlHandler<WebhookEventTriggerConditionYaml> {
  private static final String PACKAGE_STRING = "package:";
  private static final String SPLITTER = ":";

  @Inject private SecretManager secretManager;
  @Inject private AppService appService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private SettingsService settingsService;

  @Override
  public WebhookEventTriggerConditionYaml toYaml(TriggerCondition bean, String appId) {
    WebHookTriggerCondition webHookTriggerCondition = (WebHookTriggerCondition) bean;

    WebhookEventTriggerConditionYamlBuilder webhookEventTriggerConditionYamlBuilder =
        WebhookEventTriggerConditionYaml.builder()
            .action(getYAMLActions(webHookTriggerCondition))
            .releaseActions(getYAMLReleaseActions(webHookTriggerCondition))
            .repositoryType(getBeanWebhookSourceForYAML(webHookTriggerCondition.getWebhookSource()))
            .eventType(getYAMLEventTypes(webHookTriggerCondition.getEventTypes()))
            .branchRegex(webHookTriggerCondition.getBranchRegex());

    if (webHookTriggerCondition.isCheckFileContentChanged()) {
      webhookEventTriggerConditionYamlBuilder.checkFileContentChanged(
          webHookTriggerCondition.isCheckFileContentChanged());
      webhookEventTriggerConditionYamlBuilder.gitConnectorName(
          getGitConnectorNameFromId(webHookTriggerCondition.getGitConnectorId()));
      webhookEventTriggerConditionYamlBuilder.repoName(webHookTriggerCondition.getRepoName());
      webhookEventTriggerConditionYamlBuilder.branchName(webHookTriggerCondition.getBranchName());
      webhookEventTriggerConditionYamlBuilder.filePaths(webHookTriggerCondition.getFilePaths());
    }

    String accountId = appService.getAccountIdByAppId(appId);
    if (featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, accountId)) {
      webhookEventTriggerConditionYamlBuilder.webhookSecret(
          secretManager.getEncryptedYamlRef(accountId, webHookTriggerCondition.getWebHookSecret()));
    }

    return webhookEventTriggerConditionYamlBuilder.build();
  }

  @Override
  public TriggerCondition upsertFromYaml(
      ChangeContext<WebhookEventTriggerConditionYaml> changeContext, List<ChangeContext> changeSetContext) {
    TriggerConditionYaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();

    WebhookEventTriggerConditionYaml webhookConditionYaml = (WebhookEventTriggerConditionYaml) yaml;

    return fromYAML(webhookConditionYaml, accountId);
  }

  public WebHookTriggerCondition fromYAML(WebhookEventTriggerConditionYaml webhookConditionYaml, String accountId) {
    WebHookTriggerCondition webHookTriggerCondition =
        WebHookTriggerCondition.builder()
            .branchRegex(webhookConditionYaml.getBranchRegex())
            .eventTypes(getBeansEventTypes(webhookConditionYaml.getEventType()))
            .webhookSource(getBeanWebhookSource(webhookConditionYaml.getRepositoryType()))
            .checkFileContentChanged(getCheckFileContentChanged(webhookConditionYaml))
            .gitConnectorId(getGitConnectorIdFromName(
                webhookConditionYaml.getGitConnectorName(), accountId, webhookConditionYaml.getGitConnectorId()))
            .repoName(webhookConditionYaml.getRepoName())
            .branchName(webhookConditionYaml.getBranchName())
            .filePaths(webhookConditionYaml.getFilePaths())
            .build();

    if (isNotEmpty(webhookConditionYaml.getRepositoryType())) {
      if (webhookConditionYaml.getRepositoryType().equals(GITHUB.name())) {
        webHookTriggerCondition.setActions(getPRActionTypes(webhookConditionYaml.getAction(),
            webhookConditionYaml.getRepositoryType(), webhookConditionYaml.getEventType()));
        webHookTriggerCondition.setReleaseActions(
            getReleaseActionTypes(webhookConditionYaml.getReleaseActions(), webhookConditionYaml.getRepositoryType()));
      } else if (webhookConditionYaml.getRepositoryType().equals(BITBUCKET.name())) {
        webHookTriggerCondition.setBitBucketEvents(
            getBitBucketEventType(webhookConditionYaml.getAction(), webhookConditionYaml.getRepositoryType()));
      }
    }

    if (featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, accountId)) {
      if (webhookConditionYaml.getWebhookSecret() != null
          && !GITHUB.name().equals(webhookConditionYaml.getRepositoryType())) {
        throw new InvalidRequestException("WebHook Secret is only supported with Github repository", USER);
      }
      webHookTriggerCondition.setWebHookSecret(
          yamlHelper.extractEncryptedRecordId(webhookConditionYaml.getWebhookSecret(), accountId));
    }

    return webHookTriggerCondition;
  }

  private String getGitConnectorIdFromName(String gitConnectorName, String accountId, String gitConnectorId) {
    if (gitConnectorName == null) {
      if (gitConnectorId != null) {
        log.info("YAML_ID_LOGS: User sending id in yaml in Triggers. accountId: {}", accountId);
        return gitConnectorId;
      }
      return null;
    }
    SettingAttribute gitSettingAttribute = settingsService.getSettingAttributeByName(accountId, gitConnectorName);
    notNullCheck(String.format("Git ConnectorDisconnectHandler %s does not exist.", gitConnectorName), gitSettingAttribute);
    return gitSettingAttribute.getUuid();
  }

  private String getGitConnectorNameFromId(String gitConnectorId) {
    SettingAttribute gitSettingAttribute = settingsService.get(gitConnectorId);
    notNullCheck(String.format("Git ConnectorDisconnectHandler %s does not exist.", gitConnectorId), gitSettingAttribute);
    return gitSettingAttribute.getName();
  }

  private boolean getCheckFileContentChanged(WebhookEventTriggerConditionYaml webhookConditionYaml) {
    if (webhookConditionYaml.getCheckFileContentChanged() != null) {
      return webhookConditionYaml.getCheckFileContentChanged().booleanValue();
    } else {
      return false;
    }
  }

  private WebhookSource getBeanWebhookSource(String webhookSource) {
    if (EmptyPredicate.isEmpty(webhookSource)) {
      return null;
    }
    if (webhookSource.equals("GITLAB")) {
      return WebhookSource.GITLAB;
    } else if (webhookSource.equals("GITHUB")) {
      return WebhookSource.GITHUB;
    } else if (webhookSource.equals("BITBUCKET")) {
      return WebhookSource.BITBUCKET;
    } else {
      notNullCheck("webhook source is invalid or not supported", webhookSource);
    }

    return null;
  }

  private String getBeanWebhookSourceForYAML(WebhookSource webhookSource) {
    if (webhookSource == null) {
      return null;
    }
    if (webhookSource == WebhookSource.GITHUB || webhookSource == WebhookSource.GITLAB
        || webhookSource == WebhookSource.BITBUCKET) {
      return webhookSource.name();
    } else {
      notNullCheck("webhook source is invalid or not supported", webhookSource);
    }

    return null;
  }

  private List<WebhookEventType> getBeansEventTypes(List<String> eventTypes) {
    if (EmptyPredicate.isEmpty(eventTypes)) {
      return null;
    }
    return eventTypes.stream().map(WebhookEventType::find).collect(Collectors.toList());
  }

  private List<GithubAction> getPRActionTypes(List<String> actions, String webhookSource, List<String> eventType) {
    if (isNotEmpty(actions) && isNotEmpty(webhookSource) && webhookSource.equals(GITHUB.name())) {
      if (eventType != null && eventType.contains("package")) {
        return actions.stream().map(action -> GithubAction.find(PACKAGE_STRING + action)).collect(Collectors.toList());
      }
      return actions.stream().map(GithubAction::find).collect(Collectors.toList());
    } else {
      return null;
    }
  }

  private List<ReleaseAction> getReleaseActionTypes(List<String> actions, String webhookSource) {
    if (isNotEmpty(actions) && isNotEmpty(webhookSource) && webhookSource.equals(GITHUB.name())) {
      return actions.stream().map(ReleaseAction::find).collect(Collectors.toList());
    } else {
      return null;
    }
  }

  private List<BitBucketEventType> getBitBucketEventType(List<String> actions, String webhookSource) {
    if (isNotEmpty(actions) && isNotEmpty(webhookSource) && webhookSource.equals("BITBUCKET")) {
      return actions.stream().map(BitBucketEventType::find).collect(Collectors.toList());
    } else {
      return null;
    }
  }

  private List<String> getYAMLEventTypes(List<WebhookEventType> eventTypes) {
    if (isNotEmpty(eventTypes)) {
      return eventTypes.stream().map(WebhookEventType::getValue).collect(Collectors.toList());
    } else {
      return null;
    }
  }

  private List<String> getYAMLActions(WebHookTriggerCondition webHookTriggerCondition) {
    if (webHookTriggerCondition != null && webHookTriggerCondition.getWebhookSource() != null
        && webHookTriggerCondition.getWebhookSource() == GITHUB) {
      if (isNotEmpty(webHookTriggerCondition.getActions())) {
        if (webHookTriggerCondition.getEventTypes() != null
            && webHookTriggerCondition.getEventTypes().contains(WebhookEventType.PACKAGE)) {
          List<String> yamlActions = new ArrayList<>();
          for (GithubAction githubAction : webHookTriggerCondition.getActions()) {
            if (githubAction.getValue() != null && githubAction.getValue().split(SPLITTER).length > 1) {
              yamlActions.add(githubAction.getValue().split(SPLITTER)[1]);
            }
          }
          return yamlActions;
        }
        return webHookTriggerCondition.getActions().stream().map(GithubAction::getValue).collect(Collectors.toList());
      } else {
        return null;
      }
    } else if (webHookTriggerCondition != null && webHookTriggerCondition.getWebhookSource() != null
        && webHookTriggerCondition.getWebhookSource() == WebhookSource.BITBUCKET) {
      if (isNotEmpty(webHookTriggerCondition.getBitBucketEvents())) {
        return webHookTriggerCondition.getBitBucketEvents()
            .stream()
            .map(BitBucketEventType::getValue)
            .collect(Collectors.toList());
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  private List<String> getYAMLReleaseActions(WebHookTriggerCondition webHookTriggerCondition) {
    if (webHookTriggerCondition != null && webHookTriggerCondition.getWebhookSource() != null
        && webHookTriggerCondition.getWebhookSource() == GITHUB) {
      if (isNotEmpty(webHookTriggerCondition.getReleaseActions())) {
        return webHookTriggerCondition.getReleaseActions()
            .stream()
            .map(ReleaseAction::getValue)
            .collect(Collectors.toList());
      }
    }
    return null;
  }

  @Override
  public Class getYamlClass() {
    return WebhookEventTriggerConditionYaml.class;
  }
}
