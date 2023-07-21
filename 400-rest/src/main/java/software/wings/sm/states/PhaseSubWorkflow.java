/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.api.PhaseExecutionData.PhaseExecutionDataBuilder.aPhaseExecutionData;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.context.ContextElementType;
import io.harness.exception.InvalidRequestException;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.MapperUtils;
import io.harness.tasks.ResponseData;

import software.wings.api.ContainerServiceElement;
import software.wings.api.DeploymentType;
import software.wings.api.PhaseElement;
import software.wings.api.PhaseElement.PhaseElementBuilder;
import software.wings.api.PhaseExecutionData;
import software.wings.api.PhaseExecutionData.PhaseExecutionDataBuilder;
import software.wings.api.ServiceElement;
import software.wings.beans.Application;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.PhaseSubWorkflowHelperService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.ContextElement;
import software.wings.sm.ElementNotifyResponseData;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.PhaseExecutionSummary;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rishi on 1/12/17.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Slf4j
public class PhaseSubWorkflow extends SubWorkflowState {
  public static final String SERVICE_ID = "serviceId";
  public static final String INFRA_MAPPING_ID = "infraMappingId";
  public static final String INFRA_DEFINITION_ID = "infraDefinitionId";

  public PhaseSubWorkflow(String name) {
    super(name, StateType.PHASE.name());
  }

  private String uuid;
  private String serviceId;
  private String infraMappingId;
  private String infraDefinitionId;

  // Only for rollback phase steps
  @SchemaIgnore private String phaseNameForRollback;

  @Inject @Transient private WorkflowExecutionService workflowExecutionService;
  @Inject @Transient private ServiceResourceService serviceResourceService;
  @Inject @Transient private InfrastructureMappingService infrastructureMappingService;
  @Inject @Transient private ArtifactService artifactService;
  @Inject @Transient private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject @Transient private SweepingOutputService sweepingOutputService;
  @Inject @Transient private PhaseSubWorkflowHelperService phaseSubWorkflowHelperService;
  @Inject @Transient private SettingsService settingsService;
  @Inject @Transient private KryoSerializer kryoSerializer;
  @Inject @Transient private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    Application app = workflowStandardParamsExtensionService.getApp(workflowStandardParams);
    notNullCheck("App Cannot be null", app, USER);

    TemplateExpression serviceTemplateExpression = null, infraDefinitionTemplateExpression = null,
                       infraMappingTemplateExpression = null;
    List<TemplateExpression> templateExpressions = this.getTemplateExpressions();

    if (templateExpressions != null) {
      for (TemplateExpression templateExpression : templateExpressions) {
        String fieldName = templateExpression.getFieldName();
        if (fieldName != null && fieldName.equals(SERVICE_ID)) {
          serviceTemplateExpression = templateExpression;
        } else if (fieldName != null && fieldName.equals(INFRA_MAPPING_ID)) {
          infraMappingTemplateExpression = templateExpression;
        } else if (fieldName != null && fieldName.equals(INFRA_DEFINITION_ID)) {
          infraDefinitionTemplateExpression = templateExpression;
        }
      }
    }

    Service service =
        phaseSubWorkflowHelperService.getService(serviceId, serviceTemplateExpression, app.getAppId(), context);

    InfrastructureDefinition infrastructureDefinition = phaseSubWorkflowHelperService.getInfraDefinition(
        infraDefinitionId, infraDefinitionTemplateExpression, app.getAppId(), context);

    Environment env = ((ExecutionContextImpl) context).getEnv();

    InfrastructureMapping infrastructureMapping = null;

    phaseSubWorkflowHelperService.validateEntitiesRelationship(service, infrastructureDefinition, infrastructureMapping,
        env, serviceTemplateExpression, infraMappingTemplateExpression, context.getAccountId());

    ExecutionResponseBuilder executionResponseBuilder = getSpawningExecutionResponse(
        context, workflowStandardParams, service, infrastructureMapping, infrastructureDefinition);

    PhaseExecutionData phaseExecutionData =
        obtainPhaseExecutionData(context, service, infrastructureDefinition, infrastructureMapping);

    // TODO => Saving PhaseExecutionData to SweepingOutput but still publishing to context. Must move this later

    executionResponseBuilder.stateExecutionData(phaseExecutionData);
    return executionResponseBuilder.build();
  }

  private PhaseExecutionData obtainPhaseExecutionData(ExecutionContext context, Service service,
      InfrastructureDefinition infrastructureDefinition, InfrastructureMapping infrastructureMapping) {
    PhaseExecutionDataBuilder phaseExecutionDataBuilder = aPhaseExecutionData();
    if (infrastructureMapping != null) {
      DeploymentType deploymentType =
          serviceResourceService.getDeploymentType(infrastructureMapping, null, infrastructureMapping.getServiceId());

      phaseExecutionDataBuilder.withComputeProviderId(infrastructureMapping.getComputeProviderSettingId())
          .withComputeProviderName(infrastructureMapping.getComputeProviderName())
          .withComputeProviderType(
              SettingVariableTypes.valueOf(infrastructureMapping.getComputeProviderType()).getDisplayName())
          .withInfraMappingId(infrastructureMapping.getUuid())
          .withInfraMappingName(infrastructureMapping.getName())
          .withDeploymentType(deploymentType.getDisplayName());
    }
    if (infrastructureDefinition != null) {
      SettingAttribute settingAttribute =
          settingsService.get(infrastructureDefinition.getInfrastructure().getCloudProviderId());
      phaseExecutionDataBuilder.withInfraDefinitionId(infrastructureDefinition.getUuid())
          .withDeploymentType(infrastructureDefinition.getDeploymentType().getDisplayName())
          .withComputeProviderType(infrastructureDefinition.getCloudProviderType().name())
          .withComputeProviderId(infrastructureDefinition.getInfrastructure().getCloudProviderId());
      if (settingAttribute != null) {
        phaseExecutionDataBuilder.withComputeProviderName(settingAttribute.getName());
      }
    }
    if (service != null) {
      phaseExecutionDataBuilder.withServiceId(service.getUuid()).withServiceName(service.getName());
    }
    PhaseExecutionData phaseExecutionData = phaseExecutionDataBuilder.build();
    if (infrastructureMapping instanceof ContainerInfrastructureMapping) {
      phaseExecutionData.setClusterName(((ContainerInfrastructureMapping) infrastructureMapping).getClusterName());

      StateExecutionData stateExecutionData = context.getStateExecutionData();
      if (stateExecutionData != null) {
        ContextElement element = stateExecutionData.getElement();
        if (element != null) {
          if (ContextElementType.CONTAINER_SERVICE == element.getElementType()) {
            ContainerServiceElement containerElement = (ContainerServiceElement) element;
            phaseExecutionData.setContainerServiceName(containerElement.getName());
          }
        }
      }
    }
    StateExecutionInstance stateExecutionInstance = ((ExecutionContextImpl) context).getStateExecutionInstance();
    sweepingOutputService.save(
        context.prepareSweepingOutputBuilder(Scope.WORKFLOW)
            .name(PhaseExecutionData.SWEEPING_OUTPUT_NAME + stateExecutionInstance.getDisplayName().trim())
            .value(phaseExecutionData)
            .build());
    return phaseExecutionData;
  }

  private ExecutionResponseBuilder getSpawningExecutionResponse(ExecutionContext context,
      WorkflowStandardParams workflowStandardParams, Service service, InfrastructureMapping infrastructureMapping,
      InfrastructureDefinition infrastructureDefinition) {
    ExecutionContextImpl contextImpl = (ExecutionContextImpl) context;
    StateExecutionInstance stateExecutionInstance = contextImpl.getStateExecutionInstance();
    List<String> correlationIds = new ArrayList<>();

    StateExecutionInstance childStateExecutionInstance = getSpawningInstance(context, workflowStandardParams,
        stateExecutionInstance, service, infrastructureMapping, infrastructureDefinition);
    correlationIds.add(stateExecutionInstance.getUuid());

    return ExecutionResponse.builder()
        .stateExecutionInstance(childStateExecutionInstance)
        .async(true)
        .correlationIds(correlationIds);
  }

  private StateExecutionInstance getSpawningInstance(ExecutionContext context,
      WorkflowStandardParams workflowStandardParams, StateExecutionInstance stateExecutionInstance, Service service,
      InfrastructureMapping infrastructureMapping, InfrastructureDefinition infrastructureDefinition) {
    StateExecutionInstance spawningInstance = super.getSpawningInstance(stateExecutionInstance);

    PhaseElementBuilder phaseElementBuilder = PhaseElement.builder()
                                                  .uuid(getId())
                                                  .phaseName(stateExecutionInstance.getDisplayName())
                                                  .appId(stateExecutionInstance.getAppId())
                                                  .workflowExecutionId(context.getWorkflowExecutionId())
                                                  .rollback(isRollback())
                                                  .onDemandRollback(workflowExecutionService.checkIfOnDemand(
                                                      context.getAppId(), context.getWorkflowExecutionId()))
                                                  .phaseNameForRollback(phaseNameForRollback);

    if (service != null) {
      ServiceElement serviceElement = ServiceElement.builder().build();
      MapperUtils.mapObject(service, serviceElement);
      phaseElementBuilder.serviceElement(serviceElement);
    }

    // Not null check is for build Workflow
    if (infrastructureDefinition != null) {
      phaseElementBuilder.deploymentType(infrastructureDefinition.getDeploymentType().name());
      phaseElementBuilder.infraDefinitionId(infrastructureDefinition.getUuid());
    } else if (infrastructureMapping != null) {
      DeploymentType deploymentType =
          serviceResourceService.getDeploymentType(infrastructureMapping, null, infrastructureMapping.getServiceId());
      phaseElementBuilder.deploymentType(deploymentType.name()).infraMappingId(infrastructureMapping.getUuid());
    }

    if (stateExecutionInstance.getRollbackPhaseName() != null) {
      phaseElementBuilder.phaseNameForRollback(stateExecutionInstance.getRollbackPhaseName());
    }

    if (isNotEmpty(getVariableOverrides())) {
      phaseElementBuilder.variableOverrides(getVariableOverrides());
    }

    PhaseElement phaseElement = phaseElementBuilder.build();

    spawningInstance.getContextElements().push(phaseElement);
    spawningInstance.setContextElement(phaseElement);

    if (infrastructureMapping != null) {
      infrastructureMappingService.saveInfrastructureMappingToSweepingOutput(stateExecutionInstance.getAppId(),
          context.getWorkflowExecutionId(), phaseElement, infrastructureMapping.getUuid());
    }

    if (isRollback() && workflowStandardParams.getWorkflowElement() != null) {
      // if last successful deployment found, save it in sweeping output
      if (workflowStandardParams.getWorkflowElement().getLastGoodDeploymentUuid() != null) {
        WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(
            workflowStandardParams.getAppId(), workflowStandardParams.getWorkflowElement().getLastGoodDeploymentUuid(),
            WorkflowExecutionKeys.executionArgs);

        if (workflowExecution == null) {
          log.error("ERROR: Last Good Deployment ID is not found - lastGoodDeploymentUuid: {}",
              workflowStandardParams.getWorkflowElement().getLastGoodDeploymentUuid());
          throw new InvalidRequestException("Last Good Deployment ID is not found");
        }
        if (!checkBuildPipeline(workflowStandardParams)) {
          phaseElement.setRollbackArtifactId(findRollbackArtifactId(service, workflowExecution));
        }
      }
    }
    return spawningInstance;
  }

  private boolean checkBuildPipeline(WorkflowStandardParams workflowStandardParams) {
    if (workflowStandardParams.getWorkflowElement() != null
        && workflowStandardParams.getWorkflowElement().getPipelineDeploymentUuid() != null) {
      WorkflowExecution pipelineExecution = workflowExecutionService.getWorkflowExecution(
          workflowStandardParams.getAppId(), workflowStandardParams.getWorkflowElement().getPipelineDeploymentUuid(),
          WorkflowExecutionKeys.pipelineExecution);

      if (pipelineExecution != null && pipelineExecution.getPipelineExecution().getPipelineStageExecutions() != null) {
        for (PipelineStageExecution pse : pipelineExecution.getPipelineExecution().getPipelineStageExecutions()) {
          if (pse.getWorkflowExecutions() != null) {
            for (WorkflowExecution memberWorkflowExecution : pse.getWorkflowExecutions()) {
              if (memberWorkflowExecution.getOrchestrationType() == OrchestrationWorkflowType.BUILD) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  @VisibleForTesting
  String findRollbackArtifactId(Service service, WorkflowExecution workflowExecution) {
    String rollbackArtifactId = null;
    if (workflowExecution.getExecutionArgs() != null && workflowExecution.getExecutionArgs().getArtifacts() != null) {
      if (service != null) {
        for (Artifact artifact : workflowExecution.getExecutionArgs().getArtifacts()) {
          List<String> artifactStreamIds = artifactStreamServiceBindingService.listArtifactStreamIds(service);
          if (isNotEmpty(artifactStreamIds) && artifactStreamIds.contains(artifact.getArtifactStreamId())) {
            rollbackArtifactId = artifact.getUuid();
            break;
          }
        }
      } else {
        // This can happen in case of build workflow
        rollbackArtifactId = workflowExecution.getExecutionArgs().getArtifacts().get(0).getUuid();
      }
    }
    return rollbackArtifactId;
  }

  private Artifact getArtifactByUuid(List<Artifact> artifacts, String artifactId) {
    if (artifactId == null) {
      return null;
    }
    if (isNotEmpty(artifacts)) {
      for (Artifact artifact : artifacts) {
        if (artifact.getUuid().equals(artifactId)) {
          // This will fetch artifactFiles as well
          return artifactService.getWithSource(artifactId);
        }
      }
    }
    return null;
  }

  private String getArtifactIdFromPreviousArtifactVariables(ArtifactVariable artifactVariable,
      List<ArtifactVariable> lastSuccessArtifactVariables, List<Artifact> lastSuccessArtifacts) {
    String artifactId = null;
    if (isNotEmpty(lastSuccessArtifactVariables)) {
      for (ArtifactVariable variable : lastSuccessArtifactVariables) {
        if (variable.getName().equals(artifactVariable.getName())
            && variable.getEntityId().equals(artifactVariable.getEntityId())) {
          return variable.getValue();
        }
      }
    } else if (isNotEmpty(lastSuccessArtifacts)) {
      artifactId = getArtifactIdFromPreviousArtifacts(artifactVariable, lastSuccessArtifacts);
    }

    return artifactId != null ? artifactId : artifactVariable.getValue();
  }

  private String getArtifactIdFromPreviousArtifacts(
      ArtifactVariable artifactVariable, List<Artifact> lastSuccessArtifacts) {
    if (isEmpty(lastSuccessArtifacts)) {
      return null;
    }
    for (Artifact artifact : lastSuccessArtifacts) {
      if (artifact.getServiceIds().contains(artifactVariable.getEntityId())) {
        return artifact.getUuid();
      }
    }
    return null;
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder();
    StateExecutionInstance stateExecutionInstance = ((ExecutionContextImpl) context).getStateExecutionInstance();
    super.handleStatusSummary(workflowExecutionService, context, response, executionResponseBuilder);
    response.values().forEach(notifyResponseData -> {
      if (notifyResponseData instanceof ElementNotifyResponseData) {
        List<ContextElement> notifyElements = ((ElementNotifyResponseData) notifyResponseData).getContextElements();
        if (isNotEmpty(notifyElements)) {
          for (ContextElement element : notifyElements) {
            executionResponseBuilder.contextElement(element);
          }
        }
      }
    });
    PhaseExecutionSummary phaseExecutionSummary = workflowExecutionService.getPhaseExecutionSummary(
        context.getAppId(), context.getWorkflowExecutionId(), context.getStateExecutionInstanceId());

    sweepingOutputService.save(
        context.prepareSweepingOutputBuilder(Scope.WORKFLOW)
            .name(PhaseExecutionSummary.SWEEPING_OUTPUT_NAME + stateExecutionInstance.getDisplayName().trim())
            .value(phaseExecutionSummary)
            .build());
    executionResponseBuilder.stateExecutionData(context.getStateExecutionData());
    return executionResponseBuilder.build();
  }

  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  @SchemaIgnore
  public String getUuid() {
    return uuid;
  }

  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

  public String getPhaseNameForRollback() {
    return phaseNameForRollback;
  }

  public void setPhaseNameForRollback(String phaseNameForRollback) {
    this.phaseNameForRollback = phaseNameForRollback;
  }

  @SchemaIgnore
  public String getInfraMappingId() {
    return infraMappingId;
  }

  public void setInfraMappingId(String infraMappingId) {
    this.infraMappingId = infraMappingId;
  }

  @SchemaIgnore
  public String getInfraDefinitionId() {
    return infraMappingId;
  }

  public void setInfraDefinitionId(String infraDefinitionId) {
    this.infraDefinitionId = infraDefinitionId;
  }
}
