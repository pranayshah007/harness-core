/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.RESOURCE_IDENTIFIER_PATH;

import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.params.ProjectPathParams;
import io.harness.cvng.core.beans.params.ResourcePathParams;
import io.harness.cvng.servicelevelobjective.beans.AnnotationDTO;
import io.harness.cvng.servicelevelobjective.beans.AnnotationResponse;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
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

@Api("annotation")
@OwnedBy(HarnessTeam.CV)
@Produces("application/json")
@ExposeInternalException
public interface AnnotationResource {
  @POST
  @Consumes("application/json")
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  @Path("")
  RestResponse<AnnotationResponse> saveAnnotation(
      @Valid @BeanParam ProjectPathParams projectPathParams, @NotNull @Valid @Body AnnotationDTO annotationDTO);

  @PUT
  @Consumes("application/json")
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  @Path(RESOURCE_IDENTIFIER_PATH)
  RestResponse<AnnotationResponse> updateAnnotation(
      @Valid @BeanParam ResourcePathParams resourcePathParams, @NotNull @Valid @Body AnnotationDTO annotationDTO);

  @DELETE
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  @Path(RESOURCE_IDENTIFIER_PATH)
  RestResponse<Boolean> deleteAnnotation(@Valid @BeanParam ResourcePathParams resourcePathParams);
}
