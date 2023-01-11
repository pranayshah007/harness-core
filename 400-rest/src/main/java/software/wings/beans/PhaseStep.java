/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static software.wings.beans.Graph.Builder.aGraph;
import static software.wings.beans.GraphLink.Builder.aLink;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.AMI_AUTOSCALING_GROUP_SETUP;
import static software.wings.beans.PhaseStepType.CONTAINER_SETUP;
import static software.wings.beans.PhaseStepType.INFRASTRUCTURE_NODE;
import static software.wings.beans.PhaseStepType.PCF_SETUP;
import static software.wings.sm.StateType.FORK;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.data.structure.MapUtils;
import io.harness.data.structure.NullSafeImmutableMap;

import software.wings.api.DeploymentType;
import software.wings.beans.Graph.Builder;
import software.wings.beans.workflow.StepSkipStrategy;
import software.wings.common.WorkflowConstants;
import software.wings.sm.StateType;
import software.wings.sm.TransitionType;
import software.wings.yaml.BaseYamlWithType;
import software.wings.yaml.workflow.StepYaml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Created by rishi on 12/21/16.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._957_CG_BEANS)
public class PhaseStep {
  private String uuid = generateUuid();
  private String name;
  private PhaseStepType phaseStepType;
  @JsonIgnore private List<String> stepsIds = new ArrayList<>();
  @Transient private List<GraphNode> steps = new ArrayList<>();
  private boolean stepsInParallel;
  private List<FailureStrategy> failureStrategies = new ArrayList<>();
  private List<StepSkipStrategy> stepSkipStrategies = new ArrayList<>();

  private boolean rollback;
  private String phaseStepNameForRollback;
  private ExecutionStatus statusForRollback;
  private boolean artifactNeeded;

  private boolean valid = true;
  private String validationMessage;

  private Integer waitInterval;
  private static final Set<PhaseStepType> setupTypes =
      new HashSet<>(Arrays.asList(CONTAINER_SETUP, PCF_SETUP, AMI_AUTOSCALING_GROUP_SETUP));
  public PhaseStep() {}

  public PhaseStep(PhaseStepType phaseStepType) {
    this.uuid = generateUuid();
    this.phaseStepType = phaseStepType;
    this.name = phaseStepType.getDefaultName();
  }

  public PhaseStep(PhaseStepType phaseStepType, String name) {
    this.uuid = generateUuid();
    this.phaseStepType = phaseStepType;
    this.name = name;
  }

  public String getUuid() {
    return uuid;
  }

  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public PhaseStepType getPhaseStepType() {
    return phaseStepType;
  }

  public void setPhaseStepType(PhaseStepType phaseStepType) {
    this.phaseStepType = phaseStepType;
  }

  public List<GraphNode> getSteps() {
    return steps;
  }

  public void setSteps(List<GraphNode> steps) {
    this.steps = steps;
  }

  public boolean isStepsInParallel() {
    return stepsInParallel;
  }

  public void setStepsInParallel(boolean stepsInParallel) {
    this.stepsInParallel = stepsInParallel;
  }

  public List<FailureStrategy> getFailureStrategies() {
    return failureStrategies;
  }

  public void setFailureStrategies(List<FailureStrategy> failureStrategies) {
    this.failureStrategies = failureStrategies;
  }

  public List<StepSkipStrategy> getStepSkipStrategies() {
    return stepSkipStrategies;
  }

  public void setStepSkipStrategies(List<StepSkipStrategy> stepSkipStrategies) {
    this.stepSkipStrategies = stepSkipStrategies;
  }

  public List<String> getStepsIds() {
    return stepsIds;
  }

  public void setStepsIds(List<String> stepsIds) {
    this.stepsIds = stepsIds;
  }

  public boolean isRollback() {
    return rollback;
  }

  public void setRollback(boolean rollback) {
    this.rollback = rollback;
  }

  public String getPhaseStepNameForRollback() {
    return phaseStepNameForRollback;
  }

  public void setPhaseStepNameForRollback(String phaseStepNameForRollback) {
    this.phaseStepNameForRollback = phaseStepNameForRollback;
  }

  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean valid) {
    this.valid = valid;
  }

  public String getValidationMessage() {
    return validationMessage;
  }

  public void setValidationMessage(String validationMessage) {
    this.validationMessage = validationMessage;
  }

  public ExecutionStatus getStatusForRollback() {
    return statusForRollback;
  }

  public void setStatusForRollback(ExecutionStatus statusForRollback) {
    this.statusForRollback = statusForRollback;
  }

  public Integer getWaitInterval() {
    return waitInterval;
  }

  public void setWaitInterval(Integer waitInterval) {
    this.waitInterval = waitInterval;
  }

  public boolean isArtifactNeeded() {
    return artifactNeeded;
  }

  public void setArtifactNeeded(boolean artifactNeeded) {
    this.artifactNeeded = artifactNeeded;
  }

  public GraphNode generatePhaseStepNode() {
    // TODO: removing failure strategy as part of Node due to mongo driver limitation - we can try with later version
    // CodecConfigurationException: Can't find a codec for class software.wings.beans.FailureStrategy

    return GraphNode.builder()
        .id(uuid)
        .name(getName())
        .type(StateType.PHASE_STEP.name())
        .rollback(rollback)
        .properties(NullSafeImmutableMap.<String, Object>builder()
                        .put(WorkflowConstants.SUB_WORKFLOW_ID, uuid)
                        .putIfNotNull("phaseStepType", phaseStepType)
                        .putIfNotNull("stepsInParallel", stepsInParallel)
                        .putIfNotNull("artifactNeeded", artifactNeeded)
                        .putIfNotNull("waitInterval", waitInterval)
                        .putIfNotNull("phaseStepNameForRollback", phaseStepNameForRollback)
                        .putIfNotNull("statusForRollback", statusForRollback)
                        .build())
        .build();
  }

  private static boolean isExecuteWithPreviousSteps(GraphNode step) {
    if (step.getProperties() == null) {
      return false;
    }
    return Boolean.TRUE.equals(step.getProperties().get(WorkflowConstants.EXECUTE_WITH_PREVIOUS_STEPS));
  }

  public Graph generateSubworkflow(DeploymentType deploymentType) {
    Builder graphBuilder = aGraph().withGraphName(name);
    if (isEmpty(steps)) {
      return graphBuilder.build();
    }
    for (GraphNode step : getSteps()) {
      step.setProperties(MapUtils.putToImmutable("parentId", getUuid(), step.getProperties()));
    }

    GraphNode originNode = null;

    if (stepsInParallel && steps.size() > 1) {
      GraphNode forkNode = GraphNode.builder()
                               .id(generateUuid())
                               .type(FORK.name())
                               .name(name + "-FORK")
                               .properties(ImmutableMap.<String, Object>builder().put("parentId", getUuid()).build())
                               .build();
      graphBuilder.addNodes(forkNode);
      for (GraphNode step : steps) {
        step.setOrigin(false);
        graphBuilder.addNodes(step);
        graphBuilder.addLinks(aLink()
                                  .withId(generateUuid())
                                  .withFrom(forkNode.getId())
                                  .withTo(step.getId())
                                  .withType(TransitionType.FORK.name())
                                  .build());
      }
      if (originNode == null) {
        originNode = forkNode;
      }
    } else {
      GraphNode forkNode = null;
      GraphNode prevNode = null;
      for (int i = 0; i < steps.size(); ++i) {
        GraphNode step = steps.get(i);
        step.setOrigin(false);
        graphBuilder.addNodes(step);
        if (i > 0 && isExecuteWithPreviousSteps(step)) {
          graphBuilder.addLinks(
              aLink().withFrom(forkNode.getId()).withTo(step.getId()).withType(TransitionType.FORK.name()).build());
          continue;
        }
        if (i < steps.size() - 1 && isExecuteWithPreviousSteps(steps.get(i + 1))) {
          forkNode = GraphNode.builder().id(generateUuid()).type(FORK.name()).name("Fork-" + step.getName()).build();
          graphBuilder.addNodes(forkNode);
          graphBuilder.addLinks(
              aLink().withFrom(forkNode.getId()).withTo(step.getId()).withType(TransitionType.FORK.name()).build());
          if (prevNode == null) {
            originNode = forkNode;
          } else {
            graphBuilder.addLinks(aLink()
                                      .withFrom(prevNode.getId())
                                      .withTo(forkNode.getId())
                                      .withType(TransitionType.SUCCESS.name())
                                      .build());
          }
          prevNode = forkNode;
          continue;
        }
        if (prevNode == null) {
          originNode = step;
        } else {
          graphBuilder.addLinks(
              aLink().withFrom(prevNode.getId()).withTo(step.getId()).withType(TransitionType.SUCCESS.name()).build());
        }
        prevNode = step;
      }
    }

    if (originNode != null) {
      originNode.setOrigin(true);
    }
    return graphBuilder.build();
  }

  public boolean validate() {
    valid = true;
    validationMessage = null;
    if (steps != null) {
      List<String> invalidChildren =
          steps.stream().filter(step -> !step.validate()).map(GraphNode::getName).collect(toList());
      if (isNotEmpty(invalidChildren)) {
        valid = false;
        validationMessage = format(WorkflowConstants.PHASE_STEP_VALIDATION_MESSAGE, invalidChildren.toString());
      }
    }
    return valid;
  }

  public PhaseStep cloneIntenal() {
    PhaseStepType stepType = getPhaseStepType();
    // Do not clone if stepType is containerSetup or pcfSetup
    // as only 1st phase has setup step.
    if (stepType != null && setupTypes.contains(stepType)) {
      return null;
    }
    PhaseStep clonedPhaseStep = aPhaseStep(stepType, getName())
                                    .withPhaseStepNameForRollback(getPhaseStepNameForRollback())
                                    .withPhaseStepType(stepType)
                                    .withRollback(isRollback())
                                    .withFailureStrategies(getFailureStrategies())
                                    .withStatusForRollback(getStatusForRollback())
                                    .withStepsInParallel(isStepsInParallel())
                                    .withArtifactNeeded(isArtifactNeeded())
                                    .withWaitInterval(getWaitInterval())
                                    .build();
    List<GraphNode> steps = getSteps();
    List<StepSkipStrategy> stepSkipStrategyList = getStepSkipStrategies();
    List<StepSkipStrategy> clonedStepSkipStrategies = new ArrayList<>();
    if (stepSkipStrategyList != null) {
      clonedStepSkipStrategies = stepSkipStrategyList.stream()
                                     .map(StepSkipStrategy::cloneInternal)
                                     .filter(Objects::nonNull)
                                     .collect(Collectors.toList());
    }
    List<String> clonedStepIds = new ArrayList<>();
    List<GraphNode> clonedSteps = new ArrayList<>();
    if (steps != null) {
      for (GraphNode step : steps) {
        GraphNode clonedStep = step.cloneInternal();
        if (INFRASTRUCTURE_NODE == clonedPhaseStep.getPhaseStepType()
            && (clonedStep.getType().equals(StateType.DC_NODE_SELECT.name())
                || clonedStep.getType().equals(StateType.AWS_NODE_SELECT.name()))) {
          Map<String, Object> properties = new HashMap<>(clonedStep.getProperties());
          if (properties.containsKey("specificHosts") && (Boolean) properties.get("specificHosts")) {
            properties.remove("hostNames");
            clonedStep.setProperties(properties);
          }
        }
        clonedSteps.add(clonedStep);
        clonedStepIds.add(clonedStep.getId());
        for (StepSkipStrategy stepSkipStrategy : clonedStepSkipStrategies) {
          List<String> stepIds = stepSkipStrategy.getStepIds();
          if (stepIds != null && stepIds.contains(step.getId())) {
            for (int i = 0; i < stepIds.size(); i++) {
              if (stepIds.get(i).equals(step.getId())) {
                stepIds.set(i, clonedStep.getId());
                break;
              }
            }
          }
        }
      }
    }
    clonedPhaseStep.setStepsIds(clonedStepIds);
    clonedPhaseStep.setSteps(clonedSteps);
    clonedPhaseStep.setStepSkipStrategies(clonedStepSkipStrategies);
    return clonedPhaseStep;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PhaseStep phaseStep = (PhaseStep) o;

    if (stepsInParallel != phaseStep.stepsInParallel) {
      return false;
    }
    if (rollback != phaseStep.rollback) {
      return false;
    }
    if (uuid != null ? !uuid.equals(phaseStep.uuid) : phaseStep.uuid != null) {
      return false;
    }
    if (name != null ? !name.equals(phaseStep.name) : phaseStep.name != null) {
      return false;
    }
    if (phaseStepType != phaseStep.phaseStepType) {
      return false;
    }
    if (stepsIds != null ? !stepsIds.equals(phaseStep.stepsIds) : phaseStep.stepsIds != null) {
      return false;
    }
    if (steps != null ? !steps.equals(phaseStep.steps) : phaseStep.steps != null) {
      return false;
    }
    if (failureStrategies != null ? !failureStrategies.equals(phaseStep.failureStrategies)
                                  : phaseStep.failureStrategies != null) {
      return false;
    }
    return phaseStepNameForRollback != null ? phaseStepNameForRollback.equals(phaseStep.phaseStepNameForRollback)
                                            : phaseStep.phaseStepNameForRollback == null;
  }

  @Override
  public int hashCode() {
    int result = uuid != null ? uuid.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (phaseStepType != null ? phaseStepType.hashCode() : 0);
    result = 31 * result + (stepsIds != null ? stepsIds.hashCode() : 0);
    result = 31 * result + (steps != null ? steps.hashCode() : 0);
    result = 31 * result + (stepsInParallel ? 1 : 0);
    result = 31 * result + (failureStrategies != null ? failureStrategies.hashCode() : 0);
    result = 31 * result + (rollback ? 1 : 0);
    result = 31 * result + (phaseStepNameForRollback != null ? phaseStepNameForRollback.hashCode() : 0);
    return result;
  }

  public static final class PhaseStepBuilder {
    private String uuid = generateUuid();
    private String name;
    private PhaseStepType phaseStepType;
    private List<String> stepsIds = new ArrayList<>();
    private List<GraphNode> steps = new ArrayList<>();
    private boolean stepsInParallel;
    private List<FailureStrategy> failureStrategies = new ArrayList<>();
    private List<StepSkipStrategy> stepSkipStrategies = new ArrayList<>();
    private boolean rollback;
    private String phaseStepNameForRollback;
    private ExecutionStatus statusForRollback;
    private boolean artifactNeeded;
    private boolean valid = true;
    private String validationMessage;
    private Integer waitInterval;

    private PhaseStepBuilder() {}

    public static PhaseStepBuilder aPhaseStep(PhaseStepType phaseStepType) {
      return aPhaseStep(phaseStepType, phaseStepType.getDefaultName());
    }
    public static PhaseStepBuilder aPhaseStep(PhaseStepType phaseStepType, String name) {
      PhaseStepBuilder phaseStepBuilder = new PhaseStepBuilder();
      phaseStepBuilder.phaseStepType = phaseStepType;
      phaseStepBuilder.name = name;
      phaseStepBuilder.uuid = generateUuid();
      return phaseStepBuilder;
    }

    public static PhaseStepBuilder aPhaseStep(PhaseStepType phaseStepType, String name, String uuid) {
      PhaseStepBuilder phaseStepBuilder = new PhaseStepBuilder();
      phaseStepBuilder.phaseStepType = phaseStepType;
      phaseStepBuilder.name = name;
      phaseStepBuilder.uuid = uuid;
      return phaseStepBuilder;
    }

    public PhaseStepBuilder withPhaseStepType(PhaseStepType phaseStepType) {
      this.phaseStepType = phaseStepType;
      return this;
    }

    public PhaseStepBuilder addStep(GraphNode step) {
      this.steps.add(step);
      return this;
    }

    public PhaseStepBuilder addAllSteps(List<GraphNode> steps) {
      this.steps.addAll(steps);
      return this;
    }

    public PhaseStepBuilder withStepsInParallel(boolean stepsInParallel) {
      this.stepsInParallel = stepsInParallel;
      return this;
    }

    public PhaseStepBuilder withFailureStrategies(List<FailureStrategy> failureStrategies) {
      this.failureStrategies = failureStrategies;
      return this;
    }

    public PhaseStepBuilder withStepSkipStrategies(List<StepSkipStrategy> stepSkipStrategies) {
      this.stepSkipStrategies = stepSkipStrategies;
      return this;
    }

    public PhaseStepBuilder withRollback(boolean rollback) {
      this.rollback = rollback;
      return this;
    }

    public PhaseStepBuilder withPhaseStepNameForRollback(String phaseStepNameForRollback) {
      this.phaseStepNameForRollback = phaseStepNameForRollback;
      return this;
    }

    public PhaseStepBuilder withStatusForRollback(ExecutionStatus statusForRollback) {
      this.statusForRollback = statusForRollback;
      return this;
    }

    public PhaseStepBuilder withValid(boolean valid) {
      this.valid = valid;
      return this;
    }

    public PhaseStepBuilder withValidationMessage(String validationMessage) {
      this.validationMessage = validationMessage;
      return this;
    }

    public PhaseStepBuilder withArtifactNeeded(boolean artifactNeeded) {
      this.artifactNeeded = artifactNeeded;
      return this;
    }

    public PhaseStepBuilder withWaitInterval(Integer waitInterval) {
      this.waitInterval = waitInterval;
      return this;
    }

    public PhaseStep build() {
      PhaseStep phaseStep = new PhaseStep();
      phaseStep.setUuid(uuid);
      phaseStep.setName(name);
      phaseStep.setPhaseStepType(phaseStepType);
      phaseStep.setStepsIds(stepsIds);
      phaseStep.setSteps(steps);
      phaseStep.setStepsInParallel(stepsInParallel);
      phaseStep.setFailureStrategies(failureStrategies);
      phaseStep.setStepSkipStrategies(stepSkipStrategies);
      phaseStep.setRollback(rollback);
      phaseStep.setPhaseStepNameForRollback(phaseStepNameForRollback);
      phaseStep.setStatusForRollback(statusForRollback);
      phaseStep.setValid(valid);
      phaseStep.setValidationMessage(validationMessage);
      phaseStep.setArtifactNeeded(artifactNeeded);
      phaseStep.setWaitInterval(waitInterval);
      return phaseStep;
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends BaseYamlWithType {
    private String name;
    private String statusForRollback;
    private boolean stepsInParallel;
    private List<StepYaml> steps = new ArrayList<>();
    private List<FailureStrategy.Yaml> failureStrategies = new ArrayList<>();
    private List<StepSkipStrategy.Yaml> stepSkipStrategies = new ArrayList<>();
    private String phaseStepNameForRollback;
    private Integer waitInterval;

    @lombok.Builder
    public Yaml(String type, String name, String statusForRollback, boolean stepsInParallel, List<StepYaml> steps,
        List<FailureStrategy.Yaml> failureStrategies, List<StepSkipStrategy.Yaml> stepSkipStrategies,
        String phaseStepNameForRollback, Integer waitInterval) {
      super(type);
      this.name = name;
      this.statusForRollback = statusForRollback;
      this.stepsInParallel = stepsInParallel;
      this.steps = steps;
      this.failureStrategies = failureStrategies;
      this.stepSkipStrategies = stepSkipStrategies;
      this.phaseStepNameForRollback = phaseStepNameForRollback;
      this.waitInterval = waitInterval;
    }
  }
}
