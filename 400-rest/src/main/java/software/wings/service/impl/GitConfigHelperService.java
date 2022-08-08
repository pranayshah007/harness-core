/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notEmptyCheck;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.yaml.YamlConstants.GIT_YAML_LOG_PREFIX;
import static software.wings.service.impl.AssignDelegateServiceImpl.SCOPE_WILDCARD;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.ff.FeatureFlagService;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GitConfig;
import software.wings.beans.GitRepositoryInfo;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.yaml.GitCommand.GitCommandType;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitCommandExecutionResponse.GitCommandStatus;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.transport.URIish;

@Singleton
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class GitConfigHelperService {
  @Inject private DelegateService delegateService;
  @Inject private SettingsService settingsService;
  @Inject private ManagerDecryptionService managerDecryptionService;
  @Inject private SecretManager secretManager;
  @Inject private SettingValidationService settingValidationService;
  @Inject private FeatureFlagService featureFlagService;

  public static final String UNKNOWN_GIT_CONNECTOR = "Unknown Git Connector";

  public static boolean matchesRepositoryName(String repo1, String repo2) {
    repo1 = cleanupRepositoryName(repo1);
    repo2 = cleanupRepositoryName(repo2);
    return StringUtils.equals(repo1, repo2);
  }

  public static String cleanupRepositoryName(@Nullable String repoName) {
    if (null != repoName) {
      repoName = StringUtils.lowerCase(repoName);
      repoName = StringUtils.removeEnd(repoName, "/");
      repoName = StringUtils.removeEnd(repoName, ".git");
    }
    return repoName;
  }

  public void validateGitConfig(GitConfig gitConfig, List<EncryptedDataDetail> encryptionDetails) {
    if (gitConfig.isKeyAuth()) {
      if (gitConfig.getSshSettingId() == null) {
        throw new InvalidRequestException("SSH SettingId can not be empty");
      }

      SettingAttribute sshSettingAttribute =
          settingValidationService.getAndDecryptSettingAttribute(gitConfig.getSshSettingId());
      if (sshSettingAttribute == null) {
        throw new InvalidRequestException("Could not find SettingAttribute for Id: " + gitConfig.getSshSettingId());
      } else {
        gitConfig.setSshSettingAttribute(sshSettingAttribute);
      }
    } else {
      if (gitConfig.getSshSettingId() != null) {
        SettingAttribute sshSettingAttribute = settingsService.get(gitConfig.getSshSettingId());
        if (sshSettingAttribute == null) {
          throw new InvalidRequestException("Could not find SettingAttribute for Id: " + gitConfig.getSshSettingId());
        }
        gitConfig.setSshSettingAttribute(sshSettingAttribute);
      } else if (gitConfig.getUsername() == null
          || (gitConfig.getPassword() == null && gitConfig.getEncryptedPassword() == null)) {
        throw new InvalidRequestException("Username and password can not be empty", USER);
      }
    }

    if (gitConfig.getUrlType() == GitConfig.UrlType.ACCOUNT) {
      if (!featureFlagService.isEnabled(FeatureName.GIT_ACCOUNT_SUPPORT, gitConfig.getAccountId())) {
        throw new InvalidRequestException("Account level git connector is not enabled", USER);
      }

      // Cannot throw exception here as validation is being called at many places and gitConfig.repoName is transient.
      if (isEmpty(gitConfig.getRepoName())) {
        return;
      }
    }

    convertToRepoGitConfig(gitConfig, gitConfig.getRepoName());

    try {
      DelegateResponseData notifyResponseData = delegateService.executeTask(
          DelegateTask.builder()
              .accountId(gitConfig.getAccountId())
              .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, SCOPE_WILDCARD)
              .data(TaskData.builder()
                        .async(false)
                        .taskType(TaskType.GIT_COMMAND.name())
                        .parameters(new Object[] {GitCommandType.VALIDATE, gitConfig, encryptionDetails})
                        .timeout(TimeUnit.SECONDS.toMillis(60))
                        .build())
              .build());

      if (notifyResponseData instanceof ErrorNotifyResponseData) {
        throw new WingsException(((ErrorNotifyResponseData) notifyResponseData).getErrorMessage());
      } else if ((notifyResponseData instanceof RemoteMethodReturnValueData)
          && (((RemoteMethodReturnValueData) notifyResponseData).getException() instanceof InvalidRequestException)) {
        throw(InvalidRequestException)((RemoteMethodReturnValueData) notifyResponseData).getException();
      } else if (!(notifyResponseData instanceof GitCommandExecutionResponse)) {
        throw new WingsException(ErrorCode.GENERAL_ERROR)
            .addParam("message", "Unknown Response from delegate")
            .addContext(DelegateResponseData.class, notifyResponseData);
      }

      GitCommandExecutionResponse gitCommandExecutionResponse = (GitCommandExecutionResponse) notifyResponseData;

      log.info(GIT_YAML_LOG_PREFIX + "GitConfigValidation [{}]", gitCommandExecutionResponse);

      if (gitCommandExecutionResponse.getGitCommandStatus() == GitCommandStatus.FAILURE) {
        throw new WingsException(ErrorCode.INVALID_CREDENTIAL).addParam("message", "Invalid git credentials.");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InvalidRequestException(
          "Thread was interrupted. Please try again. " + e.getMessage(), WingsException.USER);
    }
  }

  /**
   * If GitConfig has keyAuth enabled, and fetch SshKeySettingAttribute using sshSettingId
   * and set it into gitConfig.
   *
   * @param gitConfig
   */
  public void setSshKeySettingAttributeIfNeeded(GitConfig gitConfig) {
    if (gitConfig.isKeyAuth() && StringUtils.isNotBlank(gitConfig.getSshSettingId())) {
      SettingAttribute settingAttribute = settingsService.get(gitConfig.getSshSettingId());
      if (settingAttribute != null && settingAttribute.getValue() != null) {
        HostConnectionAttributes attributeValue = (HostConnectionAttributes) settingAttribute.getValue();
        List<EncryptedDataDetail> encryptionDetails =
            secretManager.getEncryptionDetails(attributeValue, GLOBAL_APP_ID, null);
        managerDecryptionService.decrypt(attributeValue, encryptionDetails);
        ExceptionMessageSanitizer.storeAllSecretsForSanitizing(attributeValue, encryptionDetails);
        gitConfig.setSshSettingAttribute(settingAttribute);
      }
    }
  }

  public void renderGitConfig(ExecutionContext context, GitConfig gitConfig) {
    if (context == null) {
      return;
    }

    if (gitConfig.getBranch() != null) {
      gitConfig.setBranch(context.renderExpression(gitConfig.getBranch()).trim());
    }

    if (gitConfig.getReference() != null) {
      gitConfig.setReference(context.renderExpression(gitConfig.getReference().trim()));
    }

    if (gitConfig.getRepoUrl() != null) {
      gitConfig.setRepoUrl(context.renderExpression(gitConfig.getRepoUrl().trim()));
    }
  }

  public void convertToRepoGitConfig(GitConfig gitConfig, String repoName) {
    notNullCheck("GitConfig provided cannot be null", gitConfig);
    if (GitConfig.UrlType.ACCOUNT == gitConfig.getUrlType()) {
      String repositoryUrl = getRepositoryUrl(gitConfig, repoName);
      gitConfig.setRepoUrl(repositoryUrl);
    }

    gitConfig.setRepoName(repoName);
    gitConfig.setUrlType(GitConfig.UrlType.REPO);
  }

  private String getDisplayRepositoryUrl(String repositoryUrl) {
    try {
      URIish uri = new URIish(repositoryUrl);
      String path = uri.getPath();
      path = StringUtils.removeEnd(path, "/");
      path = StringUtils.removeEnd(path, ".git");
      return StringUtils.removeStart(path, "/");
    } catch (URISyntaxException e) {
      log.error("Failed to generate Display Repository Url {}", repositoryUrl, e);
    }
    return repositoryUrl;
  }

  private GitRepositoryInfo.GitProvider getGitProvider(String repositoryUrl) {
    try {
      URIish uri = new URIish(repositoryUrl);
      String host = uri.getHost();
      if (null != host) {
        Optional<GitRepositoryInfo.GitProvider> provider =
            Arrays.stream(GitRepositoryInfo.GitProvider.values())
                .filter(
                    p -> p != GitRepositoryInfo.GitProvider.UNKNOWN && StringUtils.containsIgnoreCase(host, p.name()))
                .findFirst();
        if (provider.isPresent()) {
          return provider.get();
        }
      }
    } catch (Exception e) {
      log.error("Failed to generate Git Provider Repository Url {}", repositoryUrl, e);
    }
    return GitRepositoryInfo.GitProvider.UNKNOWN;
  }

  public GitRepositoryInfo createRepositoryInfo(GitConfig gitConfig, String repositoryName) {
    if (null != gitConfig) {
      String repositoryUrl = getRepositoryUrl(gitConfig, repositoryName);
      String displayUrl = getDisplayRepositoryUrl(repositoryUrl);
      GitRepositoryInfo.GitProvider provider = getGitProvider(repositoryUrl);
      return GitRepositoryInfo.builder().url(repositoryUrl).displayUrl(displayUrl).provider(provider).build();
    } else {
      return GitRepositoryInfo.builder()
          .url(UNKNOWN_GIT_CONNECTOR)
          .displayUrl(UNKNOWN_GIT_CONNECTOR)
          .provider(GitRepositoryInfo.GitProvider.UNKNOWN)
          .build();
    }
  }

  public String getRepositoryUrl(GitConfig gitConfig, String repoName) {
    notNullCheck("GitConfig provided cannot be null", gitConfig);
    if (GitConfig.UrlType.ACCOUNT == gitConfig.getUrlType()) {
      repoName = trimToEmpty(repoName);
      notEmptyCheck("Repo name cannot be empty for Account level git connector", repoName);
      String purgedRepoUrl = gitConfig.getRepoUrl().replaceAll("/*$", "");
      String purgedRepoName = repoName.replaceAll("^/*", "");
      return purgedRepoUrl + "/" + purgedRepoName;
    }
    return gitConfig.getRepoUrl();
  }
}
