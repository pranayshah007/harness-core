/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static software.wings.graphql.datafetcher.billing.CloudBillingHelper.unified;
import static software.wings.security.PermissionAttribute.ResourceType.USER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.views.entities.CEReportSchedule;
import io.harness.ccm.views.service.CEReportScheduleService;
import io.harness.ccm.views.service.CEReportTemplateBuilderService;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.rest.RestResponse;

import software.wings.app.MainConfiguration;
import software.wings.graphql.datafetcher.billing.CloudBillingHelper;
import software.wings.persistence.mail.EmailData;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.EmailNotificationServiceImpl;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronSequenceGenerator;
import org.springframework.web.bind.annotation.RequestBody;

@Api("ceReportSchedule")
@Path("/ceReportSchedule")
@Produces("application/json")
@Scope(USER)
@Slf4j
@OwnedBy(HarnessTeam.CE)
public class CEReportScheduleResource {
  private CEReportScheduleService ceReportScheduleService;
  @Inject private CEReportTemplateBuilderService ceReportTemplateBuilderService;
  @Inject private EmailNotificationServiceImpl emailNotificationService;
  @Inject private CloudBillingHelper cloudBillingHelper;
  @Inject private BigQueryService bigQueryService;
  @Inject private MainConfiguration mainConfiguration;

  @Inject
  public CEReportScheduleResource(CEReportScheduleService ceReportScheduleService) {
    this.ceReportScheduleService = ceReportScheduleService;
  }

  @GET
  @Timed
  @Path("{accountId}")
  @ExceptionMetered
  public Response getReportSetting(@QueryParam("viewId") String viewId, @QueryParam("reportId") String reportId,
      @PathParam("accountId") String accountId) {
    if (viewId != null) {
      RestResponse rr =
          new RestResponse<List<CEReportSchedule>>(ceReportScheduleService.getReportSettingByView(viewId, accountId));
      return prepareResponse(rr, Response.Status.OK);
    } else if (reportId != null) {
      List<CEReportSchedule> ceList = new ArrayList<>();
      CEReportSchedule rep = ceReportScheduleService.get(reportId, accountId);
      if (rep != null) {
        ceList.add(rep);
      }
      RestResponse rr = new RestResponse<List<CEReportSchedule>>(ceList);
      return prepareResponse(rr, Response.Status.OK);
    }
    // INVALID_REQUEST
    RestResponse rr = new RestResponse<>();
    addResponseMessage(
        rr, ErrorCode.INVALID_REQUEST, Level.ERROR, "ERROR: Invalid request. Either 'viewId' or 'reportId' is needed");
    return prepareResponse(rr, Response.Status.BAD_REQUEST);
  }

  @DELETE
  @Timed
  @Path("{accountId}")
  @ExceptionMetered
  public Response deleteReportSetting(@QueryParam("reportId") String reportId, @QueryParam("viewId") String viewId,
      @PathParam("accountId") String accountId) {
    if (viewId != null) {
      ceReportScheduleService.deleteAllByView(viewId, accountId);
      RestResponse rr = new RestResponse("Successfully deleted the record");
      return prepareResponse(rr, Response.Status.OK);
    } else if (reportId != null) {
      ceReportScheduleService.delete(reportId, accountId);
      RestResponse rr = new RestResponse("Successfully deleted the record");
      return prepareResponse(rr, Response.Status.OK);
    }
    // INVALID_REQUEST
    RestResponse rr = new RestResponse();
    addResponseMessage(
        rr, ErrorCode.INVALID_REQUEST, Level.ERROR, "ERROR: Invalid request. Either 'viewId' or 'reportId' is needed");
    return prepareResponse(rr, Response.Status.BAD_REQUEST);
  }

  @POST
  @Path("{accountId}")
  @Timed
  @ExceptionMetered
  public Response createReportSetting(
      @PathParam("accountId") String accountId, @Valid @RequestBody CEReportSchedule schedule) {
    List<CEReportSchedule> ceList = new ArrayList<>();
    try {
      CronSequenceGenerator cronSequenceGenerator = new CronSequenceGenerator(schedule.getUserCron());
      ceList.add(ceReportScheduleService.createReportSetting(accountId, schedule));
      RestResponse rr = new RestResponse<List<CEReportSchedule>>(ceList);
      return prepareResponse(rr, Response.Status.OK);
    } catch (IllegalArgumentException e) {
      log.error("ERROR", e);
      RestResponse rr = new RestResponse();
      addResponseMessage(
          rr, ErrorCode.INVALID_REQUEST, Level.ERROR, "ERROR: Invalid request. Schedule provided is invalid");
      return prepareResponse(rr, Response.Status.BAD_REQUEST);
    }
  }

  @PUT
  @Path("{accountId}")
  @Timed
  @ExceptionMetered
  public Response updateReportSetting(
      @PathParam("accountId") String accountId, @Valid @RequestBody CEReportSchedule schedule) {
    try {
      CronSequenceGenerator cronSequenceGenerator = new CronSequenceGenerator(schedule.getUserCron());
      RestResponse rr = new RestResponse<List<CEReportSchedule>>(ceReportScheduleService.update(accountId, schedule));
      return prepareResponse(rr, Response.Status.OK);
    } catch (IllegalArgumentException e) {
      log.warn(String.valueOf(e));
      RestResponse rr = new RestResponse();
      addResponseMessage(
          rr, ErrorCode.INVALID_REQUEST, Level.ERROR, "ERROR: Invalid request. Schedule provided is invalid");
      return prepareResponse(rr, Response.Status.BAD_REQUEST);
    }
  }

  @POST
  @Path("{accountId}/sendReport")
  @Timed
  @ExceptionMetered
  public Response sendReport(
      @PathParam("accountId") String accountId, @QueryParam("viewId") String viewId, List<String> emails) {
    RestResponse rr = new RestResponse();
    if (viewId == null) {
      addResponseMessage(rr, ErrorCode.INVALID_REQUEST, Level.ERROR, "ERROR: Invalid request. ViewId is invalid");
      return prepareResponse(rr, Response.Status.BAD_REQUEST);
    } else if (emails.isEmpty()) {
      addResponseMessage(rr, ErrorCode.INVALID_REQUEST, Level.ERROR, "ERROR: Invalid request. No Recipients Provided");
      return prepareResponse(rr, Response.Status.BAD_REQUEST);
    }
    log.info("Valid viewId and recipients list");
    String cloudProviderTableName = cloudBillingHelper.getCloudProviderTableName(accountId, unified);
    Map<String, String> templatePlaceholders = ceReportTemplateBuilderService.getTemplatePlaceholders(
        accountId, viewId, bigQueryService.get(), cloudProviderTableName, mainConfiguration.getPortal().getUrl());

    EmailData emailData = EmailData.builder()
                              .templateName("ce_scheduled_report")
                              .templateModel(templatePlaceholders)
                              .accountId(accountId)
                              .bcc(emails)
                              .build();
    emailData.setCc(Collections.emptyList());
    emailData.setTo(Collections.emptyList());
    emailData.setRetries(2);

    emailNotificationService.sendCeMail(emailData, true);
    rr = new RestResponse("Successfully sent the report");
    return prepareResponse(rr, Response.Status.OK);
  }

  private static void addResponseMessage(RestResponse rr, ErrorCode errorCode, Level level, String message) {
    ResponseMessage rm = ResponseMessage.builder().code(errorCode).level(level).message(message).build();

    List<ResponseMessage> responseMessages = rr.getResponseMessages();
    responseMessages.add(rm);
    rr.setResponseMessages(responseMessages);
  }

  private Response prepareResponse(RestResponse restResponse, Response.Status status) {
    return Response.status(status).entity(restResponse).type(MediaType.APPLICATION_JSON).build();
  }
}
