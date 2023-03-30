/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s.openshift;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.cli.CliResponse;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.OpenShiftClientException;
import io.harness.filesystem.FileIo;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.openshift.OpenShiftClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class OpenShiftDelegateService {
  @Inject private OpenShiftClient openShiftClient;

  public List<FileData> processTemplatization(@NotEmpty String manifestFilesDirectory, @NotEmpty String ocBinaryPath,
      @NotEmpty String templateFilePath, LogCallback executionLogCallback, List<String> paramFilesContent) {
    List<String> paramsFilePaths = null;
    if (isNotEmpty(paramFilesContent)) {
      paramsFilePaths = writeParamsToFile(manifestFilesDirectory, paramFilesContent);
    }

    String command = openShiftClient.generateOcCommand(ocBinaryPath, templateFilePath, paramsFilePaths);

    CliResponse cliResponse = openShiftClient.process(command, manifestFilesDirectory, executionLogCallback);

    if (cliResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      String processedTemplateFile = cliResponse.getOutput();
      if (isEmpty(processedTemplateFile)) {
        String errorMsg = "Oc process result can't be empty";
        throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.INVALID_OPENSHIFT_FILES,
            errorMsg,
            new OpenShiftClientException(format("Executing command [%s] produced empty result", command), USER));
      }
      String kubernetesReadyYaml = prepareKubernetesReadyYaml(processedTemplateFile);

      return Collections.singletonList(
          FileData.builder().fileName("manifest.yaml").fileContent(kubernetesReadyYaml).build());
    } else {
      throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.INVALID_OPENSHIFT_FILES,
          format(KubernetesExceptionExplanation.OPENSHIFT_RENDER_ERROR, cliResponse.getError(), command),
          new OpenShiftClientException(
              format("Failed to render OpenShift template. %s", cliResponse.getError()), USER));
    }
  }

  // Get resources from oc process result and appends with yaml delimiter ---
  private String prepareKubernetesReadyYaml(String processedTemplateFile) {
    StringBuilder resultYaml = new StringBuilder();
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(new DumperOptions()), options);

    Object obj = yaml.load(processedTemplateFile);
    if (obj instanceof Map) {
      Object items = ((Map) obj).get("items");
      if (items instanceof List) {
        List itemsList = (List) items;

        for (int i = 0; i < itemsList.size(); i++) {
          Object item = itemsList.get(i);

          if (item instanceof Map) {
            String yamlResource = yaml.dump(item);
            resultYaml.append(yamlResource);
            if (i != itemsList.size() - 1) {
              resultYaml.append("\n---\n");
            }
          }
        }
      }
    }
    if (isEmpty(resultYaml.toString())) {
      String errorMsg = "Items list can't be empty";
      throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.INVALID_OPENSHIFT_FILES,
          errorMsg, new OpenShiftClientException(format("Failed to render OpenShift template. %s", errorMsg), USER));
    }
    return resultYaml.toString();
  }

  private List<String> writeParamsToFile(String directoryPath, List<String> paramFiles) {
    List<String> paramsFilePaths = new ArrayList<>();

    for (int i = 0; i < paramFiles.size(); i++) {
      String paramsFileName = format("params-%d", i);
      try {
        FileIo.writeUtf8StringToFile(directoryPath + '/' + paramsFileName, paramFiles.get(i));
      } catch (IOException e) {
        String errorMsg = "IO Failure while writing params file. " + e.getMessage();
        throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.INVALID_OPENSHIFT_FILES,
            errorMsg,
            new OpenShiftClientException(format("Failed to render OpenShift template. %s", errorMsg, e), USER));
      }
      paramsFilePaths.add(paramsFileName);
    }

    return paramsFilePaths;
  }
}
