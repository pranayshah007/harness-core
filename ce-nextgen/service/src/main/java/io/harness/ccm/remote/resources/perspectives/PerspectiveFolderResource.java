/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.perspectives;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.TelemetryConstants.FOLDER_CREATED;
import static io.harness.ccm.TelemetryConstants.FOLDER_ID;
import static io.harness.ccm.TelemetryConstants.MODULE;
import static io.harness.ccm.TelemetryConstants.MODULE_NAME;
import static io.harness.ccm.rbac.CCMRbacHelperImpl.PERMISSION_MISSING_MESSAGE;
import static io.harness.ccm.rbac.CCMRbacHelperImpl.RESOURCE_FOLDER;
import static io.harness.ccm.rbac.CCMRbacPermissions.FOLDER_CREATE_AND_EDIT;
import static io.harness.ccm.rbac.CCMRbacPermissions.FOLDER_VIEW;
import static io.harness.ccm.rbac.CCMRbacPermissions.PERSPECTIVE_CREATE_AND_EDIT;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.audittrails.events.PerspectiveFolderCreateEvent;
import io.harness.ccm.audittrails.events.PerspectiveFolderDeleteEvent;
import io.harness.ccm.audittrails.events.PerspectiveFolderUpdateEvent;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.dto.CreatePerspectiveFolderDTO;
import io.harness.ccm.views.dto.MovePerspectiveDTO;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.CEViewFolder;
import io.harness.ccm.views.entities.ViewType;
import io.harness.ccm.views.graphql.QLCEView;
import io.harness.ccm.views.service.CEViewFolderService;
import io.harness.ccm.views.service.CEViewService;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.WingsException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryReporter;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Api("perspectiveFolders")
@Path("perspectiveFolders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost Perspectives Folders",
    description = "Group your Perspectives using Folders in ways that are more meaningful to your business needs.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class PerspectiveFolderResource {
  private final CEViewFolderService ceViewFolderService;
  private final CEViewService ceViewService;
  private final TelemetryReporter telemetryReporter;
  private final CCMRbacHelper rbacHelper;

  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;

  @Inject
  public PerspectiveFolderResource(CEViewFolderService ceViewFolderService, CEViewService ceViewService,
      TelemetryReporter telemetryReporter, @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate,
      OutboxService outboxService, CCMRbacHelper rbacHelper) {
    this.ceViewFolderService = ceViewFolderService;
    this.ceViewService = ceViewService;
    this.telemetryReporter = telemetryReporter;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
    this.rbacHelper = rbacHelper;
  }

  @POST
  @Path("create")
  @Timed
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Create perspective folder", nickname = "createPerspectiveFolder")
  @FeatureRestrictionCheck(FeatureRestrictionName.PERSPECTIVES)
  @LogAccountIdentifier
  @Operation(operationId = "createPerspectiveFolder", description = "Create a Perspective Folder.",
      summary = "Create a Perspective folder",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns a created CEViewFolder object with all its details",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<CEViewFolder>
  create(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing Perspective's CEViewFolder object")
      @Valid CreatePerspectiveFolderDTO createPerspectiveFolderDTO) {
    rbacHelper.checkFolderEditPermission(accountId, null, null, null);
    CEViewFolder ceViewFolder = createPerspectiveFolderDTO.getCeViewFolder();
    ceViewFolder.setAccountId(accountId);
    ceViewFolder.setPinned(false);
    ceViewFolder.setViewType(ViewType.CUSTOMER);
    CEViewFolder ceViewFolderFinal = ceViewFolderService.save(ceViewFolder);
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    properties.put(FOLDER_ID, ceViewFolder.getUuid());
    if (createPerspectiveFolderDTO.getPerspectiveIds() != null
        && CollectionUtils.isNotEmpty(createPerspectiveFolderDTO.getPerspectiveIds())) {
      ceViewFolderService.moveMultipleCEViews(
          accountId, createPerspectiveFolderDTO.getPerspectiveIds(), ceViewFolder.getUuid());
    }
    telemetryReporter.sendTrackEvent(
        FOLDER_CREATED, null, accountId, properties, Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    return ResponseDTO.newResponse(
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          outboxService.save(new PerspectiveFolderCreateEvent(accountId, ceViewFolderFinal.toDTO()));
          return ceViewFolderFinal;
        })));
  }

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Get folders for account", nickname = "getFolders")
  @LogAccountIdentifier
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "getFolders", description = "Fetch folders given an accountId",
      summary = "Fetch folders for an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns List of CEViewFolders",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<CEViewFolder>>
  getFolders(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(description = "Search by folder name pattern") @QueryParam(
          "folderNamePattern") String folderNamePattern) {
    List<CEViewFolder> ceViewFolders =
        ceViewFolderService.getFolders(accountId, folderNamePattern == null ? "" : folderNamePattern);

    List<CEViewFolder> allowedCeViewFolders = null;
    if (ceViewFolders != null) {
      Set<String> allowedFolderIds = rbacHelper.checkFolderIdsGivenPermission(accountId, null, null,
          ceViewFolders.stream().map(ceViewFolder -> ceViewFolder.getUuid()).collect(Collectors.toSet()), FOLDER_VIEW);
      allowedCeViewFolders = ceViewFolders.stream()
                                 .filter(ceViewFolder -> allowedFolderIds.contains(ceViewFolder.getUuid()))
                                 .collect(Collectors.toList());
    }

    if ((allowedCeViewFolders == null || allowedCeViewFolders.size() == 0)
        && (ceViewFolders != null && ceViewFolders.size() > 0)) {
      throw new NGAccessDeniedException(
          String.format(PERMISSION_MISSING_MESSAGE, FOLDER_VIEW, RESOURCE_FOLDER), WingsException.USER, null);
    }
    return ResponseDTO.newResponse(allowedCeViewFolders);
  }

  @GET
  @Path("{folderId}/perspectives")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Get All perspectives in a folder", nickname = "getAllFolderPerspectives")
  @LogAccountIdentifier
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "getAllFolderPerspectives",
      description = "Return details of all the Perspectives for the given account ID and folder",
      summary = "Return details of all the Perspectives",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns a List of Perspectives",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<QLCEView>>
  getPerspectives(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Unique identifier for folder") @PathParam(
          "folderId") String folderId) {
    rbacHelper.checkFolderViewPermission(accountId, null, null, folderId);
    return ResponseDTO.newResponse(ceViewService.getAllViews(accountId, folderId, true, null));
  }

  @PUT
  @Timed
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Update a folder", nickname = "updateFolder")
  @LogAccountIdentifier
  @Operation(operationId = "updateFolder", description = "Update a folder", summary = "Update a folder",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "CEViewFolder object", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<CEViewFolder>
  updateFolder(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true,
          description = "Request body containing ceViewFolder object") @Valid CEViewFolder ceViewFolder) {
    rbacHelper.checkFolderEditPermission(accountId, null, null, ceViewFolder.getUuid());
    List<CEViewFolder> oldPerspectiveFolder =
        ceViewFolderService.getFolders(accountId, new ArrayList<>(Arrays.asList(ceViewFolder.getUuid())));
    CEViewFolder newPerspectiveFolder = ceViewFolderService.updateFolder(accountId, ceViewFolder);
    return ResponseDTO.newResponse(
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          if (oldPerspectiveFolder.size() > 0) {
            outboxService.save(new PerspectiveFolderUpdateEvent(
                accountId, newPerspectiveFolder.toDTO(), oldPerspectiveFolder.get(0).toDTO()));
          }
          return newPerspectiveFolder;
        })));
  }

  @POST
  @Path("movePerspectives")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Move perspectives", nickname = "movePerspectives")
  @FeatureRestrictionCheck(FeatureRestrictionName.PERSPECTIVES)
  @LogAccountIdentifier
  @Operation(operationId = "movePerspectives", description = "Move a perspective from a folder to another.",
      summary = "Move a Perspective",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the new CEView object",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<CEView>>
  movePerspectives(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                       NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing perspectiveIds to be moved and newFolderId")
      @Valid MovePerspectiveDTO movePerspectiveDTO) {
    List<String> perspectiveIds = movePerspectiveDTO.getPerspectiveIds();
    String newFolderId = movePerspectiveDTO.getNewFolderId();

    Set<String> perspectiveFolderIds = ceViewService.getPerspectiveFolderIds(accountId, perspectiveIds);
    perspectiveFolderIds.add(newFolderId);

    Set<String> permittedPerspectiveFolderIds =
        rbacHelper.checkFolderIdsGivenPermission(accountId, null, null, perspectiveFolderIds, FOLDER_CREATE_AND_EDIT);
    if (permittedPerspectiveFolderIds.size() != perspectiveFolderIds.size()) {
      throw new NGAccessDeniedException(
          String.format(PERMISSION_MISSING_MESSAGE, FOLDER_CREATE_AND_EDIT, RESOURCE_FOLDER), WingsException.USER,
          null);
    }

    permittedPerspectiveFolderIds = rbacHelper.checkFolderIdsGivenPermission(
        accountId, null, null, perspectiveFolderIds, PERSPECTIVE_CREATE_AND_EDIT);
    if (permittedPerspectiveFolderIds.size() != perspectiveFolderIds.size()) {
      throw new NGAccessDeniedException(
          String.format(PERMISSION_MISSING_MESSAGE, PERSPECTIVE_CREATE_AND_EDIT, RESOURCE_FOLDER), WingsException.USER,
          null);
    }
    return ResponseDTO.newResponse(ceViewFolderService.moveMultipleCEViews(accountId, perspectiveIds, newFolderId));
  }

  @DELETE
  @Path("{folderId}")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Delete folder", nickname = "deleteFolder")
  @LogAccountIdentifier
  @Operation(operationId = "deleteFolder", description = "Delete a Folder for the given Folder ID.",
      summary = "Delete a folder",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "A boolean whether the delete was successful or not",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Boolean>
  delete(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @PathParam("folderId") @Parameter(required = true,
          description = "Unique identifier for the Perspective folder") @NotNull @Valid String folderId) {
    rbacHelper.checkFolderDeletePermission(accountId, null, null, folderId);
    List<CEViewFolder> perspectiveFolder =
        ceViewFolderService.getFolders(accountId, new ArrayList<>(Arrays.asList(folderId)));
    boolean result = ceViewFolderService.delete(accountId, folderId);
    return ResponseDTO.newResponse(
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          if (perspectiveFolder.size() > 0) {
            outboxService.save(new PerspectiveFolderDeleteEvent(accountId, perspectiveFolder.get(0).toDTO()));
          }
          return result;
        })));
  }
}
