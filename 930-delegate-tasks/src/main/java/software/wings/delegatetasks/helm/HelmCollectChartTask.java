/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static software.wings.delegatetasks.helm.ArtifactoryHelmTaskHelper.shouldFetchHelmChartsFromArtifactory;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.perpetualtask.manifest.ManifestRepositoryService;

import software.wings.beans.appmanifest.HelmChart;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams.HelmChartCollectionType;
import software.wings.helpers.ext.helm.response.HelmCollectChartResponse;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class HelmCollectChartTask extends AbstractDelegateRunnableTask {
  @Inject private DelegateLogService delegateLogService;
  @Inject
  @Named(ManifestRepoServiceType.HELM_COMMAND_SERVICE)
  private ManifestRepositoryService helmCommandRepositoryService;
  @Inject
  @Named(ManifestRepoServiceType.ARTIFACTORY_HELM_SERVICE)
  private ManifestRepositoryService artifactoryHelmRepositoryService;

  public HelmCollectChartTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public HelmCollectChartResponse run(TaskParameters parameters) {
    HelmChartCollectionParams taskParams = (HelmChartCollectionParams) parameters;
    log.info(
        format("Running Helm Collect Chart for account %s app %s", taskParams.getAccountId(), taskParams.getAppId()));

    try {
      List<HelmChart> helmCharts;
      if (shouldFetchHelmChartsFromArtifactory(taskParams.getHelmChartConfigParams())) {
        helmCharts = artifactoryHelmRepositoryService.collectManifests(taskParams);
      } else {
        helmCharts = helmCommandRepositoryService.collectManifests(taskParams);
      }

      if (taskParams.getCollectionType() == HelmChartCollectionType.SPECIFIC_VERSION) {
        // that specific version is found
        if (helmCharts.size() == 1
            && helmCharts.get(0).getVersion().equals(taskParams.getHelmChartConfigParams().getChartVersion())) {
          return HelmCollectChartResponse.builder().commandExecutionStatus(SUCCESS).helmCharts(helmCharts).build();
        } else {
          return HelmCollectChartResponse.builder().commandExecutionStatus(SUCCESS).helmCharts(null).build();
        }
      } else {
        return HelmCollectChartResponse.builder().commandExecutionStatus(SUCCESS).helmCharts(helmCharts).build();
      }

    } catch (Exception e) {
      log.error("HelmCollectChartTask execution failed with exception ", e);

      return HelmCollectChartResponse.builder()
          .commandExecutionStatus(FAILURE)
          .errorMessage(
              "Execution failed with Exception: " + ExceptionMessageSanitizer.sanitizeException(e).getMessage())
          .build();
    }
  }

  @Override
  public HelmCollectChartResponse run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }
}
