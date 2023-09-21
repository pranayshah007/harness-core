/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.SearchFilter.Operator.EQ;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATIONS;
import static software.wings.security.PermissionAttribute.ResourceType.APPLICATION;

import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.data.parser.Parser;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rest.RestResponse;

import software.wings.beans.Application;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.ListAPI;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.AppService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Application Resource class.
 *
 * @author Rishi
 */
@Api("/apps")
@Path("/apps")
@Produces("application/json")
@Scope(APPLICATION)
public class AppResource {
  private AppService appService;
  private LimitCheckerFactory limitCheckerFactory;
  @Inject private FeatureFlagService featureFlagService;

  private static final String CUSTOM_MAX_LIMIT = "1200";
  private static final String LARGE_PAGE_SIZE_LIMIT = "3000";

  @Inject
  public AppResource(AppService appService, LimitCheckerFactory limitCheckerFactory) {
    this.appService = appService;
    this.limitCheckerFactory = limitCheckerFactory;
  }

  /**
   * List.
   *
   * @param pageRequest        the page request
   * @return the rest response
   */
  @GET
  @ListAPI(APPLICATION)
  @Timed
  @ExceptionMetered
  public RestResponse<PageResponse<Application>> list(@BeanParam PageRequest<Application> pageRequest,
      @QueryParam("accountId") String accountId, @QueryParam("appIds") List<String> appIds,
      @QueryParam("details") @DefaultValue("true") boolean details, @QueryParam("tagFilter") String tagFilter,
      @QueryParam("withTags") @DefaultValue("false") boolean withTags) {
    if (accountId != null) {
      pageRequest.addFilter("accountId", EQ, accountId);
    }
    if (featureFlagService.isEnabled(FeatureName.CUSTOM_MAX_PAGE_SIZE, accountId)) {
      String baseLimit = CUSTOM_MAX_LIMIT;
      if (featureFlagService.isEnabled(FeatureName.EXTRA_LARGE_PAGE_SIZE, accountId)) {
        baseLimit = LARGE_PAGE_SIZE_LIMIT;
      }

      String limit = PageRequest.UNLIMITED.equals(pageRequest.getLimit())
          ? baseLimit
          : Integer.toString(Parser.asInt(pageRequest.getLimit(), Integer.parseInt(baseLimit)));
      pageRequest.setLimit(limit);
    }
    return new RestResponse<>(appService.list(pageRequest, details, withTags, tagFilter, true));
  }

  /**
   * Save.
   *
   * @param app the app
   * @return the rest response
   */
  @POST
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_APPLICATIONS)
  public RestResponse<Application> save(@NotEmpty @QueryParam("accountId") String accountId, Application app) {
    app.setAccountId(accountId);
    return new RestResponse<>(appService.save(app));
  }

  /**
   * Update.
   *
   * @param appId the app id
   * @param app   the app
   * @return the rest response
   */
  @PUT
  @Path("{appId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_APPLICATIONS)
  public RestResponse<Application> update(@PathParam("appId") String appId, Application app) {
    app.setUuid(appId);
    return new RestResponse<>(appService.update(app));
  }

  /**
   * Gets the.
   *
   * @param appId  the app id
   * @return the rest response
   */
  @GET
  @Path("{appId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Application> get(
      @PathParam("appId") String appId, @QueryParam("details") @DefaultValue("true") boolean details) {
    return new RestResponse<>(appService.get(appId, true));
  }

  /**
   * Delete.
   *
   * @param appId the app id
   * @return the rest response
   */
  @DELETE
  @Path("{appId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_APPLICATIONS)
  public RestResponse delete(@PathParam("appId") String appId) {
    appService.delete(appId);
    return new RestResponse();
  }
}
