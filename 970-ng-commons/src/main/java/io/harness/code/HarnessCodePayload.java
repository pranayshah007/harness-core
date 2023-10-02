/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.code;

import io.harness.exception.InvalidRequestException;

import java.util.Arrays;
import lombok.Builder;
import lombok.Value;
import org.json.JSONObject;

@Value
@Builder
public class HarnessCodePayload {
  String check_uid;
  String link;
  Payload payload;
  CheckStatus status;
  String summary;

  @Value
  @Builder
  public static class Payload {
    String version;
    CheckPayloadKind kind;
    JSONObject data;
  }

  public enum CheckPayloadKind { raw, markdown }

  public enum CheckStatus {
    pending,
    running,
    success,
    failure,
    error;

    public static CheckStatus fromString(String status) {
      return Arrays.stream(CheckStatus.values())
          .filter(checkStatus -> checkStatus.name().equalsIgnoreCase(status))
          .findFirst()
          .orElseThrow(() -> new InvalidRequestException(String.format("Unknown status %s", status)));
    }
  }
}
