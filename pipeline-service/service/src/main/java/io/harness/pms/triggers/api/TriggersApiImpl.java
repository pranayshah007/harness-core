/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.api;

import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.spec.server.pipeline.v1.TriggersApi;
import io.harness.spec.server.pipeline.v1.model.TriggerCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.TriggerCreateResponseBody;
import io.harness.spec.server.pipeline.v1.model.TriggerGetResponseBody;
import io.harness.spec.server.pipeline.v1.model.TriggerUpdateRequestBody;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class TriggersApiImpl implements TriggersApi {
  private final NGTriggerService ngTriggerService;

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response createTrigger(
      String org, String project, String pipeline, @Valid TriggerCreateRequestBody body, String harnessAccount) {
    return Response.ok().entity(new TriggerCreateResponseBody()).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response getTrigger(String org, String project, String trigger, String harnessAccount) {
    return Response.ok().entity(new TriggerGetResponseBody()).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response updateTrigger(
      @Valid TriggerUpdateRequestBody body, String org, String project, String trigger, String harnessAccount) {
    return Response.ok().entity(new TriggerCreateResponseBody()).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response deleteTrigger(String org, String project, String trigger, String harnessAccount) {
    return Response.ok().build();
  }
}
