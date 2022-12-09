/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.scim.system;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "AuthenticationSchemeKeys")
@OwnedBy(PL)
public class ScimAuthenticationScheme {
  public static final String INFO_RFC_6750 = "http://www.rfc-editor.org/info/rfc6750";
  private String name;

  private String description;

  private URI specUri;

  private URI documentationUri;

  private String type;

  private boolean primary;

  @JsonCreator
  public ScimAuthenticationScheme(@JsonProperty(value = "name", required = true) final String name,
      @JsonProperty(value = "description", required = true) final String description,
      @JsonProperty(value = "specUri") final URI specUri,
      @JsonProperty(value = "documentationUri") final URI documentationUri,
      @JsonProperty(value = "type") final String type,
      @JsonProperty(value = "primary", defaultValue = "false") final boolean primary) {
    this.name = name;
    this.description = description;
    this.specUri = specUri;
    this.documentationUri = documentationUri;
    this.type = type;
    this.primary = primary;
  }

  public static ScimAuthenticationScheme getBearerTokenAuth(final boolean primary) {
    try {
      return new ScimAuthenticationScheme("OAuth Bearer Token",
          "Authentication scheme using the OAuth Bearer Token Standard", new URI(INFO_RFC_6750), null,
          "oauthbearertoken", primary);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
