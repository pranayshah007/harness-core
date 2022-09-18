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
import io.harness.spec.server.ng.ProjectSecretApi;
import io.harness.spec.server.ng.model.SecretRequest;
import io.harness.spec.server.ng.model.SecretResponse;
import io.harness.spec.server.ng.model.ValidateSecretSlugResponse;

import com.google.inject.Inject;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class ProjectSecretApiImpl implements ProjectSecretApi {
  private final SecretCrudService ngSecretService;
  private final SecretPermissionValidator secretPermissionValidator;
  private final SecretApiUtils secretApiUtils;

  @Override
  public Response createProjectScopedSecret(
      SecretRequest secretRequest, String org, String project, String account, Boolean privateSecret) {
    if (!Objects.equals(org, secretRequest.getSecret().getOrg())
        || !Objects.equals(project, secretRequest.getSecret().getProject())) {
      throw new InvalidRequestException(
          "Invalid request, org and project scope in payload and params do not match.", USER);
    }
    return createSecret(account, secretRequest, privateSecret);
  }

  @Override
  public Response createProjectScopedSecret(SecretRequest secretRequest, InputStream fileInputStream, String org,
      String project, String account, Boolean privateSecret) {
    if (!Objects.equals(org, secretRequest.getSecret().getOrg())
        || !Objects.equals(project, secretRequest.getSecret().getProject())) {
      throw new InvalidRequestException(
          "Invalid request, org and project scope in payload and params do not match.", USER);
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

    return Response.ok().entity(secretApiUtils.toSecretResponse(secretResponseWrapper)).build();
  }

  @Override
  public Response deleteProjectScopedSecret(String org, String project, String secret, String account) {
    return deleteSecret(org, project, secret, account);
  }

  @Override
  public Response getProjectScopedSecret(String org, String project, String secret, String account) {
    return getSecret(org, project, secret, account);
  }

  @Override
  public Response getProjectScopedSecrets(String org, String project, String account, List<String> secret,
      List<String> type, Boolean recursive, String searchTerm, Integer page, Integer limit) {
    return getSecrets(account, org, project, secret, type, recursive, searchTerm, page, limit);
  }

  @Override
  public Response updateProjectScopedSecret(
      SecretRequest secretRequest, String org, String project, String secret, String account) {
    return updateSecret(secretRequest, org, project, secret, account);
  }

  @Override
  public Response updateProjectScopedSecret(SecretRequest secretRequest, InputStream fileInputStream, String org,
      String project, String secret, String account) {
    if (!Objects.equals(org, secretRequest.getSecret().getOrg())
        || !Objects.equals(project, secretRequest.getSecret().getProject())) {
      throw new InvalidRequestException(
          "Invalid request, org and project scope in payload and params do not match.", USER);
    }
    SecretResponseWrapper secretResponseWrapper = ngSecretService.get(account, org, project, secret).orElse(null);
    secretPermissionValidator.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(SECRET_RESOURCE_TYPE, secret), SECRET_EDIT_PERMISSION,
        secretResponseWrapper != null ? secretResponseWrapper.getSecret().getOwner() : null);
    SecretDTOV2 secretDto = secretApiUtils.toSecretDto(secretRequest.getSecret());

    return Response.ok()
        .entity(ngSecretService.updateFile(account, org, project, secret, secretDto, fileInputStream))
        .build();
  }

  @Override
  public Response validateUniqueProjectScopedSecretSlug(String org, String project, String secret, String account) {
    return validateSecretSlug(secret, account, org, project);
  }

  private Response validateSecretSlug(String secret, String account, String org, String project) {
    boolean isUnique = ngSecretService.validateTheIdentifierIsUnique(account, org, project, secret);
    return Response.ok().entity(new ValidateSecretSlugResponse().valid(isUnique)).build();
  }

  private Response updateSecret(
      SecretRequest secretRequest, String org, String project, String secret, String account) {
    if (!Objects.equals(org, secretRequest.getSecret().getOrg())
        || !Objects.equals(project, secretRequest.getSecret().getProject())) {
      throw new InvalidRequestException(
          "Invalid request, org and project scope in payload and params do not match.", USER);
    }
    SecretResponseWrapper secretResponseWrapper = ngSecretService.get(account, org, project, secret).orElse(null);
    secretPermissionValidator.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(SECRET_RESOURCE_TYPE, secret), SECRET_EDIT_PERMISSION,
        secretResponseWrapper != null ? secretResponseWrapper.getSecret().getOwner() : null);

    SecretResponseWrapper updatedSecret =
        ngSecretService.update(account, org, project, secret, secretApiUtils.toSecretDto(secretRequest.getSecret()));
    return Response.ok().entity(secretApiUtils.toSecretResponse(updatedSecret)).build();
  }

  private Response deleteSecret(String org, String project, String secret, String account) {
    SecretResponseWrapper secretResponseWrapper = ngSecretService.get(account, org, project, secret).orElse(null);
    secretPermissionValidator.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(SECRET_RESOURCE_TYPE, secret), SECRET_DELETE_PERMISSION,
        secretResponseWrapper != null ? secretResponseWrapper.getSecret().getOwner() : null);
    boolean deleted = ngSecretService.delete(account, org, project, secret);
    if (deleted) {
      return Response.ok().entity(secretApiUtils.toSecretResponse(secretResponseWrapper)).build();
    }
    throw new NotFoundException(
        format("Secret with identifier [%s] in org [%s] and project [%s] not found", secret, org, project));
  }

  private Response getSecret(String org, String project, String secret, String account) {
    SecretResponseWrapper secretResponseWrapper = ngSecretService.get(account, org, project, secret).orElse(null);
    secretPermissionValidator.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(SECRET_RESOURCE_TYPE, secret), SECRET_VIEW_PERMISSION,
        secretResponseWrapper != null ? secretResponseWrapper.getSecret().getOwner() : null);

    if (nonNull(secretResponseWrapper)) {
      return Response.ok().entity(secretApiUtils.toSecretResponse(secretResponseWrapper)).build();
    }
    throw new NotFoundException(
        format("Secret with identifier [%s] in org [%s] and project [%s] not found", secret, org, project));
  }

  private Response getSecrets(String account, String org, String project, List<String> secret, List<String> type,
      Boolean recursive, String searchTerm, Integer page, Integer limit) {
    List<SecretType> secretTypes = secretApiUtils.toSecretTypes(type);

    List<SecretResponseWrapper> content = getNGPageResponse(
        ngSecretService.list(account, org, project, secret, secretTypes, recursive, searchTerm, page, limit, null))
                                              .getContent();

    List<SecretResponse> secretResponse =
        content.stream().map(secretApiUtils::toSecretResponse).collect(Collectors.toList());
    return Response.ok().entity(secretResponse).build();
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

    return Response.ok().entity(secretApiUtils.toSecretResponse(entity)).build();
  }
}
