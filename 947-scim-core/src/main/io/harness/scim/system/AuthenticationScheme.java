/*
 * Copyright 2015-2021 Ping Identity Corporation
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPLv2 only)
 * or the terms of the GNU Lesser General Public License (LGPLv2.1 only)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
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
public class AuthenticationScheme {
  private String name;

  private String description;

  private URI specUri;

  private URI documentationUri;

  private String type;

  private boolean primary;

  @JsonCreator
  public AuthenticationScheme(@JsonProperty(value = "name", required = true) final String name,
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

  public static AuthenticationScheme geBearerTokenAuth(final boolean primary) {
    try {
      return new AuthenticationScheme("OAuth Bearer Toke",
          "Authentication scheme using the OAuth Bearer Token Standard",
          new URI("http://www.rfc-editor.org/info/rfc6750"), null, "oauthbearertoken", primary);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
