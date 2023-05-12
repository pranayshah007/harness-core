/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.rest.RestResponse;

import software.wings.beans.AuditPreferenceResponse;
import software.wings.beans.Preference;
import software.wings.beans.User;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.PreferenceService;
import software.wings.service.intfc.UserGroupService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * The Class Preference Resource.
 */
@Api("preference")
@Path("/preference")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Scope(ResourceType.PREFERENCE)
public class PreferenceResource {
  private PreferenceService prefService;

  @Inject private UserGroupService userGroupService;

  @Inject
  public PreferenceResource(PreferenceService prefService) {
    this.prefService = prefService;
  }

  /**
   * List the preferences
   *
   * @param accountId         the account id
   * @return the rest response
   */
  @GET
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<PageResponse<Preference>> listPreferences(
      @QueryParam("accountId") @NotEmpty String accountId, @BeanParam PageRequest<Preference> pageRequest) {
    User user = UserThreadLocal.get();
    return new RestResponse<>(prefService.list(pageRequest, accountId, user.getUuid()));
  }

  @GET
  @Timed
  @Path("auditPreference")
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<AuditPreferenceResponse> listAuditPreferences(
      @QueryParam("accountId") @NotEmpty String accountId) {
    User user = UserThreadLocal.get();
    return new RestResponse<>(prefService.listAuditPreferences(accountId, user.getUuid()));
  }

  /**
   * Get the preference
   *
   * @param accountId         the account id
   * @param preferenceId      the preference id
   * @return the rest response
   */
  @GET
  @Timed
  @ExceptionMetered
  @Path("{preferenceId}")
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Preference> getPreference(
      @QueryParam("accountId") @NotEmpty String accountId, @PathParam("preferenceId") String preferenceId) {
    User user = UserThreadLocal.get();
    return new RestResponse<>(prefService.get(accountId, user.getUuid(), preferenceId));
  }

  /**
   * Create the preference
   *
   * @param accountId         the account id
   * @param preference        the preference
   * @return the rest response
   */
  @POST
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Preference> savePreference(
      @QueryParam("accountId") @NotEmpty String accountId, Preference preference) {
    User user = UserThreadLocal.get();
    preference.setUserId(user.getUuid());
    preference.setAccountId(accountId);
    return new RestResponse<>(prefService.save(accountId, user.getUuid(), preference));
  }

  /**
   * Update the preference
   *
   * @param accountId         the account id
   * @param preferenceId      the preference id
   * @param preference        the preference
   * @return the rest response
   */
  @PUT
  @Timed
  @ExceptionMetered
  @Path("{preferenceId}")
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Preference> updatePreference(@QueryParam("accountId") @NotEmpty String accountId,
      @PathParam("preferenceId") String preferenceId, Preference preference) {
    User user = UserThreadLocal.get();
    return new RestResponse<>(prefService.update(accountId, user.getUuid(), preferenceId, preference));
  }

  /**
   * Delete the preference
   *
   * @param accountId         the account id
   * @param preferenceId      the preference id
   * @return the rest response
   */
  @DELETE
  @Path("{preferenceId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Void> deletePreference(
      @QueryParam("accountId") @NotEmpty String accountId, @PathParam("preferenceId") String preferenceId) {
    User user = UserThreadLocal.get();
    prefService.delete(accountId, user.getUuid(), preferenceId);
    return new RestResponse<>();
  }
}
