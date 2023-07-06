/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.resource;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.ENTITY_TYPE;
import static io.harness.NGCommonEntityConstants.ENTITY_TYPE_MESSAGE;
import static io.harness.NGCommonEntityConstants.FILE_CONTENT_MESSAGE;
import static io.harness.NGCommonEntityConstants.FILE_FILTER_PROPERTIES_MESSAGE;
import static io.harness.NGCommonEntityConstants.FILE_LIST_IDENTIFIERS_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.FILE_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.FILE_PATH_KEY;
import static io.harness.NGCommonEntityConstants.FILE_PATH_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.FILE_SEARCH_TERM_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.FILE_TAGS_MESSAGE;
import static io.harness.NGCommonEntityConstants.FILE_YAML_DEFINITION_MESSAGE;
import static io.harness.NGCommonEntityConstants.FILTER_IDENTIFIER_MESSAGE;
import static io.harness.NGCommonEntityConstants.FOLDER_DETAILS_MESSAGE;
import static io.harness.NGCommonEntityConstants.FOLDER_NODE_MESSAGE;
import static io.harness.NGCommonEntityConstants.IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.SCOPED_FILE_PATH_PARAM_MESSAGE;
import static io.harness.NGResourceFilterConstants.FILTER_KEY;
import static io.harness.NGResourceFilterConstants.IDENTIFIERS;
import static io.harness.NGResourceFilterConstants.PAGE_KEY;
import static io.harness.NGResourceFilterConstants.SEARCH_TERM_KEY;
import static io.harness.NGResourceFilterConstants.SIZE_KEY;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.filestore.FilePermissionConstants.FILE_DELETE_PERMISSION;
import static io.harness.filestore.FilePermissionConstants.FILE_EDIT_PERMISSION;
import static io.harness.filestore.FilePermissionConstants.FILE_VIEW_PERMISSION;
import static io.harness.ng.core.utils.NGUtils.validate;
import static io.harness.pms.rbac.NGResourceType.FILE;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

import io.harness.EntityType;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SearchPageParams;
import io.harness.data.validator.EntityIdentifier;
import io.harness.eraro.ErrorCode;
import io.harness.exception.NoResultFoundException;
import io.harness.filestore.dto.filter.FileStoreNodesFilterQueryPropertiesDTO;
import io.harness.filestore.dto.filter.FilesFilterPropertiesDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.dto.EmbeddedUserDetailsDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.ng.core.filestore.dto.FileFilterDTO;
import io.harness.ng.core.filestore.dto.FileStoreRequest;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.serializer.JsonUtils;
import io.harness.utils.PageUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
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
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.data.domain.Page;

@OwnedBy(CDP)
@Path("/file-store")
@Api("/file-store")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@Tag(name = "File Store", description = "This contains APIs related to File Store in Harness")
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
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class FileStoreResource {
  private final FileStoreService fileStoreService;
  private final AccessControlClient accessControlClient;

  @POST
  @Consumes(MULTIPART_FORM_DATA)
  @ApiOperation(value = "Create Folder or File including content", nickname = "create")
  @Operation(operationId = "create", summary = "Create Folder or File including content",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns create response") })
  public ResponseDTO<FileDTO>
  create(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) @NotBlank String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier,
      @Parameter(description = FILE_TAGS_MESSAGE) @FormDataParam("tags") String tagsJson,
      @Parameter(description = FILE_CONTENT_MESSAGE) @FormDataParam("content") InputStream content,
      @NotNull @BeanParam FileDTO file) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FILE, file.getIdentifier()), FILE_EDIT_PERMISSION);

    file.setAccountIdentifier(accountIdentifier);
    file.setOrgIdentifier(orgIdentifier);
    file.setProjectIdentifier(projectIdentifier);
    file.setTags(JsonUtils.asList(tagsJson, new TypeReference<List<NGTag>>() {}));

    validate(file);

    return ResponseDTO.newResponse(fileStoreService.create(file, content));
  }

  @PUT
  @Consumes(MULTIPART_FORM_DATA)
  @Path("{identifier}")
  @ApiOperation(value = "Update Folder or File including content", nickname = "update")
  @Operation(operationId = "update", summary = "Update Folder or File including content",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns update response") })
  public ResponseDTO<FileDTO>
  update(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) @NotBlank String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier,
      @Parameter(description = FILE_PARAM_MESSAGE) @NotBlank @EntityIdentifier @PathParam(IDENTIFIER_KEY)
      String identifier, @Parameter(description = FILE_TAGS_MESSAGE) @FormDataParam("tags") String tagsJson,
      @NotNull @BeanParam FileDTO file,
      @Parameter(description = FILE_CONTENT_MESSAGE) @FormDataParam("content") InputStream content) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FILE, file.getIdentifier()), FILE_EDIT_PERMISSION);

    file.setAccountIdentifier(accountIdentifier);
    file.setOrgIdentifier(orgIdentifier);
    file.setProjectIdentifier(projectIdentifier);
    file.setIdentifier(identifier);
    file.setTags(JsonUtils.asList(tagsJson, new TypeReference<List<NGTag>>() {}));

    validate(file);

    return ResponseDTO.newResponse(fileStoreService.update(file, content));
  }

  @GET
  @Path("{identifier}")
  @ApiOperation(value = "Get the Folder or File metadata", nickname = "getFile")
  @Operation(operationId = "getFile", summary = "Get the Folder or File metadata",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Get the Folder or File metadata")
      })
  public ResponseDTO<FileDTO>
  getFile(@Parameter(description = FILE_PARAM_MESSAGE) @PathParam(
              IDENTIFIER_KEY) @NotBlank @EntityIdentifier String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) @NotBlank String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FILE, identifier), FILE_VIEW_PERMISSION);

    Optional<FileDTO> file = fileStoreService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);

    return ResponseDTO.newResponse(file.orElseThrow(
        ()
            -> NoResultFoundException.newBuilder()
                   .code(ErrorCode.ENTITY_NOT_FOUND)
                   .message(String.format(
                       "File with identifier: %s, accountIdentifier: %s, orgIdentifier: %s and projectIdentifier: %s could not be found",
                       identifier, accountIdentifier, orgIdentifier, projectIdentifier))
                   .build()));
  }

  @GET
  @Path("files/{identifier}/download")
  @ApiOperation(value = "Download File content", nickname = "downloadFile")
  @Operation(operationId = "downloadFile", summary = "Download File",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Download File content")
      })
  public Response
  downloadFile(@Parameter(description = FILE_PARAM_MESSAGE) @PathParam(
                   IDENTIFIER_KEY) @NotBlank @EntityIdentifier String fileIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) @NotBlank String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FILE, fileIdentifier), FILE_VIEW_PERMISSION);

    File file = fileStoreService.downloadFile(accountIdentifier, orgIdentifier, projectIdentifier, fileIdentifier);
    return Response.ok(file, APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=" + file.getName())
        .build();
  }

  @GET
  @ApiOperation(value = "List Files and Folders metadata", nickname = "listFilesAndFolders")
  @Operation(operationId = "listFilesAndFolders", summary = "List Files and Folders metadata",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "List Files and Folders metadata")
      })
  public ResponseDTO<Page<FileDTO>>
  list(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) @NotBlank String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier,
      @Parameter(description = FILE_LIST_IDENTIFIERS_PARAM_MESSAGE) @QueryParam(IDENTIFIERS) List<String> identifiers,
      @Parameter(description = FILE_SEARCH_TERM_PARAM_MESSAGE) @QueryParam(SEARCH_TERM_KEY) String searchTerm,
      @BeanParam PageRequest pageRequest) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FILE, null), FILE_VIEW_PERMISSION);

    FileFilterDTO fileFilterDTO = FileFilterDTO.builder().identifiers(identifiers).searchTerm(searchTerm).build();
    return ResponseDTO.newResponse(fileStoreService.listFilesAndFolders(
        accountIdentifier, orgIdentifier, projectIdentifier, fileFilterDTO, PageUtils.getPageRequest(pageRequest)));
  }

  @DELETE
  @Path("{identifier}")
  @Consumes({"application/json"})
  @ApiOperation(value = "Delete File or Folder by identifier", nickname = "deleteFile")
  @Operation(operationId = "deleteFile", summary = "Delete File or Folder by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns true if deletion was successful.")
      })
  public ResponseDTO<Boolean>
  delete(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) @NotBlank String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier,
      @Parameter(description = FILE_PARAM_MESSAGE) @NotBlank @EntityIdentifier @PathParam(
          IDENTIFIER_KEY) String identifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FILE, identifier), FILE_DELETE_PERMISSION);

    return ResponseDTO.newResponse(
        fileStoreService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier));
  }

  @POST
  @Consumes({"application/json"})
  @Path("folder")
  @ApiOperation(value = "Get folder nodes at first level, not including sub-nodes", nickname = "getFolderNodes")
  @Operation(operationId = "getFolderNodes", summary = "Get folder nodes at first level, not including sub-nodes",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns the folder populated with file store nodes as children")
      })
  public ResponseDTO<FolderNodeDTO>
  listFolderNodes(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) @NotBlank String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier,
      @RequestBody(required = true, description = FOLDER_NODE_MESSAGE) @Valid @Parameter(
          description = FOLDER_DETAILS_MESSAGE) @NotNull FolderNodeDTO folderNodeDTO,
      @BeanParam FileStoreNodesFilterQueryPropertiesDTO filterQueryParams) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FILE, null), FILE_VIEW_PERMISSION);

    return ResponseDTO.newResponse(fileStoreService.listFolderNodes(
        accountIdentifier, orgIdentifier, projectIdentifier, folderNodeDTO, filterQueryParams));
  }

  @GET
  @Consumes({"application/json"})
  @Path("folder")
  @ApiOperation(value = "Get file store nodes on path", nickname = "getFileStoreNodesOnPath")
  @Operation(operationId = "getFileStoreNodesOnPath", summary = "Get file store nodes on path",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the folder populated with file store nodes as children")
      })
  @Hidden
  public ResponseDTO<FolderNodeDTO>
  listFileStoreNodesOnPath(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) @NotBlank String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier,
      @NotNull @Parameter(description = FILE_PATH_PARAM_MESSAGE) @QueryParam(FILE_PATH_KEY) String path,
      @BeanParam FileStoreNodesFilterQueryPropertiesDTO filterQueryParams) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FILE, null), FILE_VIEW_PERMISSION);

    return ResponseDTO.newResponse(fileStoreService.listFileStoreNodesOnPath(
        accountIdentifier, orgIdentifier, projectIdentifier, path, filterQueryParams));
  }

  @POST
  @Path("yaml")
  @Consumes({APPLICATION_YAML_MEDIA_TYPE})
  @ApiOperation(value = "Create File or Folder metadata via YAML", nickname = "createViaYAML")
  @Operation(operationId = "createViaYAML", summary = "Creates File or Folder metadata via YAML",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns create response") })
  public ResponseDTO<FileDTO>
  createViaYaml(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) @NotBlank String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier,
      @RequestBody(required = true,
          description = FILE_YAML_DEFINITION_MESSAGE) @NotNull @Valid FileStoreRequest fileStoreRequest) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FILE, fileStoreRequest.getFile().getIdentifier()), FILE_EDIT_PERMISSION);

    FileDTO file = fileStoreRequest.getFile();
    file.setAccountIdentifier(accountIdentifier);
    file.setOrgIdentifier(orgIdentifier);
    file.setProjectIdentifier(projectIdentifier);
    file.setDraft(true);

    return ResponseDTO.newResponse(fileStoreService.create(file, null));
  }

  @PUT
  @Path("yaml/{identifier}")
  @Consumes({APPLICATION_YAML_MEDIA_TYPE})
  @ApiOperation(value = "Update File or Folder metadata via YAML", nickname = "updateViaYAML")
  @Operation(operationId = "updateViaYAML", summary = "Update File or Folder metadata via YAML",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns update response") })
  public ResponseDTO<FileDTO>
  updateViaYaml(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) @NotBlank String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier,
      @Parameter(description = FILE_PARAM_MESSAGE) @PathParam(IDENTIFIER_KEY) @EntityIdentifier String identifier,
      @RequestBody(required = true,
          description = FILE_YAML_DEFINITION_MESSAGE) @NotNull @Valid FileStoreRequest fileStoreRequest) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FILE, fileStoreRequest.getFile().getIdentifier()), FILE_EDIT_PERMISSION);

    FileDTO file = fileStoreRequest.getFile();
    file.setAccountIdentifier(accountIdentifier);
    file.setOrgIdentifier(orgIdentifier);
    file.setProjectIdentifier(projectIdentifier);
    file.setIdentifier(identifier);
    file.setDraft(true);

    return ResponseDTO.newResponse(fileStoreService.update(file, null));
  }

  @GET
  @Consumes({"application/json"})
  @Path("{identifier}/referenced-by")
  @ApiOperation(
      value = "Get list of entities where file is referenced by queried entity type", nickname = "getReferencedBy")
  @Operation(operationId = "getReferencedBy",
      summary = "Get list of entities where file is referenced by queried entity type",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of entities where file is referenced by")
      })
  public ResponseDTO<PageResponse<EntitySetupUsageDTO>>
  getReferencedBy(@Parameter(description = "Page number of navigation. The default value is 0") @QueryParam(
                      PAGE_KEY) @DefaultValue("0") int page,
      @Parameter(description = "Number of entries per page. The default value is 100") @QueryParam(
          SIZE_KEY) @DefaultValue("100") @Max(1000) int size,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) @NotBlank String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier,
      @Parameter(description = FILE_PARAM_MESSAGE) @NotBlank @EntityIdentifier @PathParam(IDENTIFIER_KEY)
      String identifier, @Parameter(description = ENTITY_TYPE_MESSAGE) @QueryParam(ENTITY_TYPE) EntityType entityType,
      @QueryParam(SEARCH_TERM_KEY) String searchTerm) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FILE, identifier), FILE_VIEW_PERMISSION);

    return ResponseDTO.newResponse(getNGPageResponse(fileStoreService.listReferencedBy(
        SearchPageParams.builder().page(page).size(size).searchTerm(searchTerm).build(), accountIdentifier,
        orgIdentifier, projectIdentifier, identifier, entityType)));
  }

  @GET
  @Consumes({"application/json"})
  @Path("supported-entity-types")
  @ApiOperation(value = "Get the list of supported entity types for files", nickname = "getEntityTypes")
  @Operation(operationId = "getEntityTypes", summary = "Get the list of supported entity types for files",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of supported entity types for file")
      })
  public ResponseDTO<List<EntityType>>
  getSupportedEntityTypes(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) @NotBlank String accountIdentifier) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(FILE, null), FILE_VIEW_PERMISSION);
    return ResponseDTO.newResponse(fileStoreService.getSupportedEntityTypes());
  }

  @POST
  @Consumes({"application/json"})
  @Path("files/filter")
  @ApiOperation(value = "Get filtered list of Files or Folders", nickname = "listFilesWithFilter")
  @Operation(operationId = "listFilesWithFilter", summary = "Get filtered list of Files or Folders",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns filtered list of Files or Folder")
      })
  public ResponseDTO<Page<FileDTO>>
  listFilesWithFilter(
      @RequestBody(description = "Details of Page including: size, index, sort") @BeanParam PageRequest pageRequest,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier,
      @Parameter(description = FILTER_IDENTIFIER_MESSAGE) @QueryParam(FILTER_KEY) String filterIdentifier,
      @Parameter(description = FILE_SEARCH_TERM_PARAM_MESSAGE) @QueryParam(SEARCH_TERM_KEY) String searchTerm,
      @RequestBody(description = FILE_FILTER_PROPERTIES_MESSAGE) FilesFilterPropertiesDTO filesFilterPropertiesDTO) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FILE, null), FILE_VIEW_PERMISSION);

    return ResponseDTO.newResponse(
        fileStoreService.listFilesWithFilter(accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier,
            searchTerm, filesFilterPropertiesDTO, PageUtils.getPageRequest(pageRequest)));
  }

  @GET
  @Consumes({"application/json"})
  @Path("files/createdBy")
  @ApiOperation(value = "Get list of created by user details", nickname = "getCreatedByList")
  @Operation(operationId = "getCreatedByList", summary = "Get list of created by user details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of created by user details")
      })
  public ResponseDTO<Set<EmbeddedUserDetailsDTO>>
  getCreatedByList(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FILE, null), FILE_VIEW_PERMISSION);

    return ResponseDTO.newResponse(
        fileStoreService.getCreatedByList(accountIdentifier, orgIdentifier, projectIdentifier));
  }

  @GET
  @Consumes({"application/json"})
  @Path("files/{scopedFilePath}/content")
  @ApiOperation(
      value = "Get file content as string using scopedFilePath", nickname = "getFileContentUsingScopedFilePath")
  @Operation(operationId = "getFileContentUsingScopedFilePath", summary = "Get file content of scopedFilePath",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the file content of scopedFilePath")
      })
  public ResponseDTO<String>
  getFileContentUsingScopedFilePath(@Parameter(description = SCOPED_FILE_PATH_PARAM_MESSAGE) @PathParam(
                                        "scopedFilePath") @NotBlank String scopedFilePath,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(PROJECT_KEY) String projectIdentifier) {
    return ResponseDTO.newResponse(fileStoreService.getFileContentAsString(
        accountIdentifier, orgIdentifier, projectIdentifier, scopedFilePath, Long.MAX_VALUE));
  }
}
