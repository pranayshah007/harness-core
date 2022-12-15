/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.exception.WingsException.USER;
import static io.harness.secrets.SecretPermissions.SECRET_DELETE_PERMISSION;
import static io.harness.secrets.SecretPermissions.SECRET_EDIT_PERMISSION;
import static io.harness.secrets.SecretPermissions.SECRET_RESOURCE_TYPE;
import static io.harness.secrets.SecretPermissions.SECRET_VIEW_PERMISSION;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.util.Objects.nonNull;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.api.impl.SecretPermissionValidator;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.secretmanagerclient.SecretType;
import io.harness.security.SecurityContextBuilder;
import io.harness.spec.server.ng.v1.OrgSecretApi;
import io.harness.spec.server.ng.v1.model.SecretRequest;
import io.harness.spec.server.ng.v1.model.SecretResponse;

import com.google.inject.Inject;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class OrgSecretApiImpl implements OrgSecretApi {
  private final SecretCrudService ngSecretService;
  private final SecretPermissionValidator secretPermissionValidator;
  private final SecretApiUtils secretApiUtils;

  @Override
  public Response createOrgScopedSecret(
      SecretRequest secretRequest, String org, String account, Boolean privateSecret) {
    if (!Objects.equals(org, secretRequest.getSecret().getOrg()) || nonNull(secretRequest.getSecret().getProject())) {
      throw new InvalidRequestException(
          "Org scoped request is having different org in payload and param OR non null project", USER);
    }
    return createSecret(account, secretRequest, privateSecret);
  }

  @Override
  public Response createOrgScopedSecret(
      SecretRequest secretRequest, InputStream fileInputStream, String org, String account, Boolean privateSecret) {
    if (!Objects.equals(org, secretRequest.getSecret().getOrg()) || nonNull(secretRequest.getSecret().getProject())) {
      throw new InvalidRequestException(
          "Org scoped request is having different org in payload and param OR non null project", USER);
    }
    secretPermissionValidator.checkForAccessOrThrow(
        ResourceScope.of(account, secretRequest.getSecret().getOrg(), secretRequest.getSecret().getProject()),
        Resource.of(SECRET_RESOURCE_TYPE, null), SECRET_EDIT_PERMISSION,
        privateSecret ? SecurityContextBuilder.getPrincipal() : null);
    SecretDTOV2 secretDto = secretApiUtils.toSecretDto(secretRequest.getSecret());

    if (privateSecret) {
      secretDto.setOwner(SecurityContextBuilder.getPrincipal());
    }

    SecretResponseWrapper secretResponseWrapper = ngSecretService.createFile(account, secretDto, fileInputStream);

    return Response.status(Response.Status.CREATED)
        .entity(secretApiUtils.toSecretResponse(secretResponseWrapper))
        .build();
  }

  @Override
  public Response deleteOrgScopedSecret(String org, String secret, String account) {
    return deleteSecret(org, secret, account);
  }

  @Override
  public Response getOrgScopedSecret(String org, String secret, String account) {
    return getSecret(org, secret, account);
  }

  @Override
  public Response getOrgScopedSecrets(String org, List<String> secret, List<String> type, Boolean recursive,
      String searchTerm, Integer page, Integer limit, String account) {
    return getSecrets(account, org, secret, type, recursive, searchTerm, page, limit);
  }

  @Override
  public Response updateOrgScopedSecret(SecretRequest secretRequest, String org, String secret, String account) {
    return updateSecret(secretRequest, org, secret, account);
  }

  @Override
  public Response updateOrgScopedSecret(
      SecretRequest secretRequest, InputStream fileInputStream, String org, String secret, String account) {
    if (!Objects.equals(org, secretRequest.getSecret().getOrg()) || nonNull(secretRequest.getSecret().getProject())) {
      throw new InvalidRequestException(
          "Org scoped request is having different org in payload and param OR non null project", USER);
    }
    SecretResponseWrapper secretResponseWrapper = ngSecretService.get(account, org, null, secret).orElse(null);
    secretPermissionValidator.checkForAccessOrThrow(ResourceScope.of(account, org, null),
        Resource.of(SECRET_RESOURCE_TYPE, secret), SECRET_EDIT_PERMISSION,
        secretResponseWrapper != null ? secretResponseWrapper.getSecret().getOwner() : null);
    SecretDTOV2 secretDto = secretApiUtils.toSecretDto(secretRequest.getSecret());

    return Response.ok()
        .entity(ngSecretService.updateFile(account, org, null, secret, secretDto, fileInputStream))
        .build();
  }

  private Response updateSecret(SecretRequest secretRequest, String org, String secret, String account) {
    if (!Objects.equals(org, secretRequest.getSecret().getOrg()) || nonNull(secretRequest.getSecret().getProject())) {
      throw new InvalidRequestException(
          "Org scoped request is having different org in payload and param OR non null project", USER);
    }
    SecretResponseWrapper secretResponseWrapper = ngSecretService.get(account, org, null, secret).orElse(null);
    secretPermissionValidator.checkForAccessOrThrow(ResourceScope.of(account, org, null),
        Resource.of(SECRET_RESOURCE_TYPE, secret), SECRET_EDIT_PERMISSION,
        secretResponseWrapper != null ? secretResponseWrapper.getSecret().getOwner() : null);

    SecretResponseWrapper updatedSecret =
        ngSecretService.update(account, org, null, secret, secretApiUtils.toSecretDto(secretRequest.getSecret()));
    return Response.ok().entity(secretApiUtils.toSecretResponse(updatedSecret)).build();
  }

  private Response deleteSecret(String org, String secret, String account) {
    SecretResponseWrapper secretResponseWrapper = ngSecretService.get(account, org, null, secret).orElse(null);
    secretPermissionValidator.checkForAccessOrThrow(ResourceScope.of(account, org, null),
        Resource.of(SECRET_RESOURCE_TYPE, secret), SECRET_DELETE_PERMISSION,
        secretResponseWrapper != null ? secretResponseWrapper.getSecret().getOwner() : null);
    boolean deleted = ngSecretService.delete(account, org, null, secret, false);
    if (deleted) {
      return Response.ok().entity(secretApiUtils.toSecretResponse(secretResponseWrapper)).build();
    }
    throw new NotFoundException(
        format("Secret with identifier [%s] in org [%s] and project [%s] not found", secret, org, null));
  }

  private Response getSecret(String org, String secret, String account) {
    SecretResponseWrapper secretResponseWrapper = ngSecretService.get(account, org, null, secret).orElse(null);
    secretPermissionValidator.checkForAccessOrThrow(ResourceScope.of(account, org, null),
        Resource.of(SECRET_RESOURCE_TYPE, secret), SECRET_VIEW_PERMISSION,
        secretResponseWrapper != null ? secretResponseWrapper.getSecret().getOwner() : null);

    if (nonNull(secretResponseWrapper)) {
      return Response.ok().entity(secretApiUtils.toSecretResponse(secretResponseWrapper)).build();
    }
    throw new NotFoundException(
        format("Secret with identifier [%s] in org [%s] and project [%s] not found", secret, org, null));
  }

  private Response getSecrets(String account, String org, List<String> secret, List<String> type, Boolean recursive,
      String searchTerm, Integer page, Integer limit) {
    List<SecretType> secretTypes = secretApiUtils.toSecretTypes(type);

    List<SecretResponseWrapper> content = getNGPageResponse(
        ngSecretService.list(account, org, null, secret, secretTypes, recursive, searchTerm, page, limit, null, false))
                                              .getContent();

    List<SecretResponse> secretResponse =
        content.stream().map(secretApiUtils::toSecretResponse).collect(Collectors.toList());

    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks = secretApiUtils.addLinksHeader(
        responseBuilder, format("/v1/orgs/%s/secrets", org), secretResponse.size(), page, limit);

    return responseBuilderWithLinks.entity(secretResponse).build();
  }

  private Response createSecret(String account, SecretRequest secretRequest, Boolean privateSecret) {
    secretPermissionValidator.checkForAccessOrThrow(
        ResourceScope.of(account, secretRequest.getSecret().getOrg(), secretRequest.getSecret().getProject()),
        Resource.of(SECRET_RESOURCE_TYPE, null), SECRET_EDIT_PERMISSION,
        privateSecret ? SecurityContextBuilder.getPrincipal() : null);

    SecretDTOV2 secretDto = secretApiUtils.toSecretDto(secretRequest.getSecret());

    if (TRUE.equals(privateSecret)) {
      secretDto.setOwner(SecurityContextBuilder.getPrincipal());
    }
    SecretResponseWrapper entity = ngSecretService.create(account, secretDto);

    return Response.status(Response.Status.CREATED).entity(secretApiUtils.toSecretResponse(entity)).build();
  }
}
