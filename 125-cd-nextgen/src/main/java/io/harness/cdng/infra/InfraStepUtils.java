/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.ng.core.mapper.TagMapper.convertToList;

import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.environment.EnvironmentMapper;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.infra.steps.InfraSectionStepParameters;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PrincipalTypeProtoToPrincipalTypeMapper;
import io.harness.pms.tags.TagUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.repositories.UpsertOptions;
import io.harness.steps.environment.EnvironmentOutcome;

import java.util.Optional;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class InfraStepUtils {
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
    if (environmentYaml == null) {
      environmentYaml = createEnvYamlFromEnvRef(environmentService, ambiance, environmentRef);
    }
    if (EmptyPredicate.isEmpty(environmentYaml.getName())) {
      environmentYaml.setName(environmentYaml.getIdentifier());
    }
    Environment environment = getEnvironmentObject(environmentYaml, ambiance);
    environmentService.upsert(environment, UpsertOptions.DEFAULT.withNoOutbox());
    return EnvironmentMapper.toOutcome(environmentYaml);
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

  private EnvironmentYaml createEnvYamlFromEnvRef(
      EnvironmentService environmentService, Ambiance ambiance, ParameterField<String> environmentRef) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String envIdentifier = environmentRef.getValue();

    Optional<Environment> optionalEnvironment =
        environmentService.get(accountId, orgIdentifier, projectIdentifier, envIdentifier, false);
    if (optionalEnvironment.isPresent()) {
      Environment env = optionalEnvironment.get();
      return EnvironmentYaml.builder()
          .identifier(envIdentifier)
          .name(env.getName())
          .description(env.getDescription() == null ? ParameterField.createValueField("")
                                                    : ParameterField.createValueField(env.getDescription()))
          .type(env.getType())
          .tags(TagMapper.convertToMap(env.getTags()))
          .build();
    }
    throw new InvalidRequestException("Env with identifier " + envIdentifier + " does not exist");
  }
}
