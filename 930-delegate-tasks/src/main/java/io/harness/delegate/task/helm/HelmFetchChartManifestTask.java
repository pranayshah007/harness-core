/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.task.helm.HelmTaskHelperBase.RESOURCE_DIR_BASE;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.helm.beans.FetchHelmChartManifestRequest;
import io.harness.delegate.task.helm.request.HelmFetchChartManifestTaskParameters;
import io.harness.delegate.task.helm.response.HelmChartManifest;
import io.harness.delegate.task.helm.response.HelmFetchChartManifestResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.filesystem.AutoCloseableWorkingDirectory;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.tuple.Pair;
import org.jose4j.lang.JoseException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
public class HelmFetchChartManifestTask extends AbstractDelegateRunnableTask {
  @Inject private HelmChartManifestTaskService helmChartManifestTaskService;

  public HelmFetchChartManifestTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("This method is deprecated. Use run(TaskParameters) instead.");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    if (!(parameters instanceof HelmFetchChartManifestTaskParameters)) {
      throw new InvalidArgumentsException(Pair.of("parameters",
          format("Invalid task parameters type [%s], expected [%s]", parameters.getClass().getSimpleName(),
              HelmFetchChartManifestTaskParameters.class.getSimpleName())));
    }
    final HelmFetchChartManifestTaskParameters fetchTaskParameters = (HelmFetchChartManifestTaskParameters) parameters;

    final String manifestDirectory = Paths.get(RESOURCE_DIR_BASE, convertBase64UuidToCanonicalForm(generateUuid()))
                                         .normalize()
                                         .toAbsolutePath()
                                         .toString();

    try (AutoCloseableWorkingDirectory ignore = new AutoCloseableWorkingDirectory(manifestDirectory, 10)) {
      final FetchHelmChartManifestRequest request =
          FetchHelmChartManifestRequest.builder()
              .accountId(fetchTaskParameters.getAccountId())
              .manifestDelegateConfig(fetchTaskParameters.getHelmChartConfig())
              .timeoutInMillis(fetchTaskParameters.getTimeoutInMillis())
              .workingDirectory(manifestDirectory)
              .build();

      HelmChartManifest helmChartManifest = helmChartManifestTaskService.fetchHelmChartManifest(request);

      return HelmFetchChartManifestResponse.builder().helmChartManifest(helmChartManifest).build();
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Check delegate file system permissions and space availability",
          format(
              "Unable to create manifest directory [%s] due to: %s", manifestDirectory, ExceptionUtils.getMessage(e)),
          new InvalidRequestException("Failed to create manifest directory"));
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new GeneralException(ExceptionUtils.getMessage(e));
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
