/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.resources;

import static io.harness.cvng.core.beans.params.ProjectParams.fromProjectPathParams;
import static io.harness.cvng.core.services.CVNextGenConstants.ANNOTATION_ACCOUNT_PATH;
import static io.harness.cvng.core.services.CVNextGenConstants.RESOURCE_IDENTIFIER_PATH;

import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ProjectPathParams;
import io.harness.cvng.core.beans.params.ResourcePathParams;
import io.harness.cvng.servicelevelobjective.beans.AnnotationDTO;
import io.harness.cvng.servicelevelobjective.beans.AnnotationResponse;
import io.harness.cvng.servicelevelobjective.services.api.AnnotationService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Parameter;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import retrofit2.http.Body;

@Api(value = ANNOTATION_ACCOUNT_PATH, tags = "Annotation")
@Path(ANNOTATION_ACCOUNT_PATH)
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
/*@Tag(name = "Annotation", description = "This contains APIs related to CRUD operations of Annotation")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })*/
@OwnedBy(HarnessTeam.CV)
public class AnnotationAccountLevelResource {
  @Inject AnnotationService annotationService;

  public static final String SLO = "SLO";
  public static final String EDIT_PERMISSION = "chi_slo_edit";
  public static final String DELETE_PERMISSION = "chi_slo_delete";

  @POST
  @Consumes("application/json")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "saves annotation", nickname = "saveAccountLevelAnnotation")
  public RestResponse<AnnotationResponse> saveAccountLevelAnnotation(@BeanParam ProjectPathParams projectPathParams,
      @Parameter(
          description = "Details of the annotation to be saved") @NotNull @Valid @Body AnnotationDTO annotationDTO) {
    ProjectParams projectParams = fromProjectPathParams(projectPathParams);
    return new RestResponse<>(annotationService.create(projectParams, annotationDTO));
  }

  @PUT
  @Consumes("application/json")
  @Timed
  @ExceptionMetered
  @Path(RESOURCE_IDENTIFIER_PATH)
  @ApiOperation(value = "updates annotation message", nickname = "updateAccountLevelAnnotationMessage")
  @NGAccessControlCheck(resourceType = SLO, permission = EDIT_PERMISSION)
  public RestResponse<AnnotationResponse> updateAccountLevelAnnotationMessage(
      @BeanParam ResourcePathParams resourcePathParams,
      @Parameter(
          description = "Details of the annotation to be updated") @NotNull @Valid @Body AnnotationDTO annotationDTO) {
    return new RestResponse<>(annotationService.update(resourcePathParams.getIdentifier(), annotationDTO));
  }

  @DELETE
  @Timed
  @ExceptionMetered
  @Path(RESOURCE_IDENTIFIER_PATH)
  @ApiOperation(value = "delete annotation", nickname = "deleteAccountLevelAnnotation")
  @NGAccessControlCheck(resourceType = SLO, permission = DELETE_PERMISSION)
  public RestResponse<Boolean> deleteAccountLevelAnnotation(@BeanParam ResourcePathParams resourcePathParams) {
    return new RestResponse<>(annotationService.delete(resourcePathParams.getIdentifier()));
  }
}