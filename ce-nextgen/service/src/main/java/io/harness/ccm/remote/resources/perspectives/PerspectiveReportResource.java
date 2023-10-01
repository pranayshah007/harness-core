/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.perspectives;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.TelemetryConstants.MODULE;
import static io.harness.ccm.TelemetryConstants.MODULE_NAME;
import static io.harness.ccm.TelemetryConstants.REPORT_CREATED;
import static io.harness.ccm.TelemetryConstants.REPORT_TYPE;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.audittrails.events.ReportCreateEvent;
import io.harness.ccm.audittrails.events.ReportDeleteEvent;
import io.harness.ccm.audittrails.events.ReportUpdateEvent;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.entities.CEReportSchedule;
import io.harness.ccm.views.service.CEReportScheduleService;
import io.harness.exception.InvalidRequestException;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Api("perspectiveReport")
@Path("perspectiveReport")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost Perspective Reports", description = "Manage cost reports created on Perspectives.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class PerspectiveReportResource {
  private final TelemetryReporter telemetryReporter;
  private final CEReportScheduleService ceReportScheduleService;
  private static final String accountIdPathParam = "{" + NGCommonEntityConstants.ACCOUNT_KEY + "}";

  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;

  @Inject
  public PerspectiveReportResource(CEReportScheduleService ceReportScheduleService, TelemetryReporter telemetryReporter,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, OutboxService outboxService) {
    this.ceReportScheduleService = ceReportScheduleService;
    this.telemetryReporter = telemetryReporter;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
  }

  @GET
  @Timed
  @Path(accountIdPathParam)
  @ExceptionMetered
  @LogAccountIdentifier
  @ApiOperation(value = "Get perspective reports", nickname = "getReportSetting")
  @Operation(operationId = "getReportSetting",
      description = "Fetch cost Report details for the given Report ID or a Perspective ID.",
      summary = "Fetch details of a cost Report",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns a list of Report Schedules",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<CEReportSchedule>>
  getReportSetting(@Parameter(description = "Unique identifier for the Perspective") @QueryParam(
                       "perspectiveId") String perspectiveId,
      @Parameter(description = "Unique identifier for the Report") @QueryParam("reportId") String reportId,
      @Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @PathParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Valid @NotNull String accountId) {
    if (perspectiveId != null) {
      return ResponseDTO.newResponse(ceReportScheduleService.getReportSettingByView(perspectiveId, accountId));
    } else if (reportId != null) {
      List<CEReportSchedule> ceList = new ArrayList<>();
      CEReportSchedule rep = ceReportScheduleService.get(reportId, accountId);
      if (rep != null) {
        ceList.add(rep);
      }
      return ResponseDTO.newResponse(ceList);
    }

    throw new InvalidRequestException("Either 'viewId' or 'reportId' is needed");
  }

  @DELETE
  @Timed
  @Path(accountIdPathParam)
  @ExceptionMetered
  @LogAccountIdentifier
  @ApiOperation(value = "Delete perspective reports", nickname = "deleteReportSetting")
  @Operation(operationId = "deleteReportSetting",
      description = "Delete cost Perspective Report for the given Report ID or a Perspective ID.",
      summary = "Delete cost Perspective report",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns a generic string message when the operation is successful",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<String>
  deleteReportSetting(
      @Parameter(description = "Unique identifier for the Report") @QueryParam("reportId") String reportId,
      @Parameter(description = "Unique identifier for the Perspective") @QueryParam(
          "perspectiveId") String perspectiveId,
      @Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @PathParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Valid @NotNull String accountId) {
    final String deleteSuccessfulMsg = "Successfully deleted the record";

    if (perspectiveId != null) {
      return ResponseDTO.newResponse(
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            List<CEReportSchedule> ceList = ceReportScheduleService.getReportSettingByView(perspectiveId, accountId);
            for (CEReportSchedule report : ceList) {
              outboxService.save(new ReportDeleteEvent(accountId, report.toDTO()));
            }
            ceReportScheduleService.deleteAllByView(perspectiveId, accountId);
            return deleteSuccessfulMsg;
          })));
    } else if (reportId != null) {
      CEReportSchedule report = ceReportScheduleService.get(reportId, accountId).toDTO();
      ceReportScheduleService.delete(reportId, accountId);
      return ResponseDTO.newResponse(
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            outboxService.save(new ReportDeleteEvent(accountId, report.toDTO()));
            return deleteSuccessfulMsg;
          })));
    }

    throw new InvalidRequestException("Either 'perspectiveId' or 'reportId' is needed");
  }

  @POST
  @Path(accountIdPathParam)
  @Timed
  @ExceptionMetered
  @LogAccountIdentifier
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Create perspective reports", nickname = "createReportSetting")
  @Operation(operationId = "createReportSetting",
      description = "Create a report schedule for the given Report ID or a Perspective ID.",
      summary = "Create a schedule for a Report",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns a list of Report Schedules",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<CEReportSchedule>>
  createReportSetting(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @PathParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Valid @NotNull String accountId,
      @NotNull @Valid @RequestBody(
          required = true, description = "CEReportSchedule object to be saved") CEReportSchedule schedule) {
    List<CEReportSchedule> ceList = new ArrayList<>();
    try {
      ceList.add(ceReportScheduleService.createReportSetting(accountId, schedule));
      HashMap<String, Object> properties = new HashMap<>();
      properties.put(MODULE, MODULE_NAME);
      String cronValue[] = schedule.getUserCron().split(" ");
      String reportType = "Monthly";
      if (!cronValue[4].equals("*")) {
        reportType = "Yearly";
      } else if (cronValue[3].equals("?")) {
        reportType = "Weekly";
      } else if (cronValue[3].equals("*")) {
        reportType = "Daily";
      }
      properties.put(REPORT_TYPE, reportType);
      telemetryReporter.sendTrackEvent(
          REPORT_CREATED, null, accountId, properties, Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
      return ResponseDTO.newResponse(
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            outboxService.save(new ReportCreateEvent(accountId, schedule.toDTO()));
            return ceList;
          })));
    } catch (IllegalArgumentException e) {
      log.error("ERROR", e);

      throw new InvalidRequestException("Schedule provided is invalid");
    }
  }

  @PUT
  @Path(accountIdPathParam)
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Update perspective reports", nickname = "updateReportSetting")
  @Operation(operationId = "updateReportSetting", description = "Update cost Perspective Reports.",
      summary = "Update a cost Perspective Report",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns a list of Report Schedules",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<CEReportSchedule>>
  updateReportSetting(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @PathParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @Valid @RequestBody(
          required = true, description = "CEReportSchedule object to be updated") CEReportSchedule schedule) {
    try {
      CEReportSchedule oldReport = ceReportScheduleService.get(schedule.getUuid(), accountId);
      List<CEReportSchedule> reports = ceReportScheduleService.update(accountId, schedule);
      CEReportSchedule newReport = ceReportScheduleService.get(schedule.getUuid(), accountId);
      return ResponseDTO.newResponse(
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            outboxService.save(new ReportUpdateEvent(accountId, newReport.toDTO(), oldReport.toDTO()));
            return reports;
          })));
    } catch (IllegalArgumentException e) {
      log.warn(String.valueOf(e));

      throw new InvalidRequestException("Schedule provided is invalid");
    }
  }
}
