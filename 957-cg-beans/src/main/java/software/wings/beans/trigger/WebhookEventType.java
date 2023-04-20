/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.trigger;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnknownEnumTypeException;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

@OwnedBy(HarnessTeam.CDC)
public enum WebhookEventType {
  PULL_REQUEST("On Pull Request", "pull_request"),
  PUSH("On Push", "push"),
  REPO("On Repo", "repo"),
  ISSUE("On Issue", "issue"),
  PING("On Ping", "ping"),
  DELETE("On Delete", "delete"),
  ANY("Any", "any"),
  OTHER("Other", "other"),
  RELEASE("On Release", "release"),
  PACKAGE("On Package", "package");

  @Getter private String displayName;
  @Getter private String value;

  WebhookEventType(String displayName, String value) {
    this.displayName = displayName;
    this.value = value;
    WebhookHolder.map.put(value, this);
  }

  private static class WebhookHolder {
    static Map<String, WebhookEventType> map = new HashMap<>();
  }

  public static WebhookEventType find(String val) {
    WebhookEventType t = WebhookHolder.map.get(val);
    if (t == null) {
      throw new InvalidRequestException(String.format("Unsupported Webhook Event Type %s.", val));
    }
    return t;
  }

  public static WebhookEventType fromString(String val) {
    if (isBlank(val)) {
      return null;
    }
    for (WebhookEventType type : values()) {
      if (type.name().equals(val)) {
        return type;
      }
    }
    throw new UnknownEnumTypeException("Webhook event type", val);
  }
}
