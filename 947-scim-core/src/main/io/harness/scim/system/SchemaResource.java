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
import java.util.Collection;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * All fields in this class are defined as per below doc.
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7643#section-7">SCIM Schema Definition</a>
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "SchemaResourceKeys")
@OwnedBy(PL)
public class SchemaResource {
  private String id;
  private String name;
  private String description;
  private Collection<ScimAttributeResource> attributes;

  @JsonCreator
  public SchemaResource(@JsonProperty(value = "id") String id, @JsonProperty(value = "name") String name,
      @JsonProperty(value = "description") String description,
      @JsonProperty(value = "attributes") Collection<ScimAttributeResource> attributes) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.attributes = attributes;
  }
}
