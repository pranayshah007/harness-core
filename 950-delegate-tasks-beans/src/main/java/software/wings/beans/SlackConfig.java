/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.audit.ResourceType.COLLABORATION_PROVIDER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;

import software.wings.beans.notification.SlackNotificationConfiguration;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by anubhaw on 12/14/16.
 */
@OwnedBy(CDC)
@JsonTypeName("SLACK")
@ToString
@Deprecated
public class SlackConfig extends SettingValue implements SlackNotificationConfiguration {
  private static final String SLACK_HOOK_YRL = "https://hooks.slack.com";
  @Attributes(title = "Slack Webhook URL", required = true) @NotEmpty private String outgoingWebhookUrl;

  /**
   * Instantiates a new setting value.
   */
  public SlackConfig() {
    super(SettingVariableTypes.SLACK.name());
  }

  @Nullable
  @Override
  @SchemaIgnore
  public String getName() {
    return null;
  }

  /**
   * Gets incoming webhook url.
   *
   * @return the incoming webhook url
   */
  @Override
  public String getOutgoingWebhookUrl() {
    return outgoingWebhookUrl;
  }

  /**
   * Sets incoming webhook url.
   *
   * @param outgoingWebhookUrl the incoming webhook url
   */
  public void setOutgoingWebhookUrl(String outgoingWebhookUrl) {
    this.outgoingWebhookUrl = outgoingWebhookUrl;
  }

  @Override
  public String fetchResourceCategory() {
    return COLLABORATION_PROVIDER.name();
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Arrays.asList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        SLACK_HOOK_YRL, maskingEvaluator));
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String outgoingWebhookUrl;

    private Builder() {}

    /**
     * A slack config builder.
     *
     * @return the builder
     */
    public static Builder aSlackConfig() {
      return new Builder();
    }

    /**
     * With outgoing webhook url builder.
     *
     * @param outgoingWebhookUrl the outgoing webhook url
     * @return the builder
     */
    public Builder withOutgoingWebhookUrl(String outgoingWebhookUrl) {
      this.outgoingWebhookUrl = outgoingWebhookUrl;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aSlackConfig().withOutgoingWebhookUrl(outgoingWebhookUrl);
    }
  }
}
