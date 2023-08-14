/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.govern.Switch.unhandled;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
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
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class HelmRepoConfigValidationTask extends AbstractDelegateRunnableTask {
  private static final String WORKING_DIR_BASE = "./repository/helm-validation/";
  private HelmVersion helmVersion = HelmVersion.V3;
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

      if (taskParams.isUseNewHelmBinary()) {
        helmVersion = HelmVersion.V380;
      }

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

    helmTaskHelper.initHelm(workingDirectory, helmVersion, DEFAULT_TIMEOUT_IN_MILLIS);
    String repoName = convertBase64UuidToCanonicalForm(generateUuid());

    try {
      switch (helmRepoConfig.getSettingType()) {
        case HTTP_HELM_REPO:
          tryAddingHttpHelmRepo(
              helmRepoConfig, repoName, taskParams.getRepoDisplayName(), workingDirectory, taskParams.isUseCache());
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
    } finally {
      // expecting remove repo to not throw any failures if the repo was already removed or something went wrong
      helmTaskHelper.removeRepo(repoName, workingDirectory, helmVersion, DEFAULT_TIMEOUT_IN_MILLIS);
      helmTaskHelper.cleanup(workingDirectory);
    }
  }

  private void tryLoginOciRegistry(HelmRepoConfig helmRepoConfig, HelmVersion helmVersion, String workingDirectory) {
    helmTaskHelper.loginOciRegistry(
        (OciHelmRepoConfig) helmRepoConfig, helmVersion, DEFAULT_TIMEOUT_IN_MILLIS, workingDirectory, "");
  }

  private void tryAddingGCSHelmRepo(HelmRepoConfig helmRepoConfig, String repoName,
      HelmRepoConfigValidationTaskParams taskParams, String workingDirectory) throws Exception {
    GcpConfig gcpConfig = (GcpConfig) taskParams.getConnectorConfig();
    List<EncryptedDataDetail> connectorEncryptedDataDetails = taskParams.getConnectorEncryptedDataDetails();
    encryptionService.decrypt(gcpConfig, connectorEncryptedDataDetails, false);

    helmTaskHelper.addHelmRepo(helmRepoConfig, gcpConfig, repoName, taskParams.getRepoDisplayName(), workingDirectory,
        DUMMY_BASE_PATH, helmVersion, ((GCSHelmRepoConfig) helmRepoConfig).isUseLatestChartMuseumVersion());
  }

  private void tryAddingHttpHelmRepo(HelmRepoConfig helmRepoConfig, String repoName, String repoDisplayName,
      String workingDirectory, boolean useCache) throws Exception {
    HttpHelmRepoConfig httpHelmRepoConfig = (HttpHelmRepoConfig) helmRepoConfig;

    String cacheDir = helmTaskHelper.getCacheDir(repoName, useCache, helmVersion);
    try {
      helmTaskHelper.tryAddHelmRepo(repoName, repoDisplayName, httpHelmRepoConfig.getChartRepoUrl(),
          httpHelmRepoConfig.getUsername(), httpHelmRepoConfig.getPassword(), workingDirectory, helmVersion,
          DEFAULT_TIMEOUT_IN_MILLIS, cacheDir, null);
    } finally {
      if (isNotEmpty(cacheDir) && !useCache) {
        deleteDirectoryAndItsContentIfExists(Paths.get(cacheDir).getParent().toString());
      }
    }
  }

  private void tryAddingAmazonS3HelmRepo(HelmRepoConfig helmRepoConfig, String repoName,
      HelmRepoConfigValidationTaskParams taskParams, String workingDirectory) throws Exception {
    AwsConfig awsConfig = (AwsConfig) taskParams.getConnectorConfig();
    List<EncryptedDataDetail> connectorEncryptedDataDetails = taskParams.getConnectorEncryptedDataDetails();
    encryptionService.decrypt(awsConfig, connectorEncryptedDataDetails, false);

    helmTaskHelper.addHelmRepo(helmRepoConfig, awsConfig, repoName, taskParams.getRepoDisplayName(), workingDirectory,
        DUMMY_BASE_PATH, helmVersion, ((AmazonS3HelmRepoConfig) helmRepoConfig).isUseLatestChartMuseumVersion());
  }
}
