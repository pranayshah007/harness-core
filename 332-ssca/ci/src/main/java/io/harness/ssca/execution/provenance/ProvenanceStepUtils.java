/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution.provenance;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.beans.steps.stepinfo.GCRStepInfo;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.yaml.YamlUtils;
import io.harness.ssca.beans.provenance.DockerSourceSpec;
import io.harness.ssca.beans.provenance.GcrSourceSpec;
import io.harness.ssca.beans.provenance.ProvenanceSource;
import io.harness.ssca.beans.provenance.ProvenanceSourceType;

import io.fabric8.utils.Strings;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.SSCA)
@UtilityClass
public class ProvenanceStepUtils {
  public static final String PROVENANCE_STEP_GROUP = "ProvenanceStepGroup";
  public static final String PROVENANCE_STEP = "Provenance_Step";
  public static ParallelStepElementConfig getParallelStepElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getParallel().toString(), ParallelStepElementConfig.class);
    } catch (Exception ex) {
      throw new CIStageExecutionException("Failed to deserialize ExecutionWrapperConfig parallel node", ex);
    }
  }

  public static CIAbstractStepNode getStepNode(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getStep().toString(), CIAbstractStepNode.class);
    } catch (Exception ex) {
      String errorMessage = "Failed to deserialize ExecutionWrapperConfig step node";
      Throwable throwable = ex.getCause();
      if (throwable != null && Strings.isNotBlank(throwable.getMessage())) {
        errorMessage = throwable.getMessage();
      }
      throw new CIStageExecutionException(errorMessage, ex);
    }
  }

  public static StepGroupElementConfig getStepGroupElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getStepGroup().toString(), StepGroupElementConfig.class);
    } catch (Exception ex) {
      throw new CIStageExecutionException("Failed to deserialize ExecutionWrapperConfig step node", ex);
    }
  }

  public static ProvenanceSource buildDockerProvenanceSource(DockerStepInfo dockerStepInfo) {
    return ProvenanceSource.builder()
        .type(ProvenanceSourceType.DOCKER)
        .spec(DockerSourceSpec.builder()
                  .connector(dockerStepInfo.getConnectorRef())
                  .repo(dockerStepInfo.getRepo())
                  .tags(dockerStepInfo.getTags())
                  .build())
        .build();
  }

  public static ProvenanceSource buildGcrProvenanceSource(GCRStepInfo gcrStepInfo) {
    return ProvenanceSource.builder()
        .type(ProvenanceSourceType.GCR)
        .spec(GcrSourceSpec.builder()
                  .connector(gcrStepInfo.getConnectorRef())
                  .host(gcrStepInfo.getHost())
                  .imageName(gcrStepInfo.getImageName())
                  .projectID(gcrStepInfo.getProjectID())
                  .tags(gcrStepInfo.getTags())
                  .build())
        .build();
  }
}
