package io.harness.scim.system;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * All fields in this class are defined as per below doc.
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7643#section-6">SCIM ResourceType Definition</a>
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "ResourceTypeKeys")
@OwnedBy(PL)
public class ResourceType {
  private Set<String> schemas;
  private String id;
  private String documentationUri;
  private String name;
  private String endpoint;
  private String description;
  private String schema;

  @JsonCreator
  public ResourceType(@JsonProperty(value = "schemas") Set<String> schemas, @JsonProperty(value = "id") String id,
      @JsonProperty(value = "documentationUri") String documentationUri, @JsonProperty(value = "name") String name,
      @JsonProperty(value = "endpoint") String endpoint, @JsonProperty(value = "description") String description,
      @JsonProperty(value = "schema") String schema) {
    this.schemas = schemas;
    this.id = id;
    this.documentationUri = documentationUri;
    this.name = name;
    this.endpoint = endpoint;
    this.description = description;
    this.schema = schema;
  }
}
