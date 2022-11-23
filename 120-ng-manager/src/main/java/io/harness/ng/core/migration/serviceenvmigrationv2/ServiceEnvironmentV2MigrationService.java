package io.harness.ng.core.migration.serviceenvmigrationv2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.customdeploymentng.CustomDeploymentInfrastructureHelper;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.mapper.InfrastructureMapper;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.EnvironmentValidationHelper;
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.dto.InfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.StageRequestDto;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.StageResponseDto;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.YamlPipelineUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static java.lang.String.format;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class ServiceEnvironmentV2MigrationService {

    @Inject private ServiceEntityService serviceEntityService;
    @Inject private InfrastructureEntityService infrastructureEntityService;
    @Inject private CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper;
    @Inject private OrgAndProjectValidationHelper orgAndProjectValidationHelper;
    @Inject private EnvironmentValidationHelper environmentValidationHelper;
    @Inject private AccessControlClient accessControlClient;

    private StageSchema getStageSchema(String yaml) {
        try{
            return YamlPipelineUtils.read(yaml, StageSchema.class);
        }
        catch (IOException ex) {
            throw new InvalidRequestException("not able to create stage Schema due to " + ex.getMessage());
        }
    }

    private String toYaml(StageSchema stageSchema) {
        try {
            return YamlPipelineUtils.getYamlString(stageSchema);
        } catch (IOException e) {
            throw new InvalidRequestException("not able to create yaml from stage Schema due to " + e.getMessage());
        }
    }

    public StageResponseDto createServiceInfraV2(StageRequestDto stageRequestDto, String accountId) {
        // add check to see cd stage
        if(isEmpty(stageRequestDto.getYaml())){
            throw new InvalidRequestException("stage yaml can't be empty");
        }
        // check if stage belongs to CD----done
        // check if service belongs to v1----done
        // check if infra belongs to v1-----done
        // check that v2 service do not exist with identifier-----done
        // check that v2 infra do not exist with identifier-----done
        // check if service ref and env ref are fixed values----done
        // check access control----done
        // add validation for infra identifier---done
        StageSchema stageSchema = getStageSchema(stageRequestDto.getYaml());
        DeploymentStageConfig deploymentStageConfig = stageSchema.getStageNode().getDeploymentStageConfig();
        YamlField stageField=getStageYamlField(stageRequestDto.getYaml());

        validateOldService(deploymentStageConfig);
        validateOldInfra(deploymentStageConfig);

        ServiceConfig serviceConfig = deploymentStageConfig.getServiceConfig();
        validateParameterRef(serviceConfig.getServiceRef(), "serviceRef");
        String serviceRef = serviceConfig.getServiceRef().getValue();

        PipelineInfrastructure infrastructure = deploymentStageConfig.getInfrastructure();
        validateParameterRef(infrastructure.getEnvironmentRef(), "environmentRef");
        String environmentRef = infrastructure.getEnvironmentRef().getValue();

        orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
                stageRequestDto.getOrgIdentifier(), stageRequestDto.getProjectIdentifier(), accountId);
        environmentValidationHelper.checkThatEnvExists(accountId, stageRequestDto.getOrgIdentifier(),
                stageRequestDto.getProjectIdentifier(), environmentRef);

        ServiceEntity serviceEntity = getServiceV1(accountId, stageRequestDto.getOrgIdentifier(),
                stageRequestDto.getProjectIdentifier(), serviceRef);
        evaluateServiceV2(serviceEntity, serviceConfig, stageField);

        checkInfraExistence(accountId, stageRequestDto.getOrgIdentifier(), stageRequestDto.getProjectIdentifier(),
                environmentRef, stageRequestDto.getInfraIdentifier());
        InfrastructureEntity infrastructureEntity = evaluateInfraV2(infrastructure, stageRequestDto, serviceConfig, accountId);

        accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, stageRequestDto.getOrgIdentifier()
                        , stageRequestDto.getProjectIdentifier()),
                Resource.of(NGResourceType.ENVIRONMENT, environmentRef), ENVIRONMENT_UPDATE_PERMISSION,
                "unable to create infrastructure");

        accessControlClient.checkForAccessOrThrow(
                ResourceScope.of(accountId, stageRequestDto.getOrgIdentifier(), stageRequestDto.getProjectIdentifier()),
                Resource.of(NGResourceType.SERVICE, null), SERVICE_UPDATE_PERMISSION,
                "unable to update service");

        InfrastructureEntity createdInfrastructure = infrastructureEntityService.create(infrastructureEntity);
        ServiceEntity updatedService = serviceEntityService.update(serviceEntity);

        ObjectMapper objectMapper = new ObjectMapper();
        YamlField serviceV2Field = new YamlField(new YamlNode("service",
                objectMapper.createObjectNode().set("service", objectMapper.createObjectNode())));
        ObjectNode serviceNode = (ObjectNode) serviceV2Field.getNode().getField("service").getNode().getCurrJsonNode();
        serviceNode.put("serviceRef", updatedService.getIdentifier());

        JsonNode infraNode = objectMapper.createObjectNode().put("identifier",createdInfrastructure.getIdentifier());
        ArrayNode infraArrayNode = objectMapper.createArrayNode().add(infraNode);
        YamlField envV2Field = new YamlField(new YamlNode("environment",
                objectMapper.createObjectNode().set("environment", objectMapper.createObjectNode())));
        ObjectNode envNode = (ObjectNode) envV2Field.getNode().getField("environment").getNode().getCurrJsonNode();
        envNode.put("environmentRef", createdInfrastructure.getEnvIdentifier());
        envNode.put("deployToAll", false);
        envNode.set("infrastructureDefinitions",infraArrayNode);

        ObjectNode  stageSpecNode = (ObjectNode) stageField.getNode().getField("spec").getNode().getCurrJsonNode();
        stageSpecNode.remove("serviceConfig");
        stageSpecNode.remove("infrastructure");
        stageSpecNode.put("deploymentType", updatedService.getType().getYamlName());
        stageSpecNode.set("service", serviceV2Field.getNode().getField("service").getNode().getCurrJsonNode());
        stageSpecNode.set("environment", envV2Field.getNode().getField("environment").getNode().getCurrJsonNode());

        return StageResponseDto.builder()
                .yaml(YamlPipelineUtils.writeYamlString(stageField.getNode().getCurrJsonNode()))
                .build();
    }

    private YamlField getStageYamlField(String yaml) {
        try{
            return YamlUtils.readTree(yaml).getNode().getField("stage");
        }
        catch (Exception e) {
            throw new InvalidRequestException(format("not able to parse stage yaml because of error: %s",e.getMessage()));
        }
    }

    private InfrastructureEntity evaluateInfraV2(PipelineInfrastructure infrastructure, StageRequestDto stageRequestDto,
                                                 ServiceConfig serviceConfig, String accountId) {
        InfrastructureDefinitionConfig infrastructureDefinitionConfig = InfrastructureDefinitionConfig.builder()
                .identifier(stageRequestDto.getInfraIdentifier())
                .name(stageRequestDto.getInfraIdentifier()) // name is same as identifier as of now
                .orgIdentifier(stageRequestDto.getOrgIdentifier())
                .projectIdentifier(stageRequestDto.getProjectIdentifier())
                .environmentRef(infrastructure.getEnvironmentRef().getValue())
                .allowSimultaneousDeployments(isAllowSimultaneousDeployments(infrastructure.getAllowSimultaneousDeployments()))
                .deploymentType(serviceConfig.getServiceDefinition().getType())
                .type(infrastructure.getInfrastructureDefinition().getType())
                .spec(infrastructure.getInfrastructureDefinition().getSpec())
                .build();

        InfrastructureConfig infrastructureConfig = InfrastructureConfig.builder()
                .infrastructureDefinitionConfig(infrastructureDefinitionConfig)
                .build();
        InfrastructureRequestDTO infrastructureRequestDTO = InfrastructureRequestDTO.builder()
                .identifier(infrastructureDefinitionConfig.getIdentifier())
                .name(infrastructureDefinitionConfig.getName())
                .orgIdentifier(infrastructureDefinitionConfig.getOrgIdentifier())
                .projectIdentifier(infrastructureDefinitionConfig.getProjectIdentifier())
                .environmentRef(infrastructureDefinitionConfig.getEnvironmentRef())
                .type(infrastructureDefinitionConfig.getType())
                .yaml(InfrastructureEntityConfigMapper.toYaml(infrastructureConfig))
                .build();
        InfrastructureEntity infrastructureEntity = InfrastructureMapper.toInfrastructureEntity(accountId,
                infrastructureRequestDTO);
        if (infrastructureEntity.getDeploymentType() == ServiceDefinitionType.CUSTOM_DEPLOYMENT
                && infrastructureEntity.getType() == InfrastructureType.CUSTOM_DEPLOYMENT) {
            if (customDeploymentInfrastructureHelper.isNotValidInfrastructureYaml(infrastructureEntity)) {
                throw new InvalidRequestException(
                        "Infrastructure yaml is not valid, template variables and infra variables doesn't match");
            }
        }
        return infrastructureEntity;
    }

    private ServiceEntity evaluateServiceV2(ServiceEntity serviceEntity, ServiceConfig serviceConfig, YamlField stageField) {

        YamlField serviceConfigField = stageField.getNode().getField("spec").getNode().getField("serviceConfig");
        YamlField serviceDefinitionField = serviceConfigField.getNode().getField("serviceDefinition");


        ObjectMapper objectMapper = new ObjectMapper();
        YamlField serviceV2Field = new YamlField(new YamlNode("service",
                objectMapper.createObjectNode().set("service", objectMapper.createObjectNode())));
        ObjectNode serviceNode = (ObjectNode) serviceV2Field.getNode().getField("service").getNode().getCurrJsonNode();
        serviceNode.put("name", serviceEntity.getName());
        serviceNode.put("identifier", serviceEntity.getIdentifier());
        serviceNode.put("description", serviceEntity.getDescription());
        serviceNode.put("name", serviceEntity.getName());
        serviceNode.putPOJO("tags", TagMapper.convertToMap(serviceEntity.getTags()));
        serviceNode.set("serviceDefinition", serviceDefinitionField.getNode().getCurrJsonNode());

        String serviceYaml = YamlPipelineUtils.writeYamlString(serviceV2Field.getNode().getCurrJsonNode());
        serviceEntity.setYaml(serviceYaml);
        serviceEntity.setType(serviceConfig.getServiceDefinition().getType());
        // gitops is not considered here as of now

        final NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity);
        if(ngServiceConfig==null) {
            throw new InvalidRequestException("not able to evaluate service v2 yaml");
        }
        return serviceEntity;
    }

    private ServiceEntity getServiceV1(String accountId, String orgIdentifier,
                                       String projectIdentifier, String serviceIdentifier) {
        Optional<ServiceEntity> optionalService = serviceEntityService.get(accountId, orgIdentifier,
                projectIdentifier, serviceIdentifier, false);
        if(optionalService.isPresent()) {
            ServiceEntity serviceEntity = optionalService.get();
            try {
                NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity);
                if(ngServiceConfig!= null && (ngServiceConfig.getNgServiceV2InfoConfig().getGitOpsEnabled() ||
                         ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition()!=null ||
                        ngServiceConfig.getNgServiceV2InfoConfig().getUseFromStage()!=null)) {
                    throw new InvalidRequestException(format("a service v2 already exists with identifier: %s", serviceIdentifier));

                }
            }
            catch (Exception e) {
                log.info(format("a service v2 doesn't exists with identifier: %s", serviceIdentifier));
            }
            return serviceEntity;
        }
        throw new InvalidRequestException(format("service is not present with identifier: %s", serviceIdentifier));
    }

    private void checkInfraExistence(String accountId, String orgIdentifier,
                                     String projectIdentifier, String envIdentifier, String infraIdentifier) {
        Optional<InfrastructureEntity> optionalInfra =
                infrastructureEntityService.get(accountId, orgIdentifier, projectIdentifier, envIdentifier, infraIdentifier);
        if(optionalInfra.isPresent()) {
            throw new InvalidRequestException(format("a infra v2 already exists with identifier: %s", infraIdentifier));
        }
    }

    private boolean isAllowSimultaneousDeployments(ParameterField<Boolean> allowSimultaneousDeployments) {
        if(allowSimultaneousDeployments.getValue()!=null) {
            return allowSimultaneousDeployments.getValue();
        }
        return false;
    }

    private void validateParameterRef(ParameterField<String> parameterRef, String parameter) {
        if(parameterRef==null || parameterRef.isExpression() || isEmpty(parameterRef.getValue())) {
            throw new InvalidRequestException(format("either %s is having expressions or it is not present in" +
                    "stage yaml or its value is an empty string", parameter));
        }
    }

    private void validateOldService(DeploymentStageConfig deploymentStageConfig) {
        if(deploymentStageConfig.getServiceConfig()==null || deploymentStageConfig.getService()!=null ||
        deploymentStageConfig.getServices()!=null) {
            throw new InvalidRequestException("either service v1 is not present in" +
                    "stage yaml or service v2 exist in yaml");
        }
    }

    private void validateOldInfra(DeploymentStageConfig deploymentStageConfig) {
        if(deploymentStageConfig.getInfrastructure()==null || deploymentStageConfig.getEnvironment()!=null ||
        deploymentStageConfig.getEnvironments()!=null) {
            throw new InvalidRequestException("either infra v1 is not present in" +
                    "stage yaml or environment v2 exist in yaml");
        }
    }

}
