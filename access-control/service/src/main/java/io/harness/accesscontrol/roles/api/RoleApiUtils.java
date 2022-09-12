/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles.api;

import static io.harness.NGCommonEntityConstants.NEXT_REL;
import static io.harness.NGCommonEntityConstants.PAGE;
import static io.harness.NGCommonEntityConstants.PAGE_SIZE;
import static io.harness.NGCommonEntityConstants.PREVIOUS_REL;
import static io.harness.NGCommonEntityConstants.SELF_REL;
import static io.harness.annotations.dev.HarnessTeam.PL;

import static javax.ws.rs.core.UriBuilder.fromPath;

import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.spec.server.accesscontrol.model.CreateRoleRequest;
import io.harness.spec.server.accesscontrol.model.RoleScope;
import io.harness.spec.server.accesscontrol.model.RolesResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response.ResponseBuilder;

@OwnedBy(PL)
public class RoleApiUtils {
  public static RoleDTO getRoleAccDTO(CreateRoleRequest roleRequest) {
    return RoleDTO.builder()
        .identifier(roleRequest.getSlug())
        .name(roleRequest.getName())
        .permissions(new HashSet<>(roleRequest.getPermissions()))
        .allowedScopeLevels(Collections.singleton("account"))
        .description(roleRequest.getDescription())
        .tags(roleRequest.getTags())
        .build();
  }

  public static RoleDTO getRoleOrgDTO(CreateRoleRequest roleRequest) {
    return RoleDTO.builder()
        .identifier(roleRequest.getSlug())
        .name(roleRequest.getName())
        .permissions(new HashSet<>(roleRequest.getPermissions()))
        .allowedScopeLevels(Collections.singleton("organization"))
        .description(roleRequest.getDescription())
        .tags(roleRequest.getTags())
        .build();
  }

  public static RoleDTO getRoleProjectDTO(CreateRoleRequest roleRequest) {
    return RoleDTO.builder()
        .identifier(roleRequest.getSlug())
        .name(roleRequest.getName())
        .permissions(new HashSet<>(roleRequest.getPermissions()))
        .allowedScopeLevels(Collections.singleton("project"))
        .description(roleRequest.getDescription())
        .tags(roleRequest.getTags())
        .build();
  }
  public static RolesResponse getRolesResponse(RoleResponseDTO responseDTO) {
    if (responseDTO.getRole() == null) {
      return null;
    }
    RolesResponse rolesResponse = new RolesResponse();
    rolesResponse.setSlug(responseDTO.getRole().getIdentifier());
    rolesResponse.setName(responseDTO.getRole().getName());
    Set<String> permissions = responseDTO.getRole().getPermissions();
    if (permissions != null) {
      rolesResponse.setPermissions(new ArrayList<>(permissions));
    }
    Set<String> allowedScopeLevels = responseDTO.getRole().getAllowedScopeLevels();
    if (allowedScopeLevels != null) {
      rolesResponse.setAllowedScopeLevels(new ArrayList<>(
          allowedScopeLevels.stream().map(RoleApiUtils::getAllowedScopeEnum).collect(Collectors.toList())));
    }
    rolesResponse.setDescription(responseDTO.getRole().getDescription());
    rolesResponse.setTags(responseDTO.getRole().getTags());
    rolesResponse.setScope(getRoleScope(responseDTO.getScope()));
    rolesResponse.setHarnessManaged(responseDTO.isHarnessManaged());
    rolesResponse.setCreated(responseDTO.getCreatedAt());
    rolesResponse.setUpdated(responseDTO.getLastModifiedAt());
    return rolesResponse;
  }

  public static RolesResponse.AllowedScopeLevelsEnum getAllowedScopeEnum(String scopeLevels) {
    switch (scopeLevels) {
      case "account":
        return RolesResponse.AllowedScopeLevelsEnum.ACCOUNT;
      case "organization":
        return RolesResponse.AllowedScopeLevelsEnum.ORGANIZATION;
      case "project":
        return RolesResponse.AllowedScopeLevelsEnum.PROJECT;
      default:
        return null;
    }
  }

  public static RoleScope getRoleScope(ScopeDTO scopeDTO) {
    if (scopeDTO == null) {
      return null;
    }
    RoleScope roleScope = new RoleScope();
    roleScope.setAccount(scopeDTO.getAccountIdentifier());
    roleScope.setOrg(scopeDTO.getOrgIdentifier());
    roleScope.setProject(scopeDTO.getProjectIdentifier());
    return roleScope;
  }

  public static PageRequest getPageRequest(Integer page, Integer limit) {
    return PageRequest.builder().pageIndex(page).pageSize(limit).build();
  }

  public static ResponseBuilder addLinksHeader(
      ResponseBuilder responseBuilder, String path, int currentResultCount, int page, int limit) {
    ArrayList<Link> links = new ArrayList<>();
    links.add(
        Link.fromUri(fromPath(path).queryParam(PAGE, page).queryParam(PAGE_SIZE, limit).build()).rel(SELF_REL).build());
    if (page >= 1) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page - 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(PREVIOUS_REL)
                    .build());
    }
    if (limit == currentResultCount) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page + 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(NEXT_REL)
                    .build());
    }
    return responseBuilder.links(links.toArray(new Link[links.size()]));
  }
}
