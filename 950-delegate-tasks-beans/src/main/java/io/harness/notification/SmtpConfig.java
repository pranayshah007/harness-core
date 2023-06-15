/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification;

import io.harness.secret.ConfigSecret;

import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@JsonTypeName("stmp")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"password"})
@EqualsAndHashCode
public class SmtpConfig {
  @JsonProperty("type") private String type;
  @JsonProperty("host") private String host;
  @JsonProperty("port") private int port;
  @JsonProperty("fromAddress") private String fromAddress;
  @JsonProperty("useSSL") private boolean useSSL;
  @JsonProperty("startTLS") private boolean startTLS;
  @JsonProperty("username") @ConfigSecret private String username;
  @JsonProperty("password") @ConfigSecret private char[] password;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedPassword;
  @JsonProperty("delegateSelectors") private Set<String> delegateSelectors;
}
