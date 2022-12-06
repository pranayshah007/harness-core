/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.change;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InternalChangeEventMetaData extends ChangeEventMetadata {
  String eventType; // Maps to activity type in internal change activity class.
  String updateBy;
  EventDetails eventDetails;

  @Override
  public ChangeSourceType getType() {
    return null;
  }

  @Value
  @Builder
  public class EventDetails {
    List<String> eventDetail;
    DeepLinkData internalLinkToEntity;
    DeepLinkData changeEventDetailsLink;
  }

  public enum Action { FETCH_DIFF_DATA, REDIRECT_URL }

  @Value
  @Builder
  public class DeepLinkData {
    Action action;
    String url;
  }
}
