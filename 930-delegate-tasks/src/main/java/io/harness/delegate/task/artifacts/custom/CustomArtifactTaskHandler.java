/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.custom;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class CustomArtifactTaskHandler extends DelegateArtifactTaskHandler<CustomArtifactDelegateRequest> {
  @Inject ShellScriptTaskNG shellScriptTaskNG;
  @Override
  public void decryptRequestDTOs(CustomArtifactDelegateRequest dto) {}

  @Override
  public ArtifactTaskExecutionResponse getBuilds(CustomArtifactDelegateRequest attributesRequest) {
    Map<String, String> environmentVariables = attributesRequest.getInputs().entrySet().stream().collect(
        Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue()));
    ShellScriptTaskParametersNG shellScriptTaskParametersNG = ShellScriptTaskParametersNG.builder()
                                                                  .script(attributesRequest.getScript())
                                                                  .scriptType(attributesRequest.getScriptType())
                                                                  .environmentVariables(environmentVariables)
                                                                  .build();
    DelegateResponseData shellScriptTaskResponseNG = shellScriptTaskNG.run(shellScriptTaskParametersNG);
    return null;
  }

  @Override
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(CustomArtifactDelegateRequest attributesRequest) {
    Map<String, String> environmentVariables = attributesRequest.getInputs().entrySet().stream().collect(
        Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue()));
    ShellScriptTaskParametersNG shellScriptTaskParametersNG = ShellScriptTaskParametersNG.builder()
                                                                  .script(attributesRequest.getScript())
                                                                  .scriptType(attributesRequest.getScriptType())
                                                                  .environmentVariables(environmentVariables)
                                                                  .build();
    DelegateResponseData shellScriptTaskResponseNG = shellScriptTaskNG.run(shellScriptTaskParametersNG);
    return null;
  }
}
