/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources.alert;

import static io.harness.beans.FeatureName.SPG_ENABLE_NOTIFICATION_RULES;

import static software.wings.beans.alert.AlertType.CONTINUOUS_VERIFICATION_ALERT;
import static software.wings.security.PermissionAttribute.ResourceType.ROLE;

import static java.util.stream.Collectors.toList;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.notifications.AlertVisibilityChecker;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.LearningEngineAuth;

import software.wings.beans.User;
import software.wings.beans.alert.Alert;
import software.wings.beans.alert.Alert.AlertKeys;
import software.wings.beans.alert.cv.ContinuousVerificationAlertData;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.analysis.ContinuousVerificationService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.alert.NotificationRulesStatusService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import java.util.Set;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

/**
 * Created by brett on 10/18/17
 */
@Api("alerts")
@Path("/alerts")
@Produces("application/json")
@Scope(ROLE)
public class AlertResource {
  @Inject private AlertService alertService;
  @Inject private AlertVisibilityChecker alertVisibilityChecker;
  @Inject private ContinuousVerificationService continuousVerificationService;

  @Inject private NotificationRulesStatusService notificationRulesStatusService;

  @Inject private FeatureFlagService featureFlagService;

  // PL-1389
  private static final Set<software.wings.beans.alert.AlertType> ALERT_TYPES_TO_NOT_SHOW_UNDER_BELL_ICON =
      ImmutableSet.of(CONTINUOUS_VERIFICATION_ALERT);

  @GET
  @Timed
  @ExceptionMetered
  public RestResponse<PageResponse<Alert>> list(
      @QueryParam("accountId") String accountId, @BeanParam PageRequest<Alert> request) {
    request.addFilter(AlertKeys.type, Operator.NOT_IN, ALERT_TYPES_TO_NOT_SHOW_UNDER_BELL_ICON.toArray());
    PageResponse<Alert> response = alertService.list(request);

    if (featureFlagService.isEnabled(SPG_ENABLE_NOTIFICATION_RULES, accountId)) {
      User user = UserThreadLocal.get();
      if (null == user) {
        throw new WingsException(ErrorCode.USER_DOES_NOT_EXIST, "Could not find user while trying to list alerts");
      }

      if (!notificationRulesStatusService.get(accountId).isEnabled()) {
        return new RestResponse<>();
      }

      List<String> userGroupByUserAccountId = alertVisibilityChecker.listUserGroupByUserAccountId(accountId, user);

      List<Alert> filteredAlerts =
          response.stream()
              .filter(
                  alert -> alertVisibilityChecker.shouldAlertBeShownToUser(userGroupByUserAccountId, alert, accountId))
              .collect(toList());

      response.setResponse(filteredAlerts);
    }

    return new RestResponse<>(response);
  }

  @GET
  @Path("/types")
  public RestResponse<List<AlertType>> listCategoriesAndTypes(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(
        alertService.listCategoriesAndTypes(accountId).stream().map(AlertType::new).collect(toList()));
  }

  @POST
  @Path("/open-cv-alert")
  @LearningEngineAuth
  public RestResponse<Boolean> openCVAlert(
      @QueryParam("cvConfigId") String cvConfigId, @Body ContinuousVerificationAlertData alertData) {
    return new RestResponse<>(continuousVerificationService.openAlert(cvConfigId, alertData));
  }

  @POST
  @Path("/open-cv-alert-with-ttl")
  @LearningEngineAuth
  public RestResponse<Boolean> openCVAlert(@QueryParam("cvConfigId") String cvConfigId,
      @QueryParam("validUntil") long validUntil, @Body ContinuousVerificationAlertData alertData) {
    return new RestResponse<>(continuousVerificationService.openAlert(cvConfigId, alertData, validUntil));
  }

  @POST
  @Path("/close-cv-alert")
  @LearningEngineAuth
  public RestResponse<Boolean> closeCVAlert(
      @QueryParam("cvConfigId") String cvConfigId, @Body ContinuousVerificationAlertData alertData) {
    return new RestResponse<>(continuousVerificationService.closeAlert(cvConfigId, alertData));
  }
}
