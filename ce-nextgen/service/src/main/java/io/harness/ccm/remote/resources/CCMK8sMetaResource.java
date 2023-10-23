/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.dao.ClusterRecordDao;
import io.harness.ccm.commons.entities.ClusterRecord;
import io.harness.ccm.health.LastReceivedPublishedMessageDao;
import io.harness.ccm.service.impl.K8sConnectorHelper;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.dto.CcmK8sMetaDTO;
import io.harness.ccm.views.dto.CcmK8sMetaInfo;
import io.harness.ccm.views.dto.CcmK8sMetaInfoResponseDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("ccmK8sMeta")
@Path("/ccmK8sMeta")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost K8S Connectors Metadata",
    description = "Health related metadata for your k8S clusters having cost access enabled.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class CCMK8sMetaResource {
  @Inject private LastReceivedPublishedMessageDao lastReceivedPublishedMessageDao;
  @Inject private ClusterRecordDao clusterRecordDao;
  @Inject private K8sConnectorHelper k8sConnectorHelper;

  private static final Long EVENT_TIMESTAMP_RECENCY_THRESHOLD_FOR_K8S =
      TimeUnit.MILLISECONDS.convert(30, TimeUnit.MINUTES);

  @POST
  @Timed
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @LogAccountIdentifier
  @ApiOperation(value = "CCM K8S Metadata", nickname = "CCM K8S Metadata")
  @Operation(operationId = "ccmK8sMeta", description = "Get CCM K8S Metadata ", summary = "Get CCM K8S Metadata",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns list of ConnectorDisconnectHandler identifiers with their health metadata",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<CcmK8sMetaInfoResponseDTO>
  ccmK8sMeta(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true,
          description = "Request body containing Cost Access K8s ConnectorDisconnectHandler identifiers") CcmK8sMetaDTO ccmK8sMetaDTO) {
    log.info("In request {}", ccmK8sMetaDTO.toString());
    Map<String, Long> clusterLastReceivedTime;
    Map<String, ClusterRecord> clusterIdCcmConnRefMap = new HashMap<>();
    List<CcmK8sMetaInfo> ccmK8sMetaList = new ArrayList<>();
    if (ccmK8sMetaDTO.getCcmK8sConnectorId().size() != 0) {
      List<ClusterRecord> clusterRecords =
          clusterRecordDao.getByCEK8sIdentifierList(accountId, ccmK8sMetaDTO.getCcmK8sConnectorId());
      log.info("clusterRecords: {}", clusterRecords);
      for (ClusterRecord clusterRecord : clusterRecords) {
        clusterIdCcmConnRefMap.put(clusterRecord.getUuid(), clusterRecord);
      }
      log.info("clusterIdCcmConnRefMap: {}", clusterIdCcmConnRefMap);
      clusterLastReceivedTime = lastReceivedPublishedMessageDao.getLastReceivedTimeForClusters(
          accountId, new ArrayList<>(clusterIdCcmConnRefMap.keySet()));
      log.info("clusterLastReceivedTime : {}", clusterLastReceivedTime.toString());

      DateFormat format = new SimpleDateFormat("E, dd MMMM yyyy HH:mm:ss z");
      format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));

      for (Map.Entry<String, ClusterRecord> clusterConn : clusterIdCcmConnRefMap.entrySet()) {
        List<String> errors = new ArrayList<>();
        if (!clusterLastReceivedTime.containsKey(clusterConn.getKey())) {
          log.info("clusterId = " + clusterConn.getKey() + ", No events received");
          errors.add("No events received. It typically takes 3 to 5 minutes to start receiving events.");
        } else {
          String eventDate = format.format(new Date(clusterLastReceivedTime.get(clusterConn.getKey())));
          log.info("clusterId = " + clusterConn.getKey() + ", lastReceivedAt = " + eventDate);
          if ((Instant.now().toEpochMilli() - clusterLastReceivedTime.get(clusterConn.getKey()))
              > EVENT_TIMESTAMP_RECENCY_THRESHOLD_FOR_K8S) {
            // find out errors from exception record in this case
            errors = k8sConnectorHelper.getErrors(clusterConn.getValue());
            errors.add("The cluster " + clusterConn.getValue().getClusterName() + " has not published events since "
                + eventDate);
          } else {
            errors.add("last event received at " + eventDate);
          }
        }

        CcmK8sMetaInfo ccmK8sMetaInfo = CcmK8sMetaInfo.builder()
                                            .clusterId(clusterConn.getKey())
                                            .ccmk8sConnectorId(clusterConn.getValue().getCeK8sConnectorIdentifier())
                                            .clusterName(clusterConn.getValue().getClusterName())
                                            .visibility(errors)
                                            // TODO: Add Autostopping related meta info here
                                            .build();
        ccmK8sMetaList.add(ccmK8sMetaInfo);
      }
    }
    log.info("ccmK8sMetaList: {}", ccmK8sMetaList);
    CcmK8sMetaInfoResponseDTO ccmK8sMetaInfoResponseDTO =
        CcmK8sMetaInfoResponseDTO.builder().ccmK8sMeta(ccmK8sMetaList).build();
    return ResponseDTO.newResponse(ccmK8sMetaInfoResponseDTO);
  }
}
