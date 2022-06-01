/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.govern.Switch.unhandled;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;

import static software.wings.settings.SettingVariableTypes.OCI_HELM_REPO;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.k8s.model.HelmVersion;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.beans.settings.helm.HelmRepoConfig;
import software.wings.beans.settings.helm.HelmRepoConfigValidationResponse;
import software.wings.beans.settings.helm.HelmRepoConfigValidationTaskParams;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.beans.settings.helm.OciHelmRepoConfig;
import software.wings.delegatetasks.helm.HelmTaskHelper;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class HelmRepoConfigValidationTask extends AbstractDelegateRunnableTask {
  private static final String WORKING_DIR_BASE = "./repository/helm-validation/";
  private static final HelmVersion defaultHelmVersion = HelmVersion.V3;
  // We do not want to generate index when validating a helm repo, so a dummy path serves the purpose by checking if the
  // s3/gcs bucket is accessible or not
  private static final String DUMMY_BASE_PATH = generateUuid();
  private static final long DEFAULT_TIMEOUT_IN_MILLIS = Duration.ofMinutes(DEFAULT_STEADY_STATE_TIMEOUT).toMillis();

  @Inject private EncryptionService encryptionService;
  @Inject private HelmTaskHelper helmTaskHelper;

  public HelmRepoConfigValidationTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public HelmRepoConfigValidationResponse run(TaskParameters parameters) {
    HelmRepoConfigValidationTaskParams taskParams = (HelmRepoConfigValidationTaskParams) parameters;

    try {
      log.info("Running HelmRepoConfigValidationTask for account {} app {}", taskParams.getAccountId(),
          taskParams.getAppId());

      tryAddingHelmRepo(taskParams);

      return HelmRepoConfigValidationResponse.builder().commandExecutionStatus(SUCCESS).build();
    } catch (Exception e) {
      log.warn("HelmRepoConfigValidationTask execution failed with exception " + e);
      return HelmRepoConfigValidationResponse.builder()
          .commandExecutionStatus(FAILURE)
          .errorMessage(ExceptionUtils.getMessage(e))
          .build();
    }
  }

  @Override
  public HelmRepoConfigValidationResponse run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  private void tryAddingHelmRepo(HelmRepoConfigValidationTaskParams taskParams) throws Exception {
    HelmRepoConfig helmRepoConfig = taskParams.getHelmRepoConfig();
    List<EncryptedDataDetail> encryptedDataDetails = taskParams.getEncryptedDataDetails();
    encryptionService.decrypt(helmRepoConfig, encryptedDataDetails, false);

    String workingDirectory = helmTaskHelper.createNewDirectoryAtPath(Paths.get(WORKING_DIR_BASE).toString());

    helmTaskHelper.initHelm(workingDirectory, defaultHelmVersion, DEFAULT_TIMEOUT_IN_MILLIS);
    String repoName = convertBase64UuidToCanonicalForm(generateUuid());

    if (helmRepoConfig instanceof AmazonS3HelmRepoConfig) {
      ((AmazonS3HelmRepoConfig) helmRepoConfig)
          .setUseLatestChartMuseumVersion(taskParams.isUseLatestChartMuseumVersion());

    } else if (helmRepoConfig instanceof GCSHelmRepoConfig) {
      ((GCSHelmRepoConfig) helmRepoConfig).setUseLatestChartMuseumVersion(taskParams.isUseLatestChartMuseumVersion());
    }

    if (!taskParams.isUseOCIHelmRepo() && helmRepoConfig.getSettingType().equals(OCI_HELM_REPO)) {
      unhandled(helmRepoConfig.getSettingType());
      throw new WingsException("Unhandled type of helm repo config. Type : " + helmRepoConfig.getSettingType());
    }

    switch (helmRepoConfig.getSettingType()) {
      case HTTP_HELM_REPO:
        tryAddingHttpHelmRepo(helmRepoConfig, repoName, taskParams.getRepoDisplayName(), workingDirectory);
        break;

      case OCI_HELM_REPO:
        tryLoginOciRegistry(helmRepoConfig, HelmVersion.V380, workingDirectory);
        break;
      case AMAZON_S3_HELM_REPO:
        tryAddingAmazonS3HelmRepo(helmRepoConfig, repoName, taskParams, workingDirectory);
        break;

      case GCS_HELM_REPO:
        tryAddingGCSHelmRepo(helmRepoConfig, repoName, taskParams, workingDirectory);
        break;

      default:
        unhandled(helmRepoConfig.getSettingType());
        throw new WingsException("Unhandled type of helm repo config. Type : " + helmRepoConfig.getSettingType());
    }

    helmTaskHelper.removeRepo(repoName, workingDirectory, defaultHelmVersion, DEFAULT_TIMEOUT_IN_MILLIS);
    helmTaskHelper.cleanup(workingDirectory);
  }

  private void tryLoginOciRegistry(HelmRepoConfig helmRepoConfig, HelmVersion helmVersion, String workingDirectory) {
    helmTaskHelper.loginOciRegistry(
        (OciHelmRepoConfig) helmRepoConfig, helmVersion, DEFAULT_TIMEOUT_IN_MILLIS, workingDirectory);
  }

  private void tryAddingGCSHelmRepo(HelmRepoConfig helmRepoConfig, String repoName,
      HelmRepoConfigValidationTaskParams taskParams, String workingDirectory) throws Exception {
    GcpConfig gcpConfig = (GcpConfig) taskParams.getConnectorConfig();
    List<EncryptedDataDetail> connectorEncryptedDataDetails = taskParams.getConnectorEncryptedDataDetails();
    encryptionService.decrypt(gcpConfig, connectorEncryptedDataDetails, false);

    helmTaskHelper.addHelmRepo(helmRepoConfig, gcpConfig, repoName, taskParams.getRepoDisplayName(), workingDirectory,
        DUMMY_BASE_PATH, defaultHelmVersion, ((GCSHelmRepoConfig) helmRepoConfig).isUseLatestChartMuseumVersion());
  }

  private void tryAddingHttpHelmRepo(HelmRepoConfig helmRepoConfig, String repoName, String repoDisplayName,
      String workingDirectory) throws Exception {
    HttpHelmRepoConfig httpHelmRepoConfig = (HttpHelmRepoConfig) helmRepoConfig;

    helmTaskHelper.addRepo(repoName, repoDisplayName, httpHelmRepoConfig.getChartRepoUrl(),
        httpHelmRepoConfig.getUsername(), httpHelmRepoConfig.getPassword(), workingDirectory, defaultHelmVersion,
        DEFAULT_TIMEOUT_IN_MILLIS);
  }

  private void tryAddingAmazonS3HelmRepo(HelmRepoConfig helmRepoConfig, String repoName,
      HelmRepoConfigValidationTaskParams taskParams, String workingDirectory) throws Exception {
    AwsConfig awsConfig = (AwsConfig) taskParams.getConnectorConfig();
    List<EncryptedDataDetail> connectorEncryptedDataDetails = taskParams.getConnectorEncryptedDataDetails();
    encryptionService.decrypt(awsConfig, connectorEncryptedDataDetails, false);

    helmTaskHelper.addHelmRepo(helmRepoConfig, awsConfig, repoName, taskParams.getRepoDisplayName(), workingDirectory,
        DUMMY_BASE_PATH, defaultHelmVersion, ((AmazonS3HelmRepoConfig) helmRepoConfig).isUseLatestChartMuseumVersion());
  }
}
