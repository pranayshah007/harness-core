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
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.sm.StateType.PHASE;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.persistence.UuidAccess;

import software.wings.api.DeploymentType;
import software.wings.beans.Graph.Builder;
import software.wings.service.impl.workflow.WorkflowServiceTemplateHelper;
import software.wings.sm.TransitionType;
import software.wings.yaml.BaseYamlWithType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.mongodb.morphia.annotations.Transient;

@TargetModule(HarnessModule._957_CG_BEANS)
@OwnedBy(CDC)
public class WorkflowPhase implements UuidAccess {
  private String uuid = generateUuid();
  private String name;
  private String serviceId;
  private boolean daemonSet;
  private boolean statefulSet;

  private String infraMappingId;
  private String infraMappingName;

  private String infraDefinitionId;
  private String infraDefinitionName;

  private DeploymentType deploymentType;
  @Transient @Getter @Setter private boolean srvTemplatised;
  @Transient @Getter @Setter private boolean infraTemplatised;
  private String computeProviderId;
  private boolean provisionNodes;

  private boolean rollback;
  private String phaseNameForRollback;

  private boolean valid = true;
  private String validationMessage;

  private List<TemplateExpression> templateExpressions;

  @Valid private List<NameValuePair> variableOverrides = new ArrayList<>();

  private List<PhaseStep> phaseSteps = new ArrayList<>();

  private static final String PHASE_VALIDATION_MESSAGE = "Some steps %s are found to be invalid/incomplete.";

  @Override
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

  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  public boolean isDaemonSet() {
    return daemonSet;
  }

  public void setDaemonSet(boolean daemonSet) {
    this.daemonSet = daemonSet;
  }

  public boolean isStatefulSet() {
    return statefulSet;
  }

  public void setStatefulSet(boolean statefulSet) {
    this.statefulSet = statefulSet;
  }

  public String getComputeProviderId() {
    return computeProviderId;
  }

  public void setComputeProviderId(String computeProviderId) {
    this.computeProviderId = computeProviderId;
  }

  public DeploymentType getDeploymentType() {
    return deploymentType;
  }

  public void setDeploymentType(DeploymentType deploymentType) {
    this.deploymentType = deploymentType;
  }

  public String getInfraMappingId() {
    return infraMappingId;
  }

  public void setInfraMappingId(String infraMappingId) {
    this.infraMappingId = infraMappingId;
  }

  public String getInfraDefinitionId() {
    return infraDefinitionId;
  }

  public void setInfraDefinitionId(String infraDefinitionId) {
    this.infraDefinitionId = infraDefinitionId;
  }

  public String getInfraDefinitionName() {
    return infraDefinitionName;
  }

  public void setInfraDefinitionName(String infraDefinitionName) {
    this.infraDefinitionName = infraDefinitionName;
  }

  public String getInfraMappingName() {
    return infraMappingName;
  }

  public void setInfraMappingName(String infraMappingName) {
    this.infraMappingName = infraMappingName;
  }

  public boolean isProvisionNodes() {
    return provisionNodes;
  }

  public void setProvisionNodes(boolean provisionNodes) {
    this.provisionNodes = provisionNodes;
  }

  public List<PhaseStep> getPhaseSteps() {
    return phaseSteps;
  }

  public void setPhaseSteps(List<PhaseStep> phaseSteps) {
    this.phaseSteps = phaseSteps;
  }

  public boolean isRollback() {
    return rollback;
  }

  public void setRollback(boolean rollback) {
    this.rollback = rollback;
  }

  public String getPhaseNameForRollback() {
    return phaseNameForRollback;
  }

  public void setPhaseNameForRollback(String phaseNameForRollback) {
    this.phaseNameForRollback = phaseNameForRollback;
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

  public List<NameValuePair> getVariableOverrides() {
    return variableOverrides;
  }

  public void setVariableOverrides(List<NameValuePair> variableOverrides) {
    this.variableOverrides = variableOverrides;
  }

  /**
   * Get template expressions
   * @return
   */
  public List<TemplateExpression> getTemplateExpressions() {
    return templateExpressions;
  }

  /**
   * Set template expressions
   * @param templateExpressions
   */
  public void setTemplateExpressions(List<TemplateExpression> templateExpressions) {
    this.templateExpressions = templateExpressions;
  }

  public GraphNode generatePhaseNode() {
    return GraphNode.builder()
        .id(uuid)
        .name(name)
        .type(PHASE.name())
        .rollback(rollback)
        .properties(NullSafeImmutableMap.<String, Object>builder()
                        .putIfNotNull("serviceId", serviceId)
                        .putIfNotNull("deploymentType", deploymentType)
                        .putIfNotNull("computeProviderId", computeProviderId)
                        .putIfNotNull("infraMappingName", infraMappingName)
                        .putIfNotNull("infraDefinitionName", infraDefinitionName)
                        .putIfNotNull("infraMappingId", infraMappingId)
                        .putIfNotNull("infraDefinitionId", infraDefinitionId)
                        .putIfNotNull("subWorkflowId", uuid)
                        .putIfNotNull("phaseNameForRollback", phaseNameForRollback)
                        .build())
        .templateExpressions(templateExpressions)
        .variableOverrides(variableOverrides)
        .build();
  }

  public Map<String, Object> params() {
    Map<String, Object> params = new HashMap<>();
    params.put("serviceId", serviceId);
    params.put("computeProviderId", computeProviderId);
    params.put("infraMappingName", infraMappingName);
    params.put("infraDefinitionName", infraDefinitionName);
    params.put("infraMappingId", infraMappingId);
    params.put("infraDefinitionId", infraDefinitionId);
    params.put("deploymentType", deploymentType);
    return params;
  }

  public Map<String, Graph> generateSubworkflows() {
    Map<String, Graph> graphs = new HashMap<>();
    Builder graphBuilder = aGraph().withGraphName(name);

    String id1 = null;
    String id2;
    GraphNode node;
    for (PhaseStep phaseStep : phaseSteps) {
      id2 = phaseStep.getUuid();
      node = phaseStep.generatePhaseStepNode();
      graphBuilder.addNodes(node);
      if (id1 == null) {
        node.setOrigin(true);
      } else {
        graphBuilder.addLinks(
            aLink().withId(getUuid()).withFrom(id1).withTo(id2).withType(TransitionType.SUCCESS.name()).build());
      }
      id1 = id2;
      Graph stepsGraph = phaseStep.generateSubworkflow(deploymentType);
      graphs.put(phaseStep.getUuid(), stepsGraph);
    }

    graphs.put(uuid, graphBuilder.build());
    return graphs;
  }

  public boolean validate() {
    valid = true;
    validationMessage = null;
    if (phaseSteps != null) {
      List<String> invalidChildren =
          phaseSteps.stream().filter(phaseStep -> !phaseStep.validate()).map(PhaseStep::getName).collect(toList());
      if (isNotEmpty(invalidChildren)) {
        valid = false;
        validationMessage = format(PHASE_VALIDATION_MESSAGE, invalidChildren.toString());
      }
    }

    return valid;
  }

  public WorkflowPhase cloneInternal() {
    WorkflowPhase clonedWorkflowPhase = aWorkflowPhase()
                                            .uuid(generateUuid())
                                            .serviceId(getServiceId())
                                            .infraMappingId(getInfraMappingId())
                                            .infraDefinitionId(getInfraDefinitionId())
                                            .infraMappingName(getInfraMappingName())
                                            .infraDefinitionName(getInfraDefinitionName())
                                            .computeProviderId(getComputeProviderId())
                                            .deploymentType(getDeploymentType())
                                            .rollback(isRollback())
                                            .phaseNameForRollback(getPhaseNameForRollback())
                                            .valid(isValid())
                                            .validationMessage(getValidationMessage())
                                            .templateExpressions(getTemplateExpressions())
                                            .daemonSet(isDaemonSet())
                                            .statefulSet(isStatefulSet())
                                            .variableOverrides(getVariableOverrides())
                                            .build();
    List<PhaseStep> phaseSteps = getPhaseSteps();
    List<PhaseStep> clonedPhaseSteps = new ArrayList<>();
    if (phaseSteps != null) {
      clonedPhaseSteps =
          phaseSteps.stream().map(PhaseStep::cloneIntenal).filter(Objects::nonNull).collect(Collectors.toList());
    }
    clonedWorkflowPhase.setPhaseSteps(clonedPhaseSteps);
    return clonedWorkflowPhase;
  }

  @JsonIgnore
  public boolean checkServiceTemplatized() {
    return checkFieldTemplatized("serviceId");
  }

  @JsonIgnore
  public boolean checkInfraTemplatized() {
    return checkFieldTemplatized("infraMappingId");
  }

  @JsonIgnore
  public boolean checkInfraDefinitionTemplatized() {
    return checkFieldTemplatized("infraDefinitionId");
  }

  public String fetchServiceTemplatizedName() {
    TemplateExpression serviceTemplateExpression = fetchServiceTemplateExpression();

    return serviceTemplateExpression == null
        ? null
        : WorkflowServiceTemplateHelper.getName(serviceTemplateExpression.getExpression());
  }

  public String fetchInfraMappingTemplatizedName() {
    TemplateExpression infraTemplateExpression = fetchInfraMappingTemplateExpression();
    return infraTemplateExpression == null
        ? null
        : WorkflowServiceTemplateHelper.getName(infraTemplateExpression.getExpression());
  }

  public String fetchInfraDefinitionTemplatizedName() {
    TemplateExpression infraTemplateExpression = fetchInfraDefinitonTemplateExpression();
    return infraTemplateExpression == null
        ? null
        : WorkflowServiceTemplateHelper.getName(infraTemplateExpression.getExpression());
  }

  private boolean checkFieldTemplatized(String fieldName) {
    return templateExpressions != null
        && templateExpressions.stream().anyMatch(
            templateExpression -> templateExpression.getFieldName().equals(fieldName));
  }

  public TemplateExpression fetchServiceTemplateExpression() {
    if (isEmpty(templateExpressions)) {
      return null;
    }
    return templateExpressions.stream()
        .filter(templateExpression -> templateExpression.getFieldName().equals("serviceId"))
        .findFirst()
        .orElse(null);
  }

  private TemplateExpression fetchInfraMappingTemplateExpression() {
    if (isEmpty(templateExpressions)) {
      return null;
    }
    return templateExpressions.stream()
        .filter(templateExpression -> templateExpression.getFieldName().equals("infraMappingId"))
        .findFirst()
        .orElse(null);
  }

  public TemplateExpression fetchInfraDefinitonTemplateExpression() {
    if (isEmpty(templateExpressions)) {
      return null;
    }
    return templateExpressions.stream()
        .filter(templateExpression -> templateExpression.getFieldName().equals("infraDefinitionId"))
        .findFirst()
        .orElse(null);
  }

  public static final class WorkflowPhaseBuilder {
    private String uuid = generateUuid();
    private String name;
    private String serviceId;
    private String infraMappingId;
    private String infraDefinitionId;
    private String infraMappingName;
    private String infraDefinitionName;
    private DeploymentType deploymentType;
    private String computeProviderId;
    private boolean rollback;
    private String phaseNameForRollback;
    private boolean valid = true;
    private String validationMessage;
    private boolean daemonSet;
    private boolean statefulSet;
    private List<PhaseStep> phaseSteps = new ArrayList<>();
    private List<TemplateExpression> templateExpressions;
    private List<NameValuePair> variableOverrides = new ArrayList<>();

    private WorkflowPhaseBuilder() {}

    public static WorkflowPhaseBuilder aWorkflowPhase() {
      return new WorkflowPhaseBuilder();
    }

    public WorkflowPhaseBuilder uuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public WorkflowPhaseBuilder name(String name) {
      this.name = name;
      return this;
    }

    public WorkflowPhaseBuilder serviceId(String serviceId) {
      this.serviceId = serviceId;
      return this;
    }

    public WorkflowPhaseBuilder infraMappingId(String infraMappingId) {
      this.infraMappingId = infraMappingId;
      return this;
    }

    public WorkflowPhaseBuilder infraDefinitionId(String infraDefinitionId) {
      this.infraDefinitionId = infraDefinitionId;
      return this;
    }

    public WorkflowPhaseBuilder infraMappingName(String infraMappingName) {
      this.infraMappingName = infraMappingName;
      return this;
    }

    public WorkflowPhaseBuilder infraDefinitionName(String infraDefinitionName) {
      this.infraDefinitionName = infraDefinitionName;
      return this;
    }

    public WorkflowPhaseBuilder deploymentType(DeploymentType deploymentType) {
      this.deploymentType = deploymentType;
      return this;
    }

    public WorkflowPhaseBuilder computeProviderId(String computeProviderId) {
      this.computeProviderId = computeProviderId;
      return this;
    }

    public WorkflowPhaseBuilder rollback(boolean rollback) {
      this.rollback = rollback;
      return this;
    }

    public WorkflowPhaseBuilder phaseNameForRollback(String phaseNameForRollback) {
      this.phaseNameForRollback = phaseNameForRollback;
      return this;
    }

    public WorkflowPhaseBuilder valid(boolean valid) {
      this.valid = valid;
      return this;
    }

    public WorkflowPhaseBuilder validationMessage(String validationMessage) {
      this.validationMessage = validationMessage;
      return this;
    }

    public WorkflowPhaseBuilder phaseSteps(List<PhaseStep> phaseSteps) {
      this.phaseSteps = phaseSteps;
      return this;
    }

    public WorkflowPhaseBuilder templateExpressions(List<TemplateExpression> templateExpressions) {
      this.templateExpressions = templateExpressions;
      return this;
    }

    public WorkflowPhaseBuilder daemonSet(boolean daemonSet) {
      this.daemonSet = daemonSet;
      return this;
    }

    public WorkflowPhaseBuilder statefulSet(boolean statefulSet) {
      this.statefulSet = statefulSet;
      return this;
    }

    public WorkflowPhaseBuilder variableOverrides(List<NameValuePair> variableOverrides) {
      this.variableOverrides = variableOverrides;
      return this;
    }

    public WorkflowPhase build() {
      WorkflowPhase workflowPhase = new WorkflowPhase();
      workflowPhase.setUuid(uuid);
      workflowPhase.setName(name);
      workflowPhase.setServiceId(serviceId);
      workflowPhase.setInfraMappingId(infraMappingId);
      workflowPhase.setInfraDefinitionId(infraDefinitionId);
      workflowPhase.setInfraMappingName(infraMappingName);
      workflowPhase.setInfraDefinitionName(infraDefinitionName);
      workflowPhase.setDeploymentType(deploymentType);
      workflowPhase.setComputeProviderId(computeProviderId);
      workflowPhase.setRollback(rollback);
      workflowPhase.setPhaseNameForRollback(phaseNameForRollback);
      workflowPhase.setValid(valid);
      workflowPhase.setValidationMessage(validationMessage);
      workflowPhase.setPhaseSteps(phaseSteps);
      workflowPhase.setTemplateExpressions(templateExpressions);
      workflowPhase.setDaemonSet(daemonSet);
      workflowPhase.setStatefulSet(statefulSet);
      workflowPhase.setVariableOverrides(variableOverrides);
      return workflowPhase;
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends BaseYamlWithType {
    private String name;
    private String infraMappingName;
    private String infraDefinitionName;
    private String serviceName;
    private String computeProviderName;
    private boolean provisionNodes;
    private boolean daemonSet;
    private boolean statefulSet;
    private String phaseNameForRollback;
    private List<TemplateExpression.Yaml> templateExpressions;
    private List<PhaseStep.Yaml> phaseSteps = new ArrayList<>();
    private List<NameValuePair> serviceVariableOverrides = new ArrayList<>();
    //  private DeploymentType deploymentType;

    @lombok.Builder
    public Yaml(String type, String name, String infraMappingName, String infraDefinitionName, String serviceName,
        String computeProviderName, boolean provisionNodes, String phaseNameForRollback,
        List<TemplateExpression.Yaml> templateExpressions, List<PhaseStep.Yaml> phaseSteps, boolean daemonSet,
        boolean statefulSet, List<NameValuePair> serviceVariableOverrides) {
      super(type);
      this.name = name;
      this.infraMappingName = infraMappingName;
      this.infraDefinitionName = infraDefinitionName;
      this.serviceName = serviceName;
      this.computeProviderName = computeProviderName;
      this.provisionNodes = provisionNodes;
      this.phaseNameForRollback = phaseNameForRollback;
      this.templateExpressions = templateExpressions;
      this.phaseSteps = phaseSteps;
      this.daemonSet = daemonSet;
      this.statefulSet = statefulSet;
      this.serviceVariableOverrides = serviceVariableOverrides;
    }
  }
}
