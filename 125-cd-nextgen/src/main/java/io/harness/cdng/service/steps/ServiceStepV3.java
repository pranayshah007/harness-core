package io.harness.cdng.service.steps;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static java.lang.String.format;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.rbac.PrincipalTypeProtoToPrincipalTypeMapper;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.utils.YamlPipelineUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class ServiceStepV3 implements SyncExecutable<ServiceStepV3Parameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(ExecutionNodeType.SERVICE_V3.getName()).setStepCategory(StepCategory.STEP).build();
  public static final String SERVICE_SWEEPING_OUTPUT = "serviceSweepingOutput";
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private ServiceEntityService serviceEntityService;

  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  @Override
  public Class<ServiceStepV3Parameters> getStepParametersClass() {
    return ServiceStepV3Parameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, ServiceStepV3Parameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    // Todo: check if service ref is resolved

    final Optional<ServiceEntity> serviceOpt =
        serviceEntityService.get(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
            AmbianceUtils.getProjectIdentifier(ambiance), stepParameters.getServiceRef().getValue(), false);
    if (serviceOpt.isEmpty()) {
      throw new InvalidRequestException(
          format("serviceOpt with identifier %s not found", stepParameters.getServiceRef()));
    }

    final ServiceEntity serviceEntity = serviceOpt.get();
    final String mergedServiceYaml;
    if (stepParameters.getInputs() != null && isNotEmpty(stepParameters.getInputs().getValue())) {
      mergedServiceYaml = mergeServiceInputsIntoService(serviceEntity.getYaml(), stepParameters.getInputs().getValue());
    } else {
      mergedServiceYaml = serviceEntity.getYaml();
    }

    final NGServiceConfig ngServiceConfig;
    try {
      ngServiceConfig = YamlUtils.read(mergedServiceYaml, NGServiceConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException("corrupt service yaml for service " + serviceEntity.getIdentifier(), e);
    }

    // Todo:(yogesh) check the step category
    sweepingOutputService.consume(ambiance, SERVICE_SWEEPING_OUTPUT,
        ServiceSweepingOutput.builder().finalServiceYaml(mergedServiceYaml).build(), StepCategory.STAGE.name());

    final NGServiceV2InfoConfig ngServiceV2InfoConfig = ngServiceConfig.getNgServiceV2InfoConfig();

    validateResources(ambiance, ngServiceConfig);

    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(
            StepResponse.StepOutcome.builder()
                .name(OutcomeExpressionConstants.SERVICE)
                .outcome(ServiceStepOutcome.fromServiceStepV2(serviceEntity.getIdentifier(), serviceEntity.getName(),
                    ngServiceV2InfoConfig.getServiceDefinition().getType().getYamlName(),
                    serviceEntity.getDescription(), ngServiceV2InfoConfig.getTags(), serviceEntity.getGitOpsEnabled()))
                .group(StepCategory.STAGE.name())
                .build())
        .build();
  }

  private void validateResources(Ambiance ambiance, NGServiceConfig serviceConfig) {
    final ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    final String principal = executionPrincipalInfo.getPrincipal();
    if (isEmpty(principal)) {
      return;
    }

    io.harness.accesscontrol.principals.PrincipalType principalType =
        PrincipalTypeProtoToPrincipalTypeMapper.convertToAccessControlPrincipalType(
            executionPrincipalInfo.getPrincipalType());
    ServiceDefinition serviceDefinition = serviceConfig.getNgServiceV2InfoConfig().getServiceDefinition();
    Set<EntityDetailProtoDTO> entityDetails =
        entityReferenceExtractorUtils.extractReferredEntities(ambiance, serviceDefinition);
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails);
    accessControlClient.checkForAccessOrThrow(io.harness.accesscontrol.acl.api.Principal.of(principalType, principal),
        io.harness.accesscontrol.acl.api.ResourceScope.of(AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance)),
        io.harness.accesscontrol.acl.api.Resource.of(
            NGResourceType.SERVICE, serviceConfig.getNgServiceV2InfoConfig().getIdentifier()),
        CDNGRbacPermissions.SERVICE_RUNTIME_PERMISSION, "Validation for Service Step failed");
  }

  private String mergeServiceInputsIntoService(String originalServiceYaml, Map<String, Object> serviceInputs) {
    Map<String, Object> serviceInputsYaml = new HashMap<>();
    serviceInputsYaml.put(YamlTypes.SERVICE_ENTITY, serviceInputs);
    return MergeHelper.mergeInputSetFormatYamlToOriginYaml(
        originalServiceYaml, YamlPipelineUtils.writeYamlString(serviceInputsYaml));
  }
}
