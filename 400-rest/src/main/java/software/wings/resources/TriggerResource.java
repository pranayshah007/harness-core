/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.security.PermissionAttribute.ResourceType.APPLICATION;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.WorkflowType;
import io.harness.exception.WingsException;
import io.harness.rest.RestResponse;

import software.wings.beans.WebHookToken;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.WebhookEventType;
import software.wings.beans.trigger.WebhookParameters;
import software.wings.beans.trigger.WebhookSource;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.TriggerService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by sgurubelli on 10/26/17.
 */
@Api("triggers")
@Path("/triggers")
@Produces("application/json")
@Consumes("application/json")
@Slf4j
@Scope(APPLICATION)
public class TriggerResource {
  private TriggerService triggerService;

  @Inject
  public TriggerResource(TriggerService triggerService) {
    this.triggerService = triggerService;
  }

  /**
   * @param appIds
   * @param pageRequest
   * @return
   */
  @GET
  @Timed
  @ExceptionMetered
  public RestResponse<PageResponse<Trigger>> list(@QueryParam("appId") List<String> appIds,
      @QueryParam("tagFilter") String tagFilter, @QueryParam("withTags") @DefaultValue("false") boolean withTags,
      @BeanParam PageRequest<Trigger> pageRequest) {
    if (isNotEmpty(appIds)) {
      triggerService.authorizeAppAccess(appIds);
      pageRequest.addFilter("appId", IN, appIds.toArray());
    }
    return new RestResponse<>(triggerService.list(pageRequest, withTags, tagFilter));
  }

  /**
   * Gets the.
   *
   * @param appId    the app id
   * @param triggerId the stream id
   * @return the rest response
   */
  @GET
  @Path("{triggerId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Trigger> get(@QueryParam("appId") String appId, @PathParam("triggerId") String triggerId) {
    triggerService.authorizeAppAccess(Collections.singletonList(appId));
    return new RestResponse<>(triggerService.get(appId, triggerId));
  }

  /**
   * Save rest response.
   *
   * @param appId   the app id
   * @param trigger the artifact stream
   * @return the rest response
   */
  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<Trigger> save(@QueryParam("appId") String appId, Trigger trigger) {
    notNullCheck("trigger", trigger);
    trigger.setAppId(appId);
    if (trigger.getUuid() != null) {
      Trigger existingTrigger = triggerService.get(appId, trigger.getUuid());
      if (existingTrigger == null) {
        throw new WingsException("Trigger does not exist", USER);
      }
      triggerService.authorize(existingTrigger, true);
      triggerService.authorize(trigger, false);
      return new RestResponse(triggerService.update(trigger, false));
    }
    triggerService.authorize(trigger, false);
    return new RestResponse<>(triggerService.save(trigger));
  }

  /**
   * Update rest response.
   *
   * @param appId    the app id
   * @param triggerId the stream id
   * @param trigger  the artifact stream
   * @return the rest response
   */
  @PUT
  @Path("{triggerId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Trigger> update(
      @QueryParam("appId") String appId, @PathParam("triggerId") String triggerId, Trigger trigger) {
    trigger.setUuid(triggerId);
    trigger.setAppId(appId);
    Trigger existingTrigger = triggerService.get(appId, trigger.getUuid());
    if (existingTrigger == null) {
      throw new WingsException("Trigger doesn't exist", USER);
    }
    triggerService.authorize(existingTrigger, true);
    triggerService.authorize(trigger, false);
    return new RestResponse<>(triggerService.update(trigger, false));
  }

  /**
   * Delete.
   *
   * @param appId the app id
   * @param triggerId    the id
   * @return the rest response
   */
  @DELETE
  @Path("{triggerId}")
  @Timed
  @ExceptionMetered
  public RestResponse delete(@QueryParam("appId") String appId, @PathParam("triggerId") String triggerId) {
    Trigger trigger = triggerService.get(appId, triggerId);
    if (trigger != null) {
      if (triggerService.triggerActionExists(trigger)) {
        triggerService.authorize(trigger, true);
      }
      triggerService.delete(appId, triggerId);
    }
    return new RestResponse<>();
  }

  @GET
  @Path("{triggerId}/webhook_token")
  @Timed
  @ExceptionMetered
  public RestResponse<WebHookToken> generateWebhookToken(
      @QueryParam("appId") String appId, @PathParam("triggerId") String triggerId) {
    return new RestResponse<>(triggerService.generateWebHookToken(appId, triggerId));
  }

  @GET
  @Path("{triggerId}/webhook_token/git")
  @Timed
  @ExceptionMetered
  public RestResponse<WebHookToken> generateGitWebhookToken(
      @QueryParam("appId") String appId, @PathParam("triggerId") String triggerId) {
    return new RestResponse<>(triggerService.generateWebHookToken(appId, triggerId));
  }

  /**
   * Translate cron rest response.
   *
   * @param inputMap the input map
   * @return the rest response
   */
  @POST
  @Path("cron/translate")
  @Timed
  @ExceptionMetered
  public RestResponse<String> translateCron(Map<String, String> inputMap) {
    return new RestResponse<>(triggerService.getCronDescription(inputMap.get("expression")));
  }

  @GET
  @Path("webhook/parameters")
  @Timed
  @ExceptionMetered
  public RestResponse<WebhookParameters> listWebhookParameters(@QueryParam("appId") String appId,
      @QueryParam("workflowId") String workflowId, @QueryParam("workflowType") WorkflowType workflowType,
      @QueryParam("webhookSource") WebhookSource webhookSource, @QueryParam("eventType") String eventType) {
    return new RestResponse<>(triggerService.listWebhookParameters(
        appId, workflowId, workflowType, webhookSource, WebhookEventType.fromString(eventType)));
  }

  @GET
  @Path("webhook/eventTypes")
  @Timed
  @ExceptionMetered
  public RestResponse<WebhookEventType> listWebhookEventTypes(@QueryParam("appId") String appId) {
    return new RestResponse<>();
  }
}
