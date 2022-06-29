/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.slack;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.security.JWT_CATEGORY.EXTERNAL_SERVICE_SECRET;
import static software.wings.sm.states.ApprovalState.JSON;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidArgumentsException;
import io.harness.rest.RestResponse;
import io.harness.serializer.JsonUtils;

import software.wings.api.ApprovalStateExecutionData;
import software.wings.beans.ApprovalDetails;
import software.wings.beans.approval.SlackApprovalParams;
import software.wings.security.SecretManager;
import software.wings.service.impl.notifications.SlackApprovalMessageKeys;
import software.wings.service.intfc.WorkflowExecutionService;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.text.StrSubstitutor;
import org.json.JSONObject;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Slf4j
@Singleton
public class SlackApprovalUtils {
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private ConfirmationHandler confirmationHandler;
  @Inject private ProceedResponseHandler proceedResponseHandler;
  @Inject private RevertResponseHandler revertResponseHandler;
  @Inject private SecretManager secretManager;

  public RestResponse<Boolean> slackApprovalHandler(MultivaluedMap<String, String> body) throws IOException {
    log.info("SLACK REQUEST");

    ObjectMapper mapper = new ObjectMapper();
    Map<String, String> slackUserDetails =
        (Map<String, String>) mapper.readValue(body.getFirst("payload"), Map.class).get("user");
    Map<String, String> action =
        ((ArrayList<Map>) mapper.readValue(body.getFirst("payload"), Map.class).get("actions")).get(0);

    String initialApprovalMessage =
        getInitialMessage(body).orElseThrow(() -> new InvalidArgumentsException("Failed to parse Slack response"));

    // Slack Details
    String actionType = action.get("action_id");
    String responseUrl = (String) mapper.readValue(body.getFirst("payload"), Map.class).get("response_url");
    SlackApprovalParams.External slackApprovalParams =
        mapper.readValue(action.get("value"), SlackApprovalParams.External.class);
    final SlackApprovalParams.External approvalParams = slackApprovalParams.toBuilder()
                                                            .actionType(actionType)
                                                            .slackUsername(slackUserDetails.get("name"))
                                                            .slackUserId(slackUserDetails.get("id"))
                                                            .build();

    URL sessionTimeoutTemplateUrl =
        this.getClass().getResource(SlackApprovalMessageKeys.APPROVAL_EXPIRED_MESSAGE_TEMPLATE);
    Map<String, String> sessionTimeoutTemplateFillers = new HashMap<>();
    sessionTimeoutTemplateFillers.put(
        SlackApprovalMessageKeys.WORKFLOW_EXECUTION_NAME, approvalParams.getWorkflowExecutionName());
    sessionTimeoutTemplateFillers.put(SlackApprovalMessageKeys.APP_NAME, approvalParams.getNonFormattedAppName());
    String sessionTimedOutMessage = createMessageFromTemplate(sessionTimeoutTemplateUrl, sessionTimeoutTemplateFillers);

    if (!approvalParams.isConfirmation()) {
      return confirmationHandler.handle(approvalParams, initialApprovalMessage, sessionTimedOutMessage, responseUrl);
    } else {
      // Confirmation Response : Proceed
      if (actionType.equals(SlackApprovalMessageKeys.BUTTON_PROCEED)) {
        return proceedResponseHandler.handle(
            approvalParams, initialApprovalMessage, sessionTimedOutMessage, responseUrl);
      } else {
        // Confirmation Response : Revert
        return revertResponseHandler.handle(
            approvalParams, initialApprovalMessage, sessionTimedOutMessage, responseUrl);
      }
    }
  }

  private Optional<String> getInitialMessage(MultivaluedMap<String, String> body) {
    // noinspection unchecked,rawtypes
    return ((List) ((Map<String, Object>) JsonUtils.asObject(body.getFirst("payload"), Map.class).get("message"))
                .get("blocks"))
        .stream()
        .filter(map -> ((Map) map).get("type").equals("section"))
        .map(map -> ((Map) map).get("text"))
        .map(map -> ((Map) map).get("text"))
        .findFirst();
  }

  public boolean verifyJwtToken(SlackApprovalParams.External slackApprovalParams) throws UnsupportedEncodingException {
    Algorithm algorithm = Algorithm.HMAC256(secretManager.getJWTSecret(EXTERNAL_SERVICE_SECRET));
    JWTVerifier verifier = JWT.require(algorithm)
                               .withIssuer("Harness Inc")
                               .withClaim("approvalId", slackApprovalParams.getApprovalId())
                               .build();
    try {
      verifier.verify(slackApprovalParams.getJwtToken());
      return true;
    } catch (JWTVerificationException e) {
      return false;
    }
  }

  public static String createSlackApprovalMessage(SlackApprovalParams slackApprovalParams, URL url) {
    Map<String, String> templateFillers = new HashMap<>();
    templateFillers.put(SlackApprovalMessageKeys.PAUSED_STAGE_NAME, slackApprovalParams.getPausedStageName());
    templateFillers.put(SlackApprovalMessageKeys.WORKFLOW_URL, slackApprovalParams.getWorkflowUrl());
    templateFillers.put(
        SlackApprovalMessageKeys.WORKFLOW_EXECUTION_NAME, slackApprovalParams.getWorkflowExecutionName());
    templateFillers.put(SlackApprovalMessageKeys.APP_NAME, slackApprovalParams.getAppName());
    templateFillers.put(SlackApprovalMessageKeys.SERVICES, slackApprovalParams.getServicesInvolved());
    templateFillers.put(SlackApprovalMessageKeys.ENVIRONMENTS, slackApprovalParams.getEnvironmentsInvolved());
    templateFillers.put(
        SlackApprovalMessageKeys.INFRASTRUCTURE_DEFINITIONS, slackApprovalParams.getInfraDefinitionsInvolved());
    templateFillers.put(SlackApprovalMessageKeys.START_TS_SECS, slackApprovalParams.getStartTsSecs());
    templateFillers.put(SlackApprovalMessageKeys.END_TS_SECS, slackApprovalParams.getEndTsSecs());
    templateFillers.put(SlackApprovalMessageKeys.EXPIRES_TS_SECS, slackApprovalParams.getExpiryTsSecs());
    templateFillers.put(SlackApprovalMessageKeys.START_DATE, slackApprovalParams.getStartDate());
    templateFillers.put(SlackApprovalMessageKeys.END_DATE, slackApprovalParams.getEndDate());
    templateFillers.put(SlackApprovalMessageKeys.EXPIRES_DATE, slackApprovalParams.getExpiryTsSecs());
    templateFillers.put(SlackApprovalMessageKeys.VERB, slackApprovalParams.getVerb());
    templateFillers.put(SlackApprovalMessageKeys.ARTIFACTS, slackApprovalParams.getArtifactsInvolved());
    templateFillers.put(SlackApprovalMessageKeys.TRIGGERED_BY, slackApprovalParams.getTriggeredByUser());

    return createMessageFromTemplate(url, templateFillers);
  }

  public static RequestBody createBody(String bodyText, boolean replaceOriginal) {
    JSONObject jsonPayload = new JSONObject();
    if (replaceOriginal) {
      jsonPayload.put("replace_original", "true");
    }
    jsonPayload.put("text", bodyText);
    String payload = jsonPayload.toString();
    return RequestBody.create(JSON, payload);
  }

  public static String createMessageFromTemplate(URL templateUrl, Map<String, String> templateFillers) {
    String loadedTemplate = "";
    StrSubstitutor sub = new StrSubstitutor(templateFillers);
    try {
      loadedTemplate = Resources.toString(templateUrl, Charsets.UTF_8);
    } catch (IOException e) {
      log.error("Error in loading given template");
    }
    return sub.replace(loadedTemplate)
        .replaceAll("<<<", "*<")
        .replaceAll("\\|-\\|", "|")
        .replaceAll(">>>", ">*")
        .replaceAll("\\\\n", "\n")
        .replaceAll("\\\\\\*", "*")
        .replaceAll("\\*<\\|>\\*", "");
  }

  public static RestResponse<Boolean> slackPostRequest(RequestBody body, String responseUrl) throws IOException {
    OkHttpClient client = new OkHttpClient();
    Request request1 = new Request.Builder()
                           .url(responseUrl)
                           .post(body)
                           .addHeader("Content-Type", "application/json")
                           .addHeader("Accept", "*/*")
                           .addHeader("Cache-Control", "no-cache")
                           .addHeader("Host", "hooks.slack.com")
                           .addHeader("accept-encoding", "gzip, deflate")
                           .addHeader("content-length", "798")
                           .addHeader("Connection", "keep-alive")
                           .addHeader("cache-control", "no-cache")
                           .build();

    try (Response response = client.newCall(request1).execute()) {
      if (!response.isSuccessful()) {
        log.error("Slack post request failed with code: {}", response.code());
      }
    }
    return new RestResponse<>(true);
  }

  public static boolean approve(String appId, String workflowExecutionId, String stateExecutionId,
      ApprovalDetails approvalDetails, WorkflowExecutionService workflowExecutionService) {
    ApprovalStateExecutionData approvalStateExecutionData =
        workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
            appId, workflowExecutionId, stateExecutionId, approvalDetails);

    return workflowExecutionService.approveOrRejectExecution(
        appId, approvalStateExecutionData.getUserGroups(), approvalDetails, (String) null);
  }

  public static String resetToInitialMessage(String decoratedMessage) {
    return decoratedMessage.replace("\nAre you sure you want to *Reject* ?", "")
        .replace("\nAre you sure you want to *Approve* ?", "")
        .replace("\n*Approval Pending*, would you like to _approve_?", "");
  }
}
