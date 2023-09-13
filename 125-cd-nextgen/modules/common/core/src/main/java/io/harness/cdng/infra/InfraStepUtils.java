/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.ng.core.environment.mappers.EnvironmentMapper.toNGEnvironmentConfig;
import static io.harness.ng.core.environment.mappers.EnvironmentMapper.toYaml;
import static io.harness.ng.core.mapper.TagMapper.convertToList;

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
import io.harness.cdng.environment.EnvironmentMapper;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PrincipalTypeProtoToPrincipalTypeMapper;
import io.harness.pms.tags.TagUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.repositories.UpsertOptions;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDC)
@UtilityClass
@Slf4j
public class InfraStepUtils {
  public static List<String> envFieldToBeUpdated = Arrays.asList("type", "name");
  public void validateResources(
      AccessControlClient accessControlClient, Ambiance ambiance, InfraSectionStepParameters stepParameters) {
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

    if (stepParameters.getEnvironmentRef() == null
        || EmptyPredicate.isEmpty(stepParameters.getEnvironmentRef().getValue())) {
      accessControlClient.checkForAccessOrThrow(Principal.of(principalType, principal),
          ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier), Resource.of("ENVIRONMENT", null),
          CDNGRbacPermissions.ENVIRONMENT_CREATE_PERMISSION, "Missing Environment Create Permission");
    } else {
      accessControlClient.checkForAccessOrThrow(Principal.of(principalType, principal),
          ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
          Resource.of("ENVIRONMENT", stepParameters.getEnvironmentRef().getValue()),
          CDNGRbacPermissions.ENVIRONMENT_RUNTIME_PERMISSION,
          String.format(
              "Missing Access Permission for Environment: [%s]", stepParameters.getEnvironmentRef().getValue()));
    }
  }

  public EnvironmentOutcome processEnvironment(EnvironmentService environmentService, Ambiance ambiance,
      EnvironmentYaml environmentYaml, ParameterField<String> environmentRef) {
    // environmentRef and environmentYaml are annotated with @OneOfField in PipelineInfrastructure.java
    Optional<Environment> environmentInDb = Optional.empty();
    if (environmentRef != null && isNotBlank(environmentRef.getValue())) {
      environmentInDb = getEnvironment(environmentService, ambiance, environmentRef.getValue());
    } else if (environmentYaml != null) {
      environmentInDb = getEnvironment(environmentService, ambiance, environmentYaml.getIdentifier());
    }

    if (environmentInDb.isEmpty() && environmentYaml == null) {
      throw new InvalidRequestException("Env with identifier " + environmentRef.getValue() + " does not exist");
    }

    if (environmentYaml == null) {
      environmentYaml = createEnvYamlFromEnvRef(environmentInDb.get());
    }
    if (EmptyPredicate.isEmpty(environmentYaml.getName())) {
      environmentYaml.setName(environmentYaml.getIdentifier());
    }

    Environment environment = getEnvironmentObject(environmentYaml, ambiance);

    // To support EnvironmentV2 entities in older EnvironmentV1 supported pipeline
    String updatedYaml = getUpdatedEnvironmentYaml(
        environmentInDb.isPresent() && isNotBlank(environmentInDb.get().getYaml()) ? environmentInDb.get()
                                                                                   : environment,
        environmentYaml);
    if (isNotBlank(updatedYaml)) {
      environment.setYaml(updatedYaml);
    }

    environmentService.upsert(environment, UpsertOptions.DEFAULT.withNoOutbox());
    return EnvironmentMapper.toOutcome(environmentYaml);
  }

  private static String getUpdatedEnvironmentYaml(
      @NonNull Environment environment, @NonNull EnvironmentYaml environmentYaml) {
    try {
      if (isNotBlank(environment.getYaml())) {
        YamlField yamlField = YamlUtils.readTree(environment.getYaml());
        YamlNode yamlNode = yamlField.getNode();
        if (yamlNode != null && yamlNode.isObject() && yamlNode.getCurrJsonNode().get("environment") != null
            && yamlNode.getCurrJsonNode().get("environment").isObject()) {
          ObjectNode objectNode = (ObjectNode) yamlNode.getCurrJsonNode().get("environment");
          if (objectNode != null) {
            objectNode.put(EnvironmentKeys.name, environmentYaml.getName());
            objectNode.put(EnvironmentKeys.type, environmentYaml.getType().toString());
            objectNode.replace(EnvironmentKeys.tags, JsonPipelineUtils.asTree(environmentYaml.getTags()));
            objectNode.put(EnvironmentKeys.description, environmentYaml.getDescription().getValue());
          }
        }
        return YamlUtils.writeYamlString(yamlField).replaceFirst("---\n", "");
      } else {
        NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environment);
        return toYaml(ngEnvironmentConfig);
      }

    } catch (Exception e) {
      log.warn("updating environment yaml operation failed", e);
      return StringUtils.EMPTY;
    }
  }

  private Optional<Environment> getEnvironment(
      EnvironmentService environmentService, Ambiance ambiance, String envIdentifier) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    return environmentService.get(accountId, orgIdentifier, projectIdentifier, envIdentifier, false);
  }

  private Environment getEnvironmentObject(EnvironmentYaml environmentYaml, Ambiance ambiance) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);

    TagUtils.removeUuidFromTags(environmentYaml.getTags());

    return Environment.builder()
        .name(environmentYaml.getName())
        .accountId(accountId)
        .type(environmentYaml.getType())
        .identifier(environmentYaml.getIdentifier())
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .tags(convertToList(environmentYaml.getTags()))
        .description(ParameterFieldHelper.getParameterFieldValueHandleValueNull(environmentYaml.getDescription()))
        .build();
  }

  private EnvironmentYaml createEnvYamlFromEnvRef(Environment env) {
    return EnvironmentYaml.builder()
        .identifier(env.getIdentifier())
        .name(env.getName())
        .description(env.getDescription() == null ? ParameterField.createValueField("")
                                                  : ParameterField.createValueField(env.getDescription()))
        .type(env.getType())
        .tags(TagMapper.convertToMap(env.getTags()))
        .build();
  }
}
