/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s.kustomize;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.kustomize.KustomizeExceptionConstants.ACCUMULATING_RESOURCES;
import static io.harness.kustomize.KustomizeExceptionConstants.EVALSYMLINK_ERROR_EXPLANATION;
import static io.harness.kustomize.KustomizeExceptionConstants.EVALSYMLINK_ERROR_HINT;
import static io.harness.kustomize.KustomizeExceptionConstants.EVALSYMLINK_FAILURE;
import static io.harness.kustomize.KustomizeExceptionConstants.KUSTOMIZE_BUILD_FAILED_EXPLANATION;
import static io.harness.kustomize.KustomizeExceptionConstants.KUSTOMIZE_BUILD_FAILED_HINT;
import static io.harness.kustomize.KustomizeExceptionConstants.KUSTOMIZE_IO_EXCEPTION_HINT;
import static io.harness.kustomize.KustomizeExceptionConstants.KUSTOMIZE_IO_EXPLANATION;
import static io.harness.kustomize.KustomizeExceptionConstants.KUSTOMIZE_TIMEOUT_EXCEPTION_HINT;
import static io.harness.kustomize.KustomizeExceptionConstants.KUSTOMIZE_TIMEOUT_EXPLANATION;
import static io.harness.kustomize.KustomizeExceptionConstants.RESOURCE_NOT_FOUND;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.cli.CliResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.kustomize.KustomizeClient;
import io.harness.kustomize.KustomizeClientFactory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import org.hibernate.validator.constraints.NotEmpty;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDP)
@Singleton
public class KustomizeTaskHelper {
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  private static final Pattern ACCUMULATING_RESOURCES_PATH_PATTERN =
      Pattern.compile("accumulating resources from '(.*?)'");
  @Inject private KustomizeClientFactory kustomizeClientFactory;

  @Nonnull
  public List<FileData> build(@Nonnull String manifestFilesDirectory, @Nonnull String kustomizeBinaryPath,
      String pluginRootDir, String kustomizeDirPath, LogCallback executionLogCallback) {
    CliResponse cliResponse;
    // ToDo: set command-flags correctly
    KustomizeClient kustomizeClient = kustomizeClientFactory.getClient(kustomizeBinaryPath, Collections.emptyMap());
    try {
      if (isBlank(pluginRootDir)) {
        cliResponse = kustomizeClient.build(manifestFilesDirectory, kustomizeDirPath, executionLogCallback);

      } else {
        cliResponse = kustomizeClient.buildWithPlugins(
            manifestFilesDirectory, kustomizeDirPath, pluginRootDir, executionLogCallback);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InvalidRequestException("Kustomize build interrupted", e, WingsException.USER);
    } catch (TimeoutException e) {
      throw NestedExceptionUtils.hintWithExplanationException(KUSTOMIZE_TIMEOUT_EXCEPTION_HINT,
          KUSTOMIZE_TIMEOUT_EXPLANATION,
          new InvalidRequestException("Kustomize build timed out", e, WingsException.USER));
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException(KUSTOMIZE_IO_EXCEPTION_HINT, KUSTOMIZE_IO_EXPLANATION,
          new InvalidRequestException("IO Failure occurred while running kustomize build", e, WingsException.USER));
    }

    if (cliResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      return Collections.singletonList(
          FileData.builder().fileName("manifest.yaml").fileContent(cliResponse.getOutput()).build());
    } else {
      StringBuilder stringBuilder = new StringBuilder("Kustomize build failed.");
      String cliErrorMessage = cliResponse.getError();
      if (isNotBlank(cliResponse.getOutput())) {
        stringBuilder.append(" Msg: ").append(cliResponse.getOutput());
      }

      if (isNotEmpty(cliErrorMessage) && cliErrorMessage.contains(EVALSYMLINK_FAILURE)
          && cliErrorMessage.contains(ACCUMULATING_RESOURCES)) {
        throw NestedExceptionUtils.hintWithExplanationException(EVALSYMLINK_ERROR_HINT, EVALSYMLINK_ERROR_EXPLANATION,
            new InvalidRequestException(
                RESOURCE_NOT_FOUND.replace("${RESOURCE_PATH}", getMissingResourcePath(cliErrorMessage)),
                WingsException.USER));
      }

      throw NestedExceptionUtils.hintWithExplanationException(KUSTOMIZE_BUILD_FAILED_HINT,
          KUSTOMIZE_BUILD_FAILED_EXPLANATION,
          new InvalidRequestException(
              isEmpty(cliResponse.getOutput()) ? cliErrorMessage : stringBuilder.toString(), WingsException.USER));
    }
  }

  @NotNull
  public List<FileData> buildForApply(@Nonnull String kustomizeBinaryPath, String pluginRootDir,
      @Nonnull String manifestFilesDirectory, @NotEmpty List<String> filesToApply, boolean useLatestKustomizeVersion,
      List<String> kustomizePatchesFiles, LogCallback executionLogCallback) {
    if (isEmpty(filesToApply)) {
      throw new InvalidRequestException("Apply files can't be empty", USER);
    }
    if (filesToApply.size() > 1) {
      throw new InvalidRequestException("Apply with Kustomize is supported for single file only", USER);
    }
    if (useLatestKustomizeVersion) {
      String kustomizePath = Paths.get(manifestFilesDirectory, filesToApply.get(0)).toString();
      k8sTaskHelperBase.savingPatchesToDirectory(kustomizePath, kustomizePatchesFiles, executionLogCallback);
    }
    String kustomizeDirPath = filesToApply.get(0);
    return build(manifestFilesDirectory, kustomizeBinaryPath, pluginRootDir, kustomizeDirPath, executionLogCallback);
  }

  private String getMissingResourcePath(String errorMessage) {
    Matcher matcher = ACCUMULATING_RESOURCES_PATH_PATTERN.matcher(errorMessage);
    return matcher.find() ? matcher.group(1) : errorMessage;
  }
}
