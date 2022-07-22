/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ci.integrationstage;

import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_EVENT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_TAG;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_SSL_NO_VERIFY;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public class InitializeStepUtils {
  private InitializeStepUtils() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  //Overloaded for use with VMs - the base method is setup to handle nulls accountId and Os.
  public static PluginStepInfo createPluginStepInfo(GitCloneStepInfo gitCloneStepInfo,
                                                    CIExecutionConfigService ciExecutionConfigService) {
    return createPluginStepInfo(gitCloneStepInfo, ciExecutionConfigService, null, null);
  }

  /**
   * Create Plugin step info
   * Given a gitCloneStepInfo convert it into a PluginStepInfo (which is what codebase used originally for git clone)
   * @return PluginStepInfo with values set from GitCloneStepInfo
   */
  public static PluginStepInfo createPluginStepInfo(GitCloneStepInfo gitCloneStepInfo,
                                                    CIExecutionConfigService ciExecutionConfigService, String accountId,
                                                    OSType os) {
    Map<String, JsonNode> settings = new HashMap<>();

    Pair<String, String> buildEnvVar = getBuildEnvVar(gitCloneStepInfo);

    Integer depth = null;
    final ParameterField<Integer> depthParameter = gitCloneStepInfo.getDepth();
    if (depthParameter == null || depthParameter.getValue() == null) {
      if (buildEnvVar != null && isNotEmpty(buildEnvVar.getValue())) {
        depth = GIT_CLONE_MANUAL_DEPTH;
      }
    }

    if (depth != null && depth != 0) {
      settings.put(GIT_CLONE_DEPTH_ATTRIBUTE, JsonNodeFactory.instance.textNode(depth.toString()));
    }

    Map<String, String> envVariables = new HashMap<>();
    if (gitCloneStepInfo.getSslVerify() != null && gitCloneStepInfo.getSslVerify().getValue() != null
            && !gitCloneStepInfo.getSslVerify().getValue()) {
      envVariables.put(GIT_SSL_NO_VERIFY, "true");
    }
    if (buildEnvVar != null) {
      String type = buildEnvVar.getKey();
      envVariables.put(type, buildEnvVar.getValue());
      if(DRONE_TAG.equals(type)) {
        envVariables.put(DRONE_BUILD_EVENT, "tag");
      }
    }

    CIExecutionServiceConfig ciExecutionServiceConfig = ciExecutionConfigService.getCiExecutionServiceConfig();
    List<String> entrypoint = Collections.emptyList();
    if (ciExecutionServiceConfig != null && ciExecutionServiceConfig.getStepConfig() != null
            && ciExecutionServiceConfig.getStepConfig().getGitCloneConfig() != null) {
      entrypoint = ciExecutionServiceConfig.getStepConfig().getGitCloneConfig().getEntrypoint();
      if (OSType.Windows == os) {
        entrypoint = ciExecutionServiceConfig.getStepConfig().getGitCloneConfig().getWindowsEntrypoint();
      }
    }

    PluginStepInfo step = PluginStepInfo.builder()
            .connectorRef(gitCloneStepInfo.getConnectorRef())
            .identifier(gitCloneStepInfo.getIdentifier())
            .name(gitCloneStepInfo.getName())
            .settings(ParameterField.createValueField(settings))
            .envVariables(envVariables)
            .entrypoint(entrypoint)
            .harnessManagedImage(true)
            .resources(gitCloneStepInfo.getResources())
            .privileged(ParameterField.createValueField(null))
            .reports(ParameterField.createValueField(null))
            .build();

    if (isNotEmpty(accountId)) {
      String gitCloneImage =
              ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GIT_CLONE, accountId).getImage();
      step.setImage(ParameterField.createValueField(gitCloneImage));
    }
    return step;
  }

  /**
   * Get Build Env variable - branch or tag
   *
   * @param gitCloneStepInfo gitCloneStepInfo
   * @return a pair containing whether the build is configured for a branch or tag, and the value of the branch or tag
   */
  private static Pair<String, String> getBuildEnvVar(GitCloneStepInfo gitCloneStepInfo) {
    final String identifier = gitCloneStepInfo.getIdentifier();
    final String type = gitCloneStepInfo.getStepType().getType();
    Pair<String, String> buildEnvVar = null;
    Build build = RunTimeInputHandler.resolveBuild(gitCloneStepInfo.getBuild());

    final Pair<BuildType, String> buildTypeAndValue = IntegrationStageUtils.getBuildTypeAndValue(build);
    if(buildTypeAndValue != null) {
      final String buildValue = buildTypeAndValue.getValue();
      switch (buildTypeAndValue.getKey()) {
        case PR:
          throw new CIStageExecutionException(format("%s is not a valid build type in step type %s with identifier %s",
                  BuildType.PR, type, identifier));
        case BRANCH:
          if (isNotEmpty(buildValue)) {
            buildEnvVar = new ImmutablePair<>(DRONE_COMMIT_BRANCH, buildValue);
          } else {
            throw new CIStageExecutionException("Branch should not be empty for branch build type");
          }
          break;
        case TAG:
          if (isNotEmpty(buildValue)) {
            buildEnvVar = new ImmutablePair<>(DRONE_TAG, buildValue);
          } else {
            throw new CIStageExecutionException("Tag should not be empty for tag build type");
          }
          break;
      }
    }
    return buildEnvVar;
  }
}
