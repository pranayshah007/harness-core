/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MongoBackendConfiguration extends NotificationClientBackendConfiguration {
  private String uri;
  @JsonProperty(defaultValue = "30000") @Builder.Default @NotEmpty private int connectTimeout = 30000;
  @JsonProperty(defaultValue = "90000") @Builder.Default @NotEmpty private int serverSelectionTimeout = 90000;
  @JsonProperty(defaultValue = "600000") @Builder.Default @NotEmpty private int maxConnectionIdleTime = 600000;
  @JsonProperty(defaultValue = "300") @Builder.Default @NotEmpty private int connectionsPerHost = 300;
}
