/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.FORCE_DELETE_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.USER;
import static io.harness.secrets.SecretPermissions.SECRET_ACCESS_PERMISSION;
import static io.harness.secrets.SecretPermissions.SECRET_DELETE_PERMISSION;
import static io.harness.secrets.SecretPermissions.SECRET_EDIT_PERMISSION;
import static io.harness.secrets.SecretPermissions.SECRET_RESOURCE_TYPE;
import static io.harness.secrets.SecretPermissions.SECRET_VIEW_PERMISSION;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DecryptedSecretValue;
import io.harness.connector.ConnectorCategory;
import io.harness.data.validator.EntityIdentifier;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.DecryptableEntityWithEncryptionConsumers;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.api.impl.SecretPermissionValidator;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.SecretResourceFilterDTO;
import io.harness.ng.core.dto.secrets.SecretRequestWrapper;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.secretmanagerclient.SecretType;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;

import software.wings.service.impl.security.NGEncryptorService;

import com.google.inject.Inject;
import io.dropwizard.jersey.validation.JerseyViolationException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Valid;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.multipart.FormDataParam;
import retrofit2.http.Body;

@OwnedBy(PL)
@Path("/v2/secrets")
@Api("/v2/secrets")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@Tag(name = "Secrets", description = "This contains APIs related to Secrets as defined in Harness")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class NGSecretResourceV2 {
  private static final String INCLUDE_SECRETS_FROM_EVERY_SUB_SCOPE = "includeSecretsFromEverySubScope";
  private final SecretCrudService ngSecretService;
  private final Validator validator;
  private final NGEncryptedDataService encryptedDataService;
  private final SecretPermissionValidator secretPermissionValidator;
  private final NGEncryptorService ngEncryptorService;

  @GET
  @Path("/validateUniqueIdentifier/{identifier}")
  @ApiOperation(value = "Validate Secret Identifier is unique", nickname = "validateSecretIdentifierIsUnique")
  @Operation(operationId = "validateSecretIdentifierIsUnique",
      summary = "Checks whether the identifier is unique or not",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "It returns true if the Identifier is unique and false if the Identifier is not unique.")
      })
  public ResponseDTO<Boolean>
  validateTheIdentifierIsUnique(@Parameter(description = "Secret Identifier") @NotNull @PathParam(
                                    NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    return ResponseDTO.newResponse(
        ngSecretService.validateTheIdentifierIsUnique(accountIdentifier, orgIdentifier, projectIdentifier, identifier));
  }

  @POST
  @Consumes({"application/json"})
  @ApiOperation(value = "Create a secret", nickname = "postSecret")
  @Operation(operationId = "postSecret", summary = "Creates a Secret at given Scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Secret details")
      })
  public ResponseDTO<SecretResponseWrapper>
  create(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(
          description = "This is a boolean value to specify if the Secret is Private. The default value is False.")
      @QueryParam("privateSecret") @DefaultValue("false") boolean privateSecret,
      @RequestBody(required = true,
          description = "Details required to create the Secret") @Valid @NotNull SecretRequestWrapper dto) {
    if (!Objects.equals(orgIdentifier, dto.getSecret().getOrgIdentifier())
        || !Objects.equals(projectIdentifier, dto.getSecret().getProjectIdentifier())) {
      throw new InvalidRequestException("Invalid request, scope in payload and params do not match.", USER);
    }

    secretPermissionValidator.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier), Resource.of(SECRET_RESOURCE_TYPE, null),
        SECRET_EDIT_PERMISSION, privateSecret ? SecurityContextBuilder.getPrincipal() : null);

    ngSecretService.validateSshWinRmSecretRef(accountIdentifier, orgIdentifier, projectIdentifier, dto.getSecret());
    ngSecretService.validateSecretDtoSpec(dto.getSecret());

    if (privateSecret) {
      dto.getSecret().setOwner(SecurityContextBuilder.getPrincipal());
    }

    return ResponseDTO.newResponse(ngSecretService.create(accountIdentifier, dto.getSecret()));
  }

  @POST
  @Path("/validate")
  @Consumes({"application/json", "application/yaml"})
  @ApiOperation(value = "Validate a secret", nickname = "validateSecret")
  @Operation(operationId = "validateSecret", summary = "Validates Secret with the provided ID and Scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns validation response")
      })
  public ResponseDTO<SecretValidationResultDTO>
  validateSecret(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                     NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Secret ID") @QueryParam(NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @RequestBody(
          required = true, description = "Details of the Secret type") @Valid SecretValidationMetaData metadata) {
    SecretResponseWrapper secret =
        ngSecretService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier).orElse(null);

    secretPermissionValidator.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(SECRET_RESOURCE_TYPE, identifier), SECRET_ACCESS_PERMISSION,
        secret != null ? secret.getSecret().getOwner() : null);

    return ResponseDTO.newResponse(
        ngSecretService.validateSecret(accountIdentifier, orgIdentifier, projectIdentifier, identifier, metadata));
  }

  @POST
  @Path("/yaml")
  @Consumes({"application/yaml"})
  @ApiOperation(value = "Create a secret via yaml", nickname = "postSecretViaYaml")
  @Operation(operationId = "postSecretViaYaml", summary = "Creates a secret via YAML",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Secret details")
      })
  public ResponseDTO<SecretResponseWrapper>
  createViaYaml(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                    NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(
          description = "This is a boolean value to specify if the Secret is Private. The default value is False.")
      @QueryParam("privateSecret") @DefaultValue("false") boolean privateSecret,
      @RequestBody(
          required = true, description = "Details required to create the Secret") @Valid SecretRequestWrapper dto) {
    if (!Objects.equals(orgIdentifier, dto.getSecret().getOrgIdentifier())
        || !Objects.equals(projectIdentifier, dto.getSecret().getProjectIdentifier())) {
      throw new InvalidRequestException("Invalid request, scope in payload and params do not match.", USER);
    }
    secretPermissionValidator.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier), Resource.of(SECRET_RESOURCE_TYPE, null),
        SECRET_EDIT_PERMISSION, privateSecret ? SecurityContextBuilder.getPrincipal() : null);

    ngSecretService.validateSshWinRmSecretRef(accountIdentifier, orgIdentifier, projectIdentifier, dto.getSecret());
    ngSecretService.validateSecretDtoSpec(dto.getSecret());
    if (privateSecret) {
      dto.getSecret().setOwner(SecurityContextBuilder.getPrincipal());
    }

    return ResponseDTO.newResponse(ngSecretService.createViaYaml(accountIdentifier, dto.getSecret()));
  }

  @GET
  @ApiOperation(value = "Get secrets", nickname = "listSecretsV2")
  @Operation(operationId = "listSecretsV2",
      summary = "Fetches the list of Secrets corresponding to the request's filter criteria.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Secrets")
      })
  @Deprecated
  public ResponseDTO<PageResponse<SecretResponseWrapper>>
  list(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "This is the list of Secret IDs. Details specific to these IDs would be fetched.")
      @QueryParam(NGResourceFilterConstants.IDENTIFIERS) List<String> identifiers,
      @Parameter(description = "Type of Secret whether it is SecretFile, SecretText or SSH key") @QueryParam(
          "type") SecretType secretType,
      @Parameter(description = "Filter Secrets based on name, Identifier and tags by this search term") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "Add multiple secret types like SecretFile, SecretText or SSH key to criteria")
      @QueryParam("types") List<SecretType> secretTypes,
      @Parameter(
          description =
              "Source Category like CLOUD_PROVIDER, SECRET_MANAGER, CLOUD_COST, ARTIFACTORY, CODE_REPO, MONITORING or TICKETING")
      @QueryParam("source_category") ConnectorCategory sourceCategory,
      @Parameter(description = "Specify whether or not to include secrets from all the sub-scopes of the given Scope")
      @QueryParam(INCLUDE_SECRETS_FROM_EVERY_SUB_SCOPE) @DefaultValue("false") boolean includeSecretsFromEverySubScope,
      @Parameter(description = "Specify whether or not to include all the Secrets"
              + " accessible at the scope. For eg if set as true, at the Project scope we will get"
              + " org and account Secrets also in the response") @QueryParam("includeAllSecretsAccessibleAtScope")
      @DefaultValue("false") boolean includeAllSecretsAccessibleAtScope,
      @BeanParam PageRequest pageRequest) {
    if (secretType != null) {
      secretTypes.add(secretType);
    }
    return ResponseDTO.newResponse(getNGPageResponse(ngSecretService.list(accountIdentifier, orgIdentifier,
        projectIdentifier, identifiers, secretTypes, includeSecretsFromEverySubScope, searchTerm, sourceCategory,
        includeAllSecretsAccessibleAtScope, pageRequest)));
  }

  @POST
  @Path("/list")
  @ApiOperation(value = "List secrets", nickname = "listSecretsV3")
  @Operation(operationId = "listSecretsV3",
      summary = "Fetches the list of Secrets corresponding to the request's filter criteria.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Secrets")
      })
  public ResponseDTO<PageResponse<SecretResponseWrapper>>
  listSecrets(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                  NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body SecretResourceFilterDTO secretResourceFilterDTO, @BeanParam PageRequest pageRequest) {
    return ResponseDTO.newResponse(getNGPageResponse(ngSecretService.list(accountIdentifier, orgIdentifier,
        projectIdentifier, secretResourceFilterDTO.getIdentifiers(), secretResourceFilterDTO.getSecretTypes(),
        secretResourceFilterDTO.isIncludeSecretsFromEverySubScope(), secretResourceFilterDTO.getSearchTerm(),
        secretResourceFilterDTO.getSourceCategory(), secretResourceFilterDTO.isIncludeAllSecretsAccessibleAtScope(),
        pageRequest)));
  }

  @GET
  @Path("{identifier}")
  @ApiOperation(value = "Gets secret", nickname = "getSecretV2")
  @Operation(operationId = "getSecretV2", summary = "Get the Secret by ID and Scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Secret with the requested ID and Scope")
      })
  public ResponseDTO<SecretResponseWrapper>
  get(@Parameter(description = "Secret ID") @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @NotNull String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    Optional<SecretResponseWrapper> secret =
        ngSecretService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    if (secret.isPresent()) {
      secretPermissionValidator.checkForAccessOrThrow(
          ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
          Resource.of(SECRET_RESOURCE_TYPE, identifier), SECRET_VIEW_PERMISSION, secret.get().getSecret().getOwner());

      return ResponseDTO.newResponse(secret.get());
    } else {
      throw new NotFoundException(
          String.format("Secret with identifier [%s] is not found in the given scope", identifier));
    }
  }

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete secret", nickname = "deleteSecretV2")
  @Operation(operationId = "deleteSecretV2", summary = "Deletes Secret by ID and Scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "It returns true if the secret is successfully deleted and false if it is not deleted")
      })
  public ResponseDTO<Boolean>
  delete(@Parameter(description = "Secret ID") @PathParam(
             NGCommonEntityConstants.IDENTIFIER_KEY) @NotNull String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = FORCE_DELETE_MESSAGE) @QueryParam(NGCommonEntityConstants.FORCE_DELETE) @DefaultValue(
          "false") boolean forceDelete) {
    SecretResponseWrapper secret =
        ngSecretService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier).orElse(null);
    secretPermissionValidator.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(SECRET_RESOURCE_TYPE, identifier), SECRET_DELETE_PERMISSION,
        secret != null ? secret.getSecret().getOwner() : null);
    return ResponseDTO.newResponse(
        ngSecretService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, forceDelete));
  }

  @PUT
  @Path("{identifier}")
  @ApiOperation(value = "Update a secret", nickname = "putSecret")
  @Operation(operationId = "putSecret", summary = "Updates the Secret by ID and Scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Secret")
      })
  @Consumes({"application/json"})
  public ResponseDTO<SecretResponseWrapper>
  updateSecret(@Parameter(description = "Secret ID") @PathParam(
                   NGCommonEntityConstants.IDENTIFIER_KEY) @NotNull String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, @Valid SecretRequestWrapper dto) {
    SecretResponseWrapper secret =
        ngSecretService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier).orElse(null);
    secretPermissionValidator.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(SECRET_RESOURCE_TYPE, identifier), SECRET_EDIT_PERMISSION,
        secret != null ? secret.getSecret().getOwner() : null);

    ngSecretService.validateSshWinRmSecretRef(accountIdentifier, orgIdentifier, projectIdentifier, dto.getSecret());
    ngSecretService.validateSecretDtoSpec(dto.getSecret());

    return ResponseDTO.newResponse(
        ngSecretService.update(accountIdentifier, orgIdentifier, projectIdentifier, identifier, dto.getSecret()));
  }

  @PUT
  @Path("{identifier}/yaml")
  @Consumes({"application/yaml"})
  @ApiOperation(value = "Update a secret via yaml", nickname = "putSecretViaYaml")
  @Operation(operationId = "putSecretViaYaml", summary = "Updates the Secret by ID and Scope via YAML",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Secret details")
      })
  public ResponseDTO<SecretResponseWrapper>
  updateSecretViaYaml(@Parameter(description = "Secret ID") @PathParam(
                          NGCommonEntityConstants.IDENTIFIER_KEY) @NotNull String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(required = true, description = "Details of Secret to create") @Valid SecretRequestWrapper dto) {
    SecretResponseWrapper secret =
        ngSecretService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier).orElse(null);
    secretPermissionValidator.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(SECRET_RESOURCE_TYPE, identifier), SECRET_EDIT_PERMISSION,
        secret != null ? secret.getSecret().getOwner() : null);

    ngSecretService.validateSshWinRmSecretRef(accountIdentifier, orgIdentifier, projectIdentifier, dto.getSecret());
    ngSecretService.validateSecretDtoSpec(dto.getSecret());

    return ResponseDTO.newResponse(ngSecretService.updateViaYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, dto.getSecret()));
  }

  private void validateRequestPayload(SecretRequestWrapper dto) {
    Set<ConstraintViolation<SecretRequestWrapper>> violations = validator.validate(dto);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
  }

  @PUT
  @Path("files/{identifier}")
  @ApiOperation(value = "Update a secret file", nickname = "putSecretFileV2")
  @Operation(operationId = "putSecretFileV2", summary = "Updates the Secret file by ID and Scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Secret file details")
      })
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public ResponseDTO<SecretResponseWrapper>
  updateSecretFile(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                       NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Secret ID") @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @NotNull String identifier,
      @Parameter(description = "This is the encrypted Secret File that needs to be uploaded.") @FormDataParam(
          "file") InputStream uploadedInputStream,
      @Parameter(description = "Specification of Secret file") @FormDataParam("spec") String spec) {
    SecretRequestWrapper dto = JsonUtils.asObject(spec, SecretRequestWrapper.class);
    validateRequestPayload(dto);

    SecretResponseWrapper secret =
        ngSecretService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier).orElse(null);
    secretPermissionValidator.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(SECRET_RESOURCE_TYPE, identifier), SECRET_EDIT_PERMISSION,
        secret != null ? secret.getSecret().getOwner() : null);

    return ResponseDTO.newResponse(ngSecretService.updateFile(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, dto.getSecret(), uploadedInputStream));
  }

  @POST
  @Path("files")
  @ApiOperation(value = "Create a secret file", nickname = "postSecretFileV2")
  @Operation(operationId = "postSecretFileV2", summary = "Creates a Secret File",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created Secret file")
      })
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public ResponseDTO<SecretResponseWrapper>
  createSecretFile(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                       NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(
          description = "This is a boolean value to specify if the Secret is Private. The default value is False.")
      @QueryParam("privateSecret") @DefaultValue("false") boolean privateSecret,
      @Parameter(description = "This is the encrypted Secret File that needs to be uploaded.") @NotNull @FormDataParam(
          "file") InputStream uploadedInputStream,
      @Parameter(description = "Specification of Secret file") @FormDataParam("spec") String spec) {
    SecretRequestWrapper dto = JsonUtils.asObject(spec, SecretRequestWrapper.class);
    validateRequestPayload(dto);

    if (!Objects.equals(orgIdentifier, dto.getSecret().getOrgIdentifier())
        || !Objects.equals(projectIdentifier, dto.getSecret().getProjectIdentifier())) {
      throw new InvalidRequestException("Invalid request, scope in payload and params do not match.", USER);
    }

    secretPermissionValidator.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier), Resource.of(SECRET_RESOURCE_TYPE, null),
        SECRET_EDIT_PERMISSION, privateSecret ? SecurityContextBuilder.getPrincipal() : null);
    if (privateSecret) {
      dto.getSecret().setOwner(SecurityContextBuilder.getPrincipal());
    }

    return ResponseDTO.newResponse(ngSecretService.createFile(accountIdentifier, dto.getSecret(), uploadedInputStream));
  }

  @POST
  @Hidden
  @Path("filesMigration")
  @ApiOperation(value = "File type secrets migration", nickname = "migrateSecretFiles", hidden = true)
  @Operation(operationId = "migrateSecretFiles", summary = "migrate secret files",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created Secret file")
      })
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @InternalApi
  public ResponseDTO<SecretResponseWrapper>
  createSecretFile(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                       NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(
          description = "This is a boolean value to specify if the Secret is Private. The default value is False.")
      @QueryParam("privateSecret") @DefaultValue("false") boolean privateSecret,
      @Parameter(description = "encryptionKey of the file secret from cg") @QueryParam(
          "encryptionKey") @NotNull String encryptionKey,
      @Parameter(description = "encryptionValue of the file secret from cg") @QueryParam(
          "encryptedValue") @NotNull String encryptedValue,
      @Parameter(description = "Specification of Secret file") @FormDataParam("spec") String spec) {
    SecretRequestWrapper dto = JsonUtils.asObject(spec, SecretRequestWrapper.class);
    validateRequestPayload(dto);

    if (!Objects.equals(orgIdentifier, dto.getSecret().getOrgIdentifier())
        || !Objects.equals(projectIdentifier, dto.getSecret().getProjectIdentifier())) {
      throw new InvalidRequestException("Invalid request, scope in payload and params do not match.", USER);
    }

    secretPermissionValidator.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier), Resource.of(SECRET_RESOURCE_TYPE, null),
        SECRET_EDIT_PERMISSION, privateSecret ? SecurityContextBuilder.getPrincipal() : null);
    if (privateSecret) {
      dto.getSecret().setOwner(SecurityContextBuilder.getPrincipal());
    }

    return ResponseDTO.newResponse(
        ngSecretService.createFile(accountIdentifier, dto.getSecret(), encryptionKey, encryptedValue));
  }

  @POST
  @Hidden
  @Path("decrypt-encryption-details")
  @Consumes("application/x-kryo")
  @Produces("application/x-kryo")
  @ApiOperation(hidden = true, value = "Decrypt Encrypted Details", nickname = "postDecryptEncryptedDetails")
  @Operation(operationId = "postDecryptEncryptedDetails", summary = "Decrypt the encrypted details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns decrypted details")
      })
  @InternalApi
  public ResponseDTO<DecryptableEntity>
  decryptEncryptedDetails(@Body DecryptableEntityWithEncryptionConsumers decryptableEntityWithEncryptionConsumers,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    return ResponseDTO.newResponse(
        ngEncryptorService.decryptEncryptedDetails(decryptableEntityWithEncryptionConsumers.getDecryptableEntity(),
            decryptableEntityWithEncryptionConsumers.getEncryptedDataDetailList(), accountIdentifier));
  }

  @POST
  @Hidden
  @Path("encryption-details")
  @Consumes("application/x-kryo")
  @Produces("application/x-kryo")
  @ApiOperation(hidden = true, value = "Get Encryption Details", nickname = "postEncryptionDetails")
  @Operation(operationId = "postEncryptionDetails",
      summary = "Gets the encryption details of the Secret referenced fields that are passed in request",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns encryption details")
      })
  @InternalApi
  public ResponseDTO<List<EncryptedDataDetail>>
  getEncryptionDetails(@NotNull NGAccessWithEncryptionConsumer ngAccessWithEncryptionConsumer,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @AccountIdentifier @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    NGAccess ngAccess = ngAccessWithEncryptionConsumer.getNgAccess();
    DecryptableEntity decryptableEntity = ngAccessWithEncryptionConsumer.getDecryptableEntity();
    if (ngAccess == null || decryptableEntity == null) {
      return ResponseDTO.newResponse(new ArrayList<>());
    }
    for (Field field : decryptableEntity.getSecretReferenceFields()) {
      try {
        field.setAccessible(true);
        SecretRefData secretRefData = (SecretRefData) field.get(decryptableEntity);
        if (!Optional.ofNullable(secretRefData).isPresent() || secretRefData.isNull()) {
          continue;
        }
        Scope secretScope = secretRefData.getScope();
        SecretResponseWrapper secret =
            ngSecretService
                .get(accountIdentifier, getOrgIdentifier(ngAccess.getOrgIdentifier(), secretScope),
                    getProjectIdentifier(ngAccess.getProjectIdentifier(), secretScope), secretRefData.getIdentifier())
                .orElse(null);
        secretPermissionValidator.checkForAccessOrThrow(
            ResourceScope.of(accountIdentifier, getOrgIdentifier(ngAccess.getOrgIdentifier(), secretScope),
                getProjectIdentifier(ngAccess.getProjectIdentifier(), secretScope)),
            Resource.of(SECRET_RESOURCE_TYPE, secretRefData.getIdentifier()), SECRET_ACCESS_PERMISSION,
            secret != null ? secret.getSecret().getOwner() : null);

      } catch (IllegalAccessException illegalAccessException) {
        log.error("Error while checking access permission for secret: {}", field, illegalAccessException);
      }
    }
    return ResponseDTO.newResponse(encryptedDataService.getEncryptionDetails(
        ngAccessWithEncryptionConsumer.getNgAccess(), ngAccessWithEncryptionConsumer.getDecryptableEntity()));
  }

  @GET
  @Path("{identifier}/decrypt")
  @InternalApi
  @ApiOperation(hidden = true, value = "Get Decrypted Secret", nickname = "getDecryptedSecret")
  @Hidden
  public ResponseDTO<DecryptedSecretValue> getDecryptedSecretValue(
      @Parameter(description = "Secret Identifier") @NotNull @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @EntityIdentifier String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    return ResponseDTO.newResponse(
        encryptedDataService.decryptSecret(accountIdentifier, orgIdentifier, projectIdentifier, identifier));
  }

  private String getOrgIdentifier(String parentOrgIdentifier, @NotNull Scope scope) {
    if (scope != Scope.ACCOUNT) {
      return parentOrgIdentifier;
    }
    return null;
  }

  private String getProjectIdentifier(String parentProjectIdentifier, @NotNull Scope scope) {
    if (scope == Scope.PROJECT) {
      return parentProjectIdentifier;
    }
    return null;
  }
}
