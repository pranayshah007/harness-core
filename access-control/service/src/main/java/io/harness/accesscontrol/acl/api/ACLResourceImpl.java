/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.api;

import static io.harness.accesscontrol.acl.api.AccessControlResourceUtils.checkPreconditions;
import static io.harness.accesscontrol.acl.api.AccessControlResourceUtils.checkResourcePreconditions;
import static io.harness.accesscontrol.acl.api.AccessControlResourceUtils.getAccessControlDTO;
import static io.harness.accesscontrol.acl.api.AccessControlResourceUtils.serviceContextAndOnlyServicePrincipalInBody;
import static io.harness.accesscontrol.principals.PrincipalType.API_KEY;
import static io.harness.accesscontrol.principals.PrincipalType.SERVICE;
import static io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.accesscontrol.acl.ACLService;
import io.harness.accesscontrol.acl.PermissionCheck;
import io.harness.accesscontrol.acl.PermissionCheckResult;
import io.harness.accesscontrol.acl.ResourceAttributeProvider;
import io.harness.accesscontrol.preference.services.AccessControlPreferenceService;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.privileged.PrivilegedAccessCheck;
import io.harness.accesscontrol.roleassignments.privileged.PrivilegedAccessResult;
import io.harness.accesscontrol.roleassignments.privileged.PrivilegedRoleAssignmentService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@ValidateOnExecution
@Singleton
@Slf4j
@NextGenManagerAuth
@OwnedBy(HarnessTeam.PL)
public class ACLResourceImpl implements ACLResource {
  private final ACLService aclService;
  private final AccessControlPreferenceService accessControlPreferenceService;
  private final PrivilegedRoleAssignmentService privilegedRoleAssignmentService;
  private final ResourceAttributeProvider resourceAttributeProvider;

  private Optional<String> getAccountIdentifier(List<PermissionCheckDTO> permissionCheckDTOList) {
    if (permissionCheckDTOList.isEmpty()) {
      return Optional.empty();
    }

    PermissionCheckDTO permissionCheckDTO = permissionCheckDTOList.get(0);
    if (permissionCheckDTO.getResourceScope() == null
        || StringUtils.isEmpty(permissionCheckDTO.getResourceScope().getAccountIdentifier())) {
      return Optional.of(permissionCheckDTO.getResourceIdentifier());
    } else {
      return Optional.of(permissionCheckDTO.getResourceScope().getAccountIdentifier());
    }
  }

  private boolean notPresent(Principal principal) {
    return !Optional.ofNullable(principal).map(Principal::getPrincipalIdentifier).filter(x -> !x.isEmpty()).isPresent();
  }

  @Override
  public ResponseDTO<AccessCheckResponseDTO> get(AccessCheckRequestDTO dto) {
    io.harness.security.dto.Principal contextPrincipal = SecurityContextBuilder.getPrincipal();
    List<PermissionCheckDTO> permissionChecksDTOs = dto.getPermissions();
    Principal principalToCheckPermissionsFor = dto.getPrincipal();
    if (isEmpty(permissionChecksDTOs)) {
      return ResponseDTO.newResponse(AccessCheckResponseDTO.builder()
                                         .principal(principalToCheckPermissionsFor)
                                         .accessControlList(new ArrayList<>())
                                         .build());
    }

    boolean preconditionsValid = checkPreconditions(contextPrincipal, principalToCheckPermissionsFor);
    boolean resourcePreconditionsValid = checkResourcePreconditions(permissionChecksDTOs);

    if (serviceContextAndOnlyServicePrincipalInBody(contextPrincipal, principalToCheckPermissionsFor)) {
      return ResponseDTO.newResponse(
          AccessCheckResponseDTO.builder()
              .principal(Principal.of(SERVICE, contextPrincipal.getName()))
              .accessControlList(permissionChecksDTOs.stream()
                                     .map(permissionCheckDTO
                                         -> AccessControlDTO.builder()
                                                .permitted(true)
                                                .permission(permissionCheckDTO.getPermission())
                                                .resourceScope(permissionCheckDTO.getResourceScope())
                                                .resourceIdentifier(permissionCheckDTO.getResourceIdentifier())
                                                .resourceType(permissionCheckDTO.getResourceType())
                                                .resourceAttributes(permissionCheckDTO.getResourceAttributes())
                                                .build())
                                     .collect(Collectors.toList()))
              .build());
    }

    if (!preconditionsValid) {
      throw new InvalidRequestException(
          "Missing principal in context or User doesn't have permission to check access for a different principal",
          WingsException.USER);
    }

    if (!resourcePreconditionsValid) {
      throw new InvalidRequestException(
          "Cannot pass both resource attributes and resource identifier in permission check", WingsException.USER);
    }

    Optional<String> accountIdentifierOptional = getAccountIdentifier(permissionChecksDTOs);
    if (accountIdentifierOptional.isPresent()
        && !accessControlPreferenceService.isAccessControlEnabled(accountIdentifierOptional.get())) {
      return ResponseDTO.newResponse(
          AccessCheckResponseDTO.builder()
              .accessControlList(permissionChecksDTOs.stream()
                                     .map(permissionCheckDTO -> getAccessControlDTO(permissionCheckDTO, true))
                                     .collect(Collectors.toList()))
              .principal(principalToCheckPermissionsFor)
              .build());
    }

    if (notPresent(principalToCheckPermissionsFor)) {
      principalToCheckPermissionsFor =
          Principal.of(fromSecurityPrincipalType(contextPrincipal.getType()), contextPrincipal.getName());
    }
    List<PermissionCheck> permissionChecks =
        permissionChecksDTOs.stream().map(PermissionCheckDTOMapper::fromDTO).collect(Collectors.toList());
    List<PermissionCheckResult> permissionCheckResults =
        aclService.checkAccess(principalToCheckPermissionsFor, permissionChecks, resourceAttributeProvider);

    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .principal(principalToCheckPermissionsFor)
            .accessControlList(
                permissionCheckResults.stream().map(PermissionCheckDTOMapper::toDTO).collect(Collectors.toList()))
            .build();

    if (accountIdentifierOptional.isPresent()) {
      io.harness.accesscontrol.principals.Principal principal =
          io.harness.accesscontrol.principals.Principal.builder()
              .principalIdentifier(principalToCheckPermissionsFor.getPrincipalIdentifier())
              .principalType(principalToCheckPermissionsFor.getPrincipalType())
              .build();
      PrivilegedAccessCheck privilegedAccessCheck = PrivilegedAccessCheck.builder()
                                                        .principal(principal)
                                                        .accountIdentifier(accountIdentifierOptional.get())
                                                        .permissionChecks(permissionChecks)
                                                        .build();
      PrivilegedAccessResult privilegedAccessResult =
          privilegedRoleAssignmentService.checkAccess(privilegedAccessCheck);
      Iterator<AccessControlDTO> iterator = accessCheckResponseDTO.getAccessControlList().iterator();
      int index = 0;
      while (iterator.hasNext()) {
        AccessControlDTO rbacAccess = iterator.next();
        PermissionCheckResult privilegedAccess = privilegedAccessResult.getPermissionCheckResults().get(index);
        rbacAccess.setPermitted(privilegedAccess.isPermitted() || rbacAccess.isPermitted());
        index++;
      }
    }
    return ResponseDTO.newResponse(accessCheckResponseDTO);
  }

  private static PrincipalType fromSecurityPrincipalType(io.harness.security.dto.PrincipalType principalType) {
    switch (principalType) {
      case SERVICE:
        return SERVICE;
      case USER:
        return PrincipalType.USER;
      case API_KEY:
        return API_KEY;
      case SERVICE_ACCOUNT:
        return SERVICE_ACCOUNT;
      default:
        return null;
    }
  }
}
