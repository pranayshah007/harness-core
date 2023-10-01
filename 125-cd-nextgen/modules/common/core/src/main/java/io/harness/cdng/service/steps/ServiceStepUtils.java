/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceYaml;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.rbac.PrincipalTypeProtoToPrincipalTypeMapper;
import io.harness.pms.tags.TagUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import java.util.Set;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDC)
@UtilityClass
@Slf4j
public class ServiceStepUtils {
  public void validateResources(EntityReferenceExtractorUtils entityReferenceExtractorUtils,
      PipelineRbacHelper pipelineRbacHelper, AccessControlClient accessControlClient, Ambiance ambiance,
      ServiceStepParameters stepParameters) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    String principal = executionPrincipalInfo.getPrincipal();
    if (EmptyPredicate.isEmpty(principal)) {
      return;
    }

    PrincipalType principalType = PrincipalTypeProtoToPrincipalTypeMapper.convertToAccessControlPrincipalType(
        executionPrincipalInfo.getPrincipalType());
    ServiceConfig serviceConfig = stepParameters.getServiceConfigInternal().getValue();
    Set<EntityDetailProtoDTO> entityDetails =
        entityReferenceExtractorUtils.extractReferredEntities(ambiance, serviceConfig);
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails);
    if (stepParameters.getServiceRefInternal() == null
        || EmptyPredicate.isEmpty(stepParameters.getServiceRefInternal().getValue())) {
      accessControlClient.checkForAccessOrThrow(Principal.of(principalType, principal),
          ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier), Resource.of("SERVICE", null),
          CDNGRbacPermissions.SERVICE_CREATE_PERMISSION, "Validation for Service Step failed");
    }
  }

  // NOTE: Returned service entity shouldn't contain a version. Multiple stages running in parallel might see
  // DuplicateKeyException if they're trying to deploy the same service.
  public ServiceEntity getServiceEntity(
      ServiceEntityService serviceEntityService, Ambiance ambiance, ServiceStepParameters stepParameters) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);

    Optional<ServiceEntity> serviceEntity = Optional.empty();
    if (stepParameters.getServiceRefInternal() != null
        && EmptyPredicate.isNotEmpty(stepParameters.getServiceRefInternal().getValue())) {
      String serviceIdentifier = stepParameters.getServiceRefInternal().getValue();
      serviceEntity = serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, false);
      if (serviceEntity.isPresent()) {
        ServiceEntity finalServiceEntity = serviceEntity.get();
        finalServiceEntity.setVersion(null);
        return finalServiceEntity;
      } else {
        throw new InvalidRequestException("Service with identifier " + serviceIdentifier + " does not exist");
      }
    }

    TagUtils.removeUuidFromTags(stepParameters.getTags());

    serviceEntity = Optional.of(
        ServiceEntity.builder()
            .identifier(stepParameters.getIdentifier())
            .name(stepParameters.getName())
            .description(ParameterFieldHelper.getParameterFieldValueHandleValueNull(stepParameters.getDescription()))
            .projectIdentifier(projectIdentifier)
            .orgIdentifier(orgIdentifier)
            .accountId(accountId)
            .tags(TagMapper.convertToList(stepParameters.getTags()))
            .build());

    String updatedYaml = StringUtils.EMPTY;
    ParameterField<ServiceConfig> serviceConfigInternal = stepParameters.getServiceConfigInternal();
    if (serviceConfigInternal != null && serviceConfigInternal.getValue() != null
        && serviceConfigInternal.getValue().getService() != null) {
      ServiceYaml service = serviceConfigInternal.getValue().getService();
      Optional<ServiceEntity> serviceEntityInDb =
          serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, stepParameters.getIdentifier(), false);
      updatedYaml = getUpdatedServiceYaml(serviceEntityInDb.isPresent() && isNotBlank(serviceEntityInDb.get().getYaml())
              ? serviceEntityInDb.get()
              : serviceEntity.get(),
          service);
    }

    ServiceEntity finalServiceEntity = serviceEntity.get();
    if (isNotBlank(updatedYaml)) {
      finalServiceEntity.setYaml(updatedYaml);
    }
    return finalServiceEntity;
  }

  public static String getUpdatedServiceYaml(@NonNull ServiceEntity serviceEntity, @NonNull ServiceYaml serviceYaml) {
    try {
      if (isNotBlank(serviceEntity.getYaml())) {
        YamlField yamlField = YamlUtils.readTree(serviceEntity.getYaml());
        YamlNode yamlNode = yamlField.getNode();
        if (yamlNode != null && yamlNode.isObject() && yamlNode.getCurrJsonNode().get("service") != null
            && yamlNode.getCurrJsonNode().get("service").isObject()) {
          ObjectNode objectNode = (ObjectNode) yamlNode.getCurrJsonNode().get("service");
          if (objectNode != null) {
            objectNode.put(ServiceEntityKeys.name, serviceYaml.getName());
            TagUtils.removeUuidFromTags(serviceYaml.getTags());
            objectNode.replace(ServiceEntityKeys.tags, JsonPipelineUtils.asTree(serviceYaml.getTags()));
            objectNode.put(ServiceEntityKeys.description, serviceYaml.getDescription().getValue());
          }
        }
        return YamlUtils.writeYamlString(yamlField).replaceFirst("---\n", "");

      } else {
        NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity);
        return NGServiceEntityMapper.toYaml(ngServiceConfig);
      }
    } catch (Exception e) {
      log.warn("updating service yaml operation failed", e);
      return StringUtils.EMPTY;
    }
  }
}
