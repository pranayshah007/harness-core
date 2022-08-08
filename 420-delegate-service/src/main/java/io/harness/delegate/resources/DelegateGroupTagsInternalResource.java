/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroupDTO;
import io.harness.delegate.beans.DelegateGroupTags;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;
import io.harness.service.intfc.DelegateSetupService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("/delegate-group-tags-internal")
@Path("/delegate-group-tags-internal")
@Produces("application/json")
@OwnedBy(DEL)
@Hidden
public class DelegateGroupTagsInternalResource {
  private final DelegateSetupService delegateSetupService;

  @Inject
  public DelegateGroupTagsInternalResource(DelegateSetupService delegateSetupService) {
    this.delegateSetupService = delegateSetupService;
  }

  @GET
  @Timed
  @Hidden
  @InternalApi
  @ExceptionMetered
  public RestResponse<Optional<DelegateGroupDTO>> listSelectorsFromDelegateGroup(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Group Identifier") @QueryParam(
          NGCommonEntityConstants.GROUP_IDENTIFIER_KEY) @NotEmpty String groupIdentifier) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateSetupService.listDelegateGroupTags(
          accountIdentifier, orgIdentifier, projectIdentifier, groupIdentifier));
    }
  }

  @POST
  @Timed
  @Hidden
  @InternalApi
  @ExceptionMetered
  public RestResponse<Optional<DelegateGroupDTO>> addSelectorsToDelegateGroup(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Group Identifier") @QueryParam(
          NGCommonEntityConstants.GROUP_IDENTIFIER_KEY) @NotEmpty String groupIdentifier,
      @RequestBody(required = true, description = "Set of tags") DelegateGroupTags tags) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateSetupService.addDelegateGroupTags(
          accountIdentifier, orgIdentifier, projectIdentifier, groupIdentifier, tags));
    }
  }

  @PUT
  @Timed
  @Hidden
  @InternalApi
  @ExceptionMetered
  public RestResponse<Optional<DelegateGroupDTO>> updateSelectorsToDelegateGroup(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Delegate Group Identifier") @QueryParam(
          NGCommonEntityConstants.GROUP_IDENTIFIER_KEY) @NotEmpty String groupIdentifier,
      @RequestBody(required = true, description = "Set of tags") DelegateGroupTags tags) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateSetupService.updateDelegateGroupTags(
          accountIdentifier, orgIdentifier, projectIdentifier, groupIdentifier, tags));
    }
  }

  // TODO: Remove below api and related code once we verify through logs that no one is using it

  @PUT
  @Path("/tags")
  @Timed
  @Hidden
  @InternalApi
  @ExceptionMetered
  public RestResponse<DelegateGroup> updateTagsForDelegateGroup(
      @Parameter(description = "Delegate Group Name") @QueryParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @NotEmpty String groupName,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @RequestBody(required = true, description = "List of tags") DelegateGroupTags tags) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(
          delegateSetupService.updateDelegateGroupTags_old(accountId, orgId, projectId, groupName, tags.getTags()));
    }
  }

  @POST
  @Path("/delegate-groups")
  @Timed
  @Hidden
  @InternalApi
  @ExceptionMetered
  public RestResponse<List<DelegateGroupDTO>> listDelegateGroupsHavingTags(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(required = true, description = "Set of tags") DelegateGroupTags tags) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR)) {
      return new RestResponse<>(
          delegateSetupService.listDelegateGroupsHavingTags(accountIdentifier, orgIdentifier, projectIdentifier, tags));
    }
  }
}
