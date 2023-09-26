/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution.provenance;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.ssca.execution.provenance.ProvenanceStepUtils.PROVENANCE_STEP;
import static io.harness.ssca.execution.provenance.ProvenanceStepUtils.PROVENANCE_STEP_GROUP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.beans.steps.stepinfo.GCRStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.slsa.beans.SlsaConfig;
import io.harness.ssca.beans.attestation.v1.AttestationV1;
import io.harness.ssca.beans.provenance.ProvenanceSource;
import io.harness.ssca.beans.stepinfo.ProvenanceStepInfo;
import io.harness.ssca.beans.stepnode.ProvenanceStepNode;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.SSCA)
public class ProvenanceStepGenerator {
  private static final String UNDERSCORE_SEP = "_";
  private static final List<CIStepInfoType> allowedTypesForProvenance = List.of(CIStepInfoType.DOCKER);

  public void encapsulateBuildAndPushStepsWithStepGroup(
      List<ExecutionWrapperConfig> executionWrapperConfigs, SlsaConfig slsaConfig, Infrastructure.Type infraType) {
    boolean slsaEnabled = slsaConfig != null
        && RunTimeInputHandler.resolveBooleanParameter(slsaConfig.getEnabled(), false)
        && infraType == Infrastructure.Type.KUBERNETES_DIRECT;
    if (!slsaEnabled) {
      return;
    }

    AttestationV1 attestationV1 = slsaConfig.getAttestation();

    for (ExecutionWrapperConfig executionWrapper : executionWrapperConfigs) {
      if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
        handleSingleStep(executionWrapper, attestationV1);
      } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
        handleParallelSteps(executionWrapper, attestationV1);
      } else if (executionWrapper.getStepGroup() != null && !executionWrapper.getStepGroup().isNull()) {
        handleStepGroup(executionWrapper, attestationV1);
      } else {
        throw new InvalidRequestException("Only Parallel, StepElement and StepGroup are supported");
      }
    }
  }

  private void handleSingleStep(ExecutionWrapperConfig executionWrapper, AttestationV1 attestation) {
    CIAbstractStepNode stepNode = ProvenanceStepUtils.getStepNode(executionWrapper);
    CIStepInfo stepInfo = (CIStepInfo) stepNode.getStepSpecType();
    if (!allowedTypesForProvenance.contains(stepInfo.getNonYamlInfo().getStepInfoType())) {
      return;
    }
    executionWrapper.setStepGroup(createStepGroupExecutionWrapper(stepNode, attestation));
    executionWrapper.setStep(null);
  }

  private void handleParallelSteps(ExecutionWrapperConfig executionWrapper, AttestationV1 attestation) {
    ParallelStepElementConfig parallelStepElementConfig =
        ProvenanceStepUtils.getParallelStepElementConfig(executionWrapper);
    if (EmptyPredicate.isEmpty(parallelStepElementConfig.getSections())) {
      return;
    }
    for (ExecutionWrapperConfig executionWrapperInParallel : parallelStepElementConfig.getSections()) {
      if (executionWrapperInParallel.getStep() != null && !executionWrapperInParallel.getStep().isNull()) {
        handleSingleStep(executionWrapperInParallel, attestation);
      } else if (executionWrapperInParallel.getStepGroup() != null
          && !executionWrapperInParallel.getStepGroup().isNull()) {
        handleStepGroup(executionWrapperInParallel, attestation);
      }
    }
  }

  private void handleStepGroup(ExecutionWrapperConfig executionWrapper, AttestationV1 attestation) {
    StepGroupElementConfig stepGroupElementConfig = ProvenanceStepUtils.getStepGroupElementConfig(executionWrapper);
    if (isEmpty(stepGroupElementConfig.getSteps())) {
      return;
    }
    for (ExecutionWrapperConfig step : stepGroupElementConfig.getSteps()) {
      if (step.getStep() != null && !step.getStep().isNull()) {
        handleSingleStep(step, attestation);
      } else if (step.getParallel() != null && !step.getParallel().isNull()) {
        handleParallelSteps(step, attestation);
      } else if (step.getStepGroup() != null && !step.getStepGroup().isNull()) {
        handleStepGroup(step, attestation);
      }
    }
  }

  private JsonNode createStepGroupExecutionWrapper(CIAbstractStepNode stepNode, AttestationV1 attestation) {
    StepGroupElementConfig stepGroupElementConfig =
        StepGroupElementConfig.builder()
            .identifier(PROVENANCE_STEP_GROUP + UNDERSCORE_SEP + stepNode.getIdentifier())
            .name(PROVENANCE_STEP_GROUP + UNDERSCORE_SEP + stepNode.getName())
            .uuid(generateUuid())
            .when(stepNode.getWhen())
            .skipCondition(stepNode.getSkipCondition())
            .failureStrategies(stepNode.getFailureStrategies())
            .build();

    List<ExecutionWrapperConfig> executionWrapperConfigs = new ArrayList<>();
    ExecutionWrapperConfig provenanceStepConfig = createProvenanceExecutionWrapper(stepNode, attestation);
    executionWrapperConfigs.add(modifyCurrentStep(stepNode));
    executionWrapperConfigs.add(provenanceStepConfig);
    stepGroupElementConfig.setSteps(executionWrapperConfigs);

    try {
      String jsonString = JsonPipelineUtils.writeJsonString(stepGroupElementConfig);
      return JsonPipelineUtils.getMapper().readTree(jsonString);
    } catch (IOException e) {
      throw new CIStageExecutionException("Failed to create Json Node of provenance step group", e);
    }
  }

  private ExecutionWrapperConfig modifyCurrentStep(CIAbstractStepNode stepNode) {
    stepNode.setUuid(generateUuid());
    stepNode.setFailureStrategies(null);
    stepNode.setSkipCondition(null);
    stepNode.setFailureStrategies(null);
    try {
      String jsonString = JsonPipelineUtils.writeJsonString(stepNode);
      JsonNode jsonNode = JsonPipelineUtils.getMapper().readTree(jsonString);
      return ExecutionWrapperConfig.builder().uuid(generateUuid()).step(jsonNode).build();
    } catch (IOException e) {
      throw new CIStageExecutionException("Failed to create execution config of step", e);
    }
  }

  private ExecutionWrapperConfig createProvenanceExecutionWrapper(
      CIAbstractStepNode stepNode, AttestationV1 attestationV1) {
    CIStepInfo ciStepInfo = (CIStepInfo) stepNode.getStepSpecType();
    ProvenanceSource source = null;
    if (ciStepInfo.getNonYamlInfo().getStepInfoType() == CIStepInfoType.DOCKER) {
      source = ProvenanceStepUtils.buildDockerProvenanceSource((DockerStepInfo) ciStepInfo);
    }
    if (ciStepInfo.getNonYamlInfo().getStepInfoType() == CIStepInfoType.GCR) {
      source = ProvenanceStepUtils.buildGcrProvenanceSource((GCRStepInfo) ciStepInfo);
    }
    ProvenanceStepInfo stepInfo =
        ProvenanceStepInfo.builder().uuid(generateUuid()).attestation(attestationV1).source(source).build();

    ProvenanceStepNode provenanceStepNode = new ProvenanceStepNode();
    provenanceStepNode.setStepInfo(stepInfo);
    provenanceStepNode.setUuid(generateUuid());
    provenanceStepNode.setTimeout(stepNode.getTimeout());
    provenanceStepNode.setIdentifier(stepNode.getIdentifier() + UNDERSCORE_SEP + PROVENANCE_STEP);
    provenanceStepNode.setName(stepNode.getName() + UNDERSCORE_SEP + PROVENANCE_STEP);
    try {
      String jsonString = JsonPipelineUtils.writeJsonString(provenanceStepNode);
      JsonNode jsonNode = JsonPipelineUtils.getMapper().readTree(jsonString);
      return ExecutionWrapperConfig.builder().uuid(generateUuid()).step(jsonNode).build();
    } catch (IOException e) {
      throw new CIStageExecutionException("Failed to create execution config of provenance step", e);
    }
  }
}
