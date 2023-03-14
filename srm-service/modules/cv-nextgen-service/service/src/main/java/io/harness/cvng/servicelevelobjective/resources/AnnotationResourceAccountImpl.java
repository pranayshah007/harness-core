/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.resources;

import static io.harness.cvng.core.beans.params.ProjectParams.fromProjectPathParams;
import static io.harness.cvng.core.services.CVNextGenConstants.ANNOTATION_ACCOUNT_PATH;

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
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Api("annotation")
@Path(ANNOTATION_ACCOUNT_PATH)
@Produces("application/json")
@ExposeInternalException
@OwnedBy(HarnessTeam.CV)
public class AnnotationResourceAccountImpl implements AnnotationResource {
  @Inject AnnotationService annotationService;

  @Override
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  @ApiOperation(value = "saves annotation", nickname = "saveAccountLevelAnnotation")
  public RestResponse<AnnotationResponse> saveAnnotation(
      @Valid ProjectPathParams projectPathParams, @NotNull @Valid AnnotationDTO annotationDTO) {
    ProjectParams projectParams = fromProjectPathParams(projectPathParams);
    return new RestResponse<>(annotationService.create(projectParams, annotationDTO));
  }

  @Override
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  @ApiOperation(value = "updates annotation message", nickname = "updateAccountLevelAnnotation")
  public RestResponse<AnnotationResponse> updateAnnotation(
      @Valid ResourcePathParams resourcePathParams, @NotNull @Valid AnnotationDTO annotationDTO) {
    return new RestResponse<>(annotationService.update(resourcePathParams.getIdentifier(), annotationDTO));
  }

  @Override
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  @ApiOperation(value = "delete annotation", nickname = "deleteAccountLevelAnnotation")
  public RestResponse<Boolean> deleteAnnotation(@Valid ResourcePathParams resourcePathParams) {
    return new RestResponse<>(annotationService.delete(resourcePathParams.getIdentifier()));
  }
}
