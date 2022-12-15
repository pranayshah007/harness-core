/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.logging.CommandExecutionStatus;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

public class HelmFetchChartVersionTaskNG extends AbstractDelegateRunnableTask {
  public static final long DEFAULT_TIMEOUT = 60000L;

  public static final int MAX_RETRIES = 10;
  private static final String MANIFEST_COLLECTION_DIR = "manifest-collection";
  @Inject private HelmTaskHelperBase helmTaskHelperBase;

  public HelmFetchChartVersionTaskNG(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public HelmFetchChartVersionResponse run(TaskParameters parameters) throws IOException {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    HelmFetchChartVersionRequestNG helmFetchChartVersionRequestNG = (HelmFetchChartVersionRequestNG) parameters;
    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
        helmFetchChartVersionRequestNG.getHelmChartManifestDelegateConfig();
    StoreDelegateConfig storeDelegateConfig = helmChartManifestDelegateConfig.getStoreDelegateConfig();
    String destinationDirectory = getDestinationDirectory(storeDelegateConfig);
    List<String> chartVersions = null;
    try {
      helmTaskHelperBase.decryptEncryptedDetails(helmChartManifestDelegateConfig);
      chartVersions =
          helmTaskHelperBase.fetchChartVersions(helmChartManifestDelegateConfig, DEFAULT_TIMEOUT, destinationDirectory);
    } catch (Exception e) {
      throw new TaskNGDataException(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), e);
    }

    return HelmFetchChartVersionResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .chartVersionsList(chartVersions)
        .build();
  }

  public String getDestinationDirectory(StoreDelegateConfig storeDelegateConfig) throws IOException {
    String destinationDirectory = "";

    if (storeDelegateConfig instanceof HttpHelmStoreDelegateConfig) {
      HttpHelmStoreDelegateConfig httpHelmStoreDelegateConfig = (HttpHelmStoreDelegateConfig) storeDelegateConfig;
      destinationDirectory =
          MANIFEST_COLLECTION_DIR + "/" + convertBase64UuidToCanonicalForm(httpHelmStoreDelegateConfig.getRepoName());
      createDirectoryIfDoesNotExist(destinationDirectory);
      waitForDirectoryToBeAccessibleOutOfProcess(destinationDirectory, MAX_RETRIES);
    } else if (storeDelegateConfig instanceof S3HelmStoreDelegateConfig) {
      S3HelmStoreDelegateConfig s3HelmStoreConfig = (S3HelmStoreDelegateConfig) storeDelegateConfig;
      destinationDirectory = MANIFEST_COLLECTION_DIR + "/"
          + convertBase64UuidToCanonicalForm(s3HelmStoreConfig.getRepoName()) + "-" + s3HelmStoreConfig.getBucketName();
      createDirectoryIfDoesNotExist(destinationDirectory);
      waitForDirectoryToBeAccessibleOutOfProcess(destinationDirectory, MAX_RETRIES);
    } else if (storeDelegateConfig instanceof GcsHelmStoreDelegateConfig) {
      GcsHelmStoreDelegateConfig gcsHelmStoreConfig = (GcsHelmStoreDelegateConfig) storeDelegateConfig;
      destinationDirectory = MANIFEST_COLLECTION_DIR + "/"
          + convertBase64UuidToCanonicalForm(gcsHelmStoreConfig.getRepoName()) + "-"
          + gcsHelmStoreConfig.getBucketName();
      createDirectoryIfDoesNotExist(destinationDirectory);
      waitForDirectoryToBeAccessibleOutOfProcess(destinationDirectory, MAX_RETRIES);
    }

    return destinationDirectory;
  }
}
