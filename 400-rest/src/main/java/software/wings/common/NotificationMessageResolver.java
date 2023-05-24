/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.common;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.govern.Switch.unhandled;
import static io.harness.logging.Misc.getDurationString;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.context.ContextElementType;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.sanitizer.HtmlInputSanitizer;
import io.harness.serializer.YamlUtils;

import software.wings.app.MainConfiguration;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.alert.AlertType;
import software.wings.common.NotificationMessageResolver.ChannelTemplate.EmailTemplate;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.WorkflowStandardParams;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StrSubstitutor;
import org.apache.commons.text.WordUtils;
import org.apache.http.client.utils.URIBuilder;

/**
 * Created by anubhaw on 7/25/16.
 */
@Singleton
@Slf4j
@TargetModule(HarnessModule._830_NOTIFICATION_SERVICE)
public class NotificationMessageResolver {
  private Map<String, ChannelTemplate> templateMap;

  @Inject private MainConfiguration configuration;
  @Inject private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Inject private HtmlInputSanitizer userNameSanitizer;
  private static final String FAILED_STATUS = "failed";
  private final DateFormat dateFormat = new SimpleDateFormat("MMM d");
  private final DateFormat timeFormat = new SimpleDateFormat("HH:mm z");

  /**
   * The enum Notification message type.
   */
  public enum NotificationMessageType {
    ENTITY_CREATE_NOTIFICATION,
    ENTITY_DELETE_NOTIFICATION,
    ARTIFACT_APPROVAL_NOTIFICATION,
    ARTIFACT_APPROVAL_NOTIFICATION_STATUS,
    WORKFLOW_NOTIFICATION,
    PIPELINE_NOTIFICATION,
    DELEGATE_STATE_NOTIFICATION,
    SSO_PROVIDER_NOT_REACHABLE_NOTIFICATION,
    ALL_DELEGATE_DOWN_NOTIFICATION,
    APPROVAL_NEEDED_NOTIFICATION,
    APPROVAL_STATE_CHANGE_NOTIFICATION,
    APPROVAL_EXPIRED_NOTIFICATION,
    APPROVAL_EXPIRED_WORKFLOW_NOTIFICATION,
    MANUAL_INTERVENTION_NEEDED_NOTIFICATION,
    NEEDS_RUNTIME_INPUTS,
    RUNTIME_INPUTS_PROVIDED,
    RESOURCE_CONSTRAINT_BLOCKED_NOTIFICATION,
    RESOURCE_CONSTRAINT_UNBLOCKED_NOTIFICATION,
    DELEGATE_DOWN_ALERT_NOTIFICATION,
    DELEGATE_SCALING_GROUP_DOWN_ALERT_NOTIFICATION,
    GENERIC_ALERT_NOTIFICATION,
    CV_SERVICE_GUARD_NOTIFICATION,
    USER_LOCKED_NOTIFICATION,
    BUDGET_NOTIFICATION,
    WORKFLOW_PAUSE_NOTIFICATION,
    WORKFLOW_RESUME_NOTIFICATION,
    WORKFLOW_ABORT_NOTIFICATION,
    EXPORT_EXECUTIONS_READY_NOTIFICATION,
    EXPORT_EXECUTIONS_FAILED_NOTIFICATION,
    PIPELINE_FREEZE_NOTIFICATION,
    TRIGGER_EXECUTION_REJECTED_NOTIFICATION,
    FREEZE_ACTIVATION_NOTIFICATION,
    FREEZE_DEACTIVATION_NOTIFICATION
  }

  private static Pattern placeHolderPattern = Pattern.compile("\\$\\{.+?}");

  /**
   * Gets decorated notification message.
   *
   * @param templateText the template text
   * @param params       the params
   * @return the decorated notification message
   */
  public static String getDecoratedNotificationMessage(String templateText, Map<String, String> params) {
    if (templateText == null) {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT, "Template text can not be null", WingsException.USER)
          .addParam("args", "Template text can not be null");
    }
    templateText = StrSubstitutor.replace(templateText, params);
    validate(templateText);
    return templateText;
  }

  private static void validate(String templateText) {
    if (placeHolderPattern.matcher(templateText).find()) {
      String errorMsg = new StringBuilder(128)
                            .append("Incomplete placeholder replacement for templateText: ")
                            .append(templateText)
                            .toString();
      throw new WingsException(ErrorCode.INVALID_ARGUMENT, errorMsg, WingsException.USER).addParam("args", errorMsg);
    }
  }

  /**
   * Instantiates a new Notification message resolver.
   *
   * @param yamlUtils the yaml utils
   */
  @Inject
  public NotificationMessageResolver(YamlUtils yamlUtils) {
    try {
      URL url = this.getClass().getResource("/notificationtemplates/notification_templates.yml");
      String yaml = Resources.toString(url, UTF_8);
      templateMap = yamlUtils.read(yaml, new TypeReference<Map<String, ChannelTemplate>>() {});
    } catch (Exception e) {
      throw new WingsException(e);
    }
  }

  /**
   * Gets slack template.
   *
   * @param templateName the template name
   * @return the slack template
   */
  public String getSlackTemplate(String templateName) {
    return templateMap.getOrDefault(templateName, new ChannelTemplate()).getSlack();
  }

  /**
   * Gets pagerDuty template.
   *
   * @param templateName the template name
   * @return the pagerDuty template
   */
  public PagerDutyTemplate getPagerDutyTemplate(String templateName) {
    return templateMap.getOrDefault(templateName, new ChannelTemplate()).getPagerDuty();
  }

  /**
   * Gets web template.
   *
   * @param templateName the template name
   * @return the web template
   */
  public String getWebTemplate(String templateName) {
    return templateMap.getOrDefault(templateName, new ChannelTemplate()).getWeb();
  }

  /**
   * Gets email template.
   *
   * @param templateName the template name
   * @return the email template
   */
  public EmailTemplate getEmailTemplate(String templateName) {
    return templateMap.getOrDefault(templateName, new ChannelTemplate()).getEmail();
  }

  /**
   * The type Channel template.
   */
  public static class ChannelTemplate {
    private String web;
    private String slack;
    private EmailTemplate email;
    private PagerDutyTemplate pagerDuty;

    /**
     * Gets web.
     *
     * @return the web
     */
    public String getWeb() {
      return web;
    }

    /**
     * Sets web.
     *
     * @param web the web
     */
    public void setWeb(String web) {
      this.web = web;
    }

    /**
     * Gets pagerDuty.
     *
     * @return the pagerDuty
     */
    public PagerDutyTemplate getPagerDuty() {
      return pagerDuty;
    }

    /**
     * Sets pagerDuty.
     *
     * @param pagerDuty the pagerDuty
     */
    public void setPagerDuty(PagerDutyTemplate pagerDuty) {
      this.pagerDuty = pagerDuty;
    }

    /**
     * Gets slack.
     *
     * @return the slack
     */
    public String getSlack() {
      return slack;
    }

    /**
     * Sets slack.
     *
     * @param slack the slack
     */
    public void setSlack(String slack) {
      this.slack = slack;
    }

    /**
     * Gets email.
     *
     * @return the email
     */
    public EmailTemplate getEmail() {
      return email;
    }

    /**
     * Sets email.
     *
     * @param email the email
     */
    public void setEmail(EmailTemplate email) {
      this.email = email;
    }

    /**
     * The type Email template.
     */
    public static class EmailTemplate {
      private String subject;
      private String body;

      /**
       * Gets subject.
       *
       * @return the subject
       */
      public String getSubject() {
        return subject;
      }

      /**
       * Sets subject.
       *
       * @param subject the subject
       */
      public void setSubject(String subject) {
        this.subject = subject;
      }

      /**
       * Gets body.
       *
       * @return the body
       */
      public String getBody() {
        return body;
      }

      /**
       * Sets body.
       *
       * @param body the body
       */
      public void setBody(String body) {
        this.body = body;
      }
    }
  }

  @Getter
  @Setter
  public static class Link {
    private String href;
    private String text;
  }

  @Getter
  @Setter
  public static class PagerDutyTemplate {
    private String summary;
    private Link link;
  }

  public static String getStatusVerb(ExecutionStatus status) {
    switch (status) {
      case SUCCESS:
        return "completed";
      case FAILED:
      case ERROR:
        return FAILED_STATUS;
      case PAUSED:
        return "paused";
      case RESUMED:
        return "resumed";
      case ABORTED:
        return "aborted";
      case REJECTED:
        return "rejected";
      case EXPIRED:
        return "expired";
      default:
        unhandled(status);
        return FAILED_STATUS;
    }
  }

  public static String buildAbsoluteUrl(String fragment, String baseUrl) {
    if (!baseUrl.endsWith("/")) {
      baseUrl += "/";
    }
    try {
      URIBuilder uriBuilder = new URIBuilder(baseUrl);
      uriBuilder.setFragment(fragment);
      return uriBuilder.toString();
    } catch (URISyntaxException e) {
      log.error("Bad URI syntax", e);
      return baseUrl;
    }
  }

  private String generateUrl(Application app, ExecutionContext context, AlertType alertType) {
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(context.getAccountId());
    if (alertType == AlertType.ApprovalNeeded || alertType == AlertType.ManualInterventionNeeded
        || alertType == AlertType.DEPLOYMENT_FREEZE_EVENT) {
      if (context.getWorkflowType() == WorkflowType.PIPELINE) {
        return buildAbsoluteUrl(format("/account/%s/app/%s/pipeline-execution/%s/workflow-execution/undefined/details",
                                    app.getAccountId(), app.getUuid(), context.getWorkflowExecutionId()),
            baseUrl);
      } else if (context.getWorkflowType() == WorkflowType.ORCHESTRATION) {
        WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
        String pipelineExecutionId = null;
        if (workflowStandardParams != null && workflowStandardParams.getWorkflowElement() != null) {
          pipelineExecutionId = workflowStandardParams.getWorkflowElement().getPipelineDeploymentUuid();
        }

        if (isEmpty(pipelineExecutionId)) {
          String envId = "empty";
          // Direct WF execution
          if (((ExecutionContextImpl) context).getEnv() != null) {
            envId = ((ExecutionContextImpl) context).getEnv().getUuid();
          }
          return buildAbsoluteUrl(format("/account/%s/app/%s/env/%s/executions/%s/details", app.getAccountId(),
                                      app.getUuid(), envId, context.getWorkflowExecutionId()),
              baseUrl);
        } else {
          // WF in a Pipeline execution
          return buildAbsoluteUrl(
              format("/account/%s/app/%s/pipeline-execution/%s/workflow-execution/%s/details", app.getAccountId(),
                  app.getUuid(), pipelineExecutionId, context.getWorkflowExecutionId()),
              baseUrl);
        }
      } else {
        log.error("Unhandled Approval case. No URL can be generated for alertType ", alertType.name());
        return "";
      }
    } else {
      log.warn("Unhandled case. No URL can be generated for alertType ", alertType.name());
      return "";
    }
  }

  private String toCamelCase(String input) {
    return WordUtils.capitalizeFully(input);
  }

  public String getApprovalType(WorkflowType workflowType) {
    if (workflowType == WorkflowType.PIPELINE) {
      return toCamelCase(WorkflowType.PIPELINE.name());
    } else if (workflowType == WorkflowType.ORCHESTRATION) {
      return "Workflow";
    }
    return "";
  }

  public Map<String, String> getPlaceholderValues(ExecutionContext context, String userName, long startTs, long endTs,
      String timeout, String statusMsg, String artifactsMessage, ExecutionStatus status, AlertType alertType) {
    Application app = ((ExecutionContextImpl) context).getApp();
    String workflowUrl = (app == null) ? null : generateUrl(app, context, alertType);
    long expiresTs = endTs;

    if (StringUtils.isNumeric(timeout)) {
      expiresTs += parseLong(timeout);
    }

    String startTime = format("%s at %s", dateFormat.format(new Date(startTs)), timeFormat.format(new Date(startTs)));
    String endTime = format("%s at %s", dateFormat.format(new Date(endTs)), timeFormat.format(new Date(endTs)));
    String expiresTime = getFormattedExpiresTime(expiresTs);

    Environment env = ((ExecutionContextImpl) context).getEnv();
    String envName = (env != null) ? env.getName() : "";
    String verb = getStatusVerb(status);

    Map<String, String> placeHolderValues = new HashMap<>();
    placeHolderValues.put("START_TS_SECS", Long.toString(startTs / 1000L));
    placeHolderValues.put("END_TS_SECS", Long.toString(endTs / 1000L));
    placeHolderValues.put("EXPIRES_TS_SECS", String.valueOf(expiresTs / 1000L));
    placeHolderValues.put("START_DATE", startTime);
    placeHolderValues.put("END_DATE", endTime);
    placeHolderValues.put("EXPIRES_DATE", expiresTime);
    placeHolderValues.put("DURATION", getDurationString(startTs, endTs));
    placeHolderValues.put("VERB", verb);
    placeHolderValues.put("STATUS_CAMELCASE", toCamelCase(verb));
    placeHolderValues.put("WORKFLOW_NAME", context.getWorkflowExecutionName());
    placeHolderValues.put("WORKFLOW_URL", workflowUrl);
    placeHolderValues.put("TIMEOUT", timeout);
    placeHolderValues.put("APP_NAME", app.getName());
    placeHolderValues.put("USER_NAME", userNameSanitizer.sanitizeInput(userName));
    placeHolderValues.put("STATUS", statusMsg);
    placeHolderValues.put("ENV", envName);
    placeHolderValues.put("ARTIFACT", artifactsMessage);

    if (((ExecutionContextImpl) context).getStateExecutionInstance() != null
        && ((ExecutionContextImpl) context).getStateExecutionInstance().getExecutionType() != null) {
      placeHolderValues.put("APPROVAL_TYPE",
          getApprovalType(((ExecutionContextImpl) context).getStateExecutionInstance().getExecutionType()));
    } else {
      placeHolderValues.put("APPROVAL_TYPE", "NONE");
    }

    return placeHolderValues;
  }

  public String getFormattedExpiresTime(long expiresTs) {
    return format("%s at %s", dateFormat.format(new Date(expiresTs)), timeFormat.format(new Date(expiresTs)));
  }

  private Long parseLong(String s) {
    if (StringUtils.isNotBlank(s)) {
      return Long.parseLong(s);
    } else {
      return 0L;
    }
  }
}
