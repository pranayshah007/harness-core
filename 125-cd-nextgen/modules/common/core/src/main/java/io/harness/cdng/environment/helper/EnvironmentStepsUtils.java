/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.exception.WingsException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PrincipalTypeProtoToPrincipalTypeMapper;
import io.harness.pms.yaml.ParameterField;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.utils.IdentifierRefHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class EnvironmentStepsUtils {
  public static final String ENVIRONMENT_RESOURCE_TYPE = "ENVIRONMENT";

  /**
   * Checks whether the principal set in ambiance has core_environment_access permission for the given environment.
   * Throws NGAccessDeniedException otherwise
   * @param accessControlClient instance of the access control client
   * @param ambiance ambiance of the plan execution
   * @param environmentRef environment reference. If account or org scoped, it should be account.envId or org.envId
   * @throws NGAccessDeniedException if the principal does not core_environment_access permissions for the given
   *     environment
   */
  public void checkForEnvAccessOrThrow(@NotNull AccessControlClient accessControlClient, @NotNull Ambiance ambiance,
      ParameterField<String> environmentRef) throws NGAccessDeniedException {
    final String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    final String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    final ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    final String principal = executionPrincipalInfo.getPrincipal();
    if (isEmpty(principal)) {
      return;
    }
    PrincipalType principalType = PrincipalTypeProtoToPrincipalTypeMapper.convertToAccessControlPrincipalType(
        executionPrincipalInfo.getPrincipalType());

    if (ParameterField.isNotNull(environmentRef)) {
      if (environmentRef.isExpression()) {
        throw new UnresolvedExpressionsException(List.of(environmentRef.getExpressionValue()));
      }

      if (isNotEmpty(environmentRef.getValue())) {
        IdentifierRef envIdentifierRef = IdentifierRefHelper.getIdentifierRef(
            environmentRef.getValue(), accountIdentifier, orgIdentifier, projectIdentifier);
        accessControlClient.checkForAccessOrThrow(Principal.of(principalType, principal),
            ResourceScope.of(envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
                envIdentifierRef.getProjectIdentifier()),
            Resource.of(ENVIRONMENT_RESOURCE_TYPE, envIdentifierRef.getIdentifier()),
            CDNGRbacPermissions.ENVIRONMENT_RUNTIME_PERMISSION,
            String.format("Missing Access Permission for Environment: [%s]", environmentRef.getValue()));
      }
    }
  }

  /**
   * Checks whether the principal set in ambiance has core_environment_access permission for the given environments.
   * Throws NGAccessDeniedException if it does not have permission for at least 1 environment
   * @param accessControlClient instance of the access control client
   * @param ambiance ambiance of the plan execution
   * @param environments List of environment entities. These can be from multiple scopes (account/org/project). The
   *     scope if figured from the properties of the environment entity
   * @throws NGAccessDeniedException if the principal does not have permission for at least 1 environment
   */
  public void checkForAllEnvsAccessOrThrow(@NotNull AccessControlClient accessControlClient, @NotNull Ambiance ambiance,
      List<Environment> environments) throws NGAccessDeniedException {
    if (EmptyPredicate.isEmpty(environments)) {
      return;
    }
    final ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    final String principal = executionPrincipalInfo.getPrincipal();
    if (isEmpty(principal)) {
      return;
    }
    PrincipalType principalType = PrincipalTypeProtoToPrincipalTypeMapper.convertToAccessControlPrincipalType(
        executionPrincipalInfo.getPrincipalType());

    List<PermissionCheckDTO> permissionCheckDTOs = new ArrayList<>();
    for (Environment environment : environments) {
      permissionCheckDTOs.add(PermissionCheckDTO.builder()
                                  .permission(CDNGRbacPermissions.ENVIRONMENT_RUNTIME_PERMISSION)
                                  .resourceScope(ResourceScope.of(environment.getAccountId(),
                                      environment.getOrgIdentifier(), environment.getProjectIdentifier()))
                                  .resourceType(ENVIRONMENT_RESOURCE_TYPE)
                                  .resourceIdentifier(environment.getIdentifier())
                                  .build());
    }
    if (isNotEmpty(permissionCheckDTOs)) {
      AccessCheckResponseDTO accessCheckResponseDTO =
          accessControlClient.checkForAccess(Principal.of(principalType, principal), permissionCheckDTOs);

      Map<ResourceScope, List<AccessControlDTO>> deniedEnvironmentsByScope =
          accessCheckResponseDTO.getAccessControlList()
              .stream()
              .filter(dto -> !dto.isPermitted())
              .collect(Collectors.groupingBy(AccessControlDTO::getResourceScope));

      if (isNotEmpty(deniedEnvironmentsByScope)) {
        throw new NGAccessDeniedException(
            buildPermissionDeniedMessage(deniedEnvironmentsByScope), WingsException.USER, List.of());
      }
    }
  }

  private String buildPermissionDeniedMessage(Map<ResourceScope, List<AccessControlDTO>> deniedEnvironmentsByScope) {
    Set<String> accountScopedEnvs = new HashSet<>();
    Set<String> orgScopedEnvs = new HashSet<>();
    Set<String> projectScopedEnvs = new HashSet<>();
    deniedEnvironmentsByScope.keySet().forEach(scope -> {
      Set<String> envIdentifiers = deniedEnvironmentsByScope.get(scope)
                                       .stream()
                                       .map(AccessControlDTO::getResourceIdentifier)
                                       .collect(Collectors.toSet());
      if (isNotEmpty(scope.getProjectIdentifier())) {
        projectScopedEnvs.addAll(envIdentifiers);
      } else if (isNotEmpty(scope.getOrgIdentifier())) {
        orgScopedEnvs.addAll(envIdentifiers);
      } else {
        accountScopedEnvs.addAll(envIdentifiers);
      }
    });

    String accessDeniedMessage = "Missing Access Permission for Following Environments: \n";
    if (isNotEmpty(accountScopedEnvs)) {
      accessDeniedMessage =
          accessDeniedMessage + "[Scope = Account]: " + StringUtils.joinWith(",", accountScopedEnvs) + "\n";
    }

    if (isNotEmpty(orgScopedEnvs)) {
      accessDeniedMessage =
          accessDeniedMessage + "[Scope = Organisation]: " + StringUtils.joinWith(",", orgScopedEnvs) + "\n";
    }

    if (isNotEmpty(projectScopedEnvs)) {
      accessDeniedMessage =
          accessDeniedMessage + "[Scope = Project]: " + StringUtils.joinWith(",", projectScopedEnvs) + "\n";
    }
    return accessDeniedMessage;
  }

  public Scope getScopeForRef(String ref) {
    String[] refSplit = StringUtils.split(ref, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    if (refSplit == null || refSplit.length == 1) {
      return Scope.PROJECT;
    } else {
      return Scope.fromString(refSplit[0]);
    }
  }

  public static String getEnvironmentRef(String environmentRef, Scope envGroupScope) {
    // project level env groups not modified
    if (envGroupScope == null || Scope.PROJECT.equals(envGroupScope) || Scope.UNKNOWN.equals(envGroupScope)) {
      return environmentRef;
    }

    String[] envRefSplit = StringUtils.split(environmentRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    if (envRefSplit == null || envRefSplit.length == 1) {
      return envGroupScope.getYamlRepresentation() + "." + environmentRef;
    } else {
      return environmentRef;
    }
  }
}