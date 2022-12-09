package io.harness.scim.system;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static java.util.Collections.unmodifiableList;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.Collection;
import javax.ws.rs.BadRequestException;
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
@FieldNameConstants(innerTypeName = "ScimResourceAttributeKeys")
@OwnedBy(PL)
public class ScimResourceAttribute {
  private String name;
  private Type type;
  private Collection<ScimResourceAttribute> subAttributes;
  private boolean multiValued;
  private String description;
  private boolean required;
  private Collection<String> canonicalValues;
  private boolean caseExact;
  private Mutability mutability;
  private Returned returned;
  private Uniqueness uniqueness;
  private Collection<String> referenceTypes;

  @JsonCreator
  public ScimResourceAttribute(@JsonProperty(value = "name", required = true) String name,
      @JsonProperty(value = "type", required = true) Type type,
      @JsonProperty(value = "subAttributes") Collection<ScimResourceAttribute> subAttributes,
      @JsonProperty(value = "multiValued", required = true) boolean multiValued,
      @JsonProperty(value = "description") String description,
      @JsonProperty(value = "required", required = true) boolean required,
      @JsonProperty(value = "canonicalValues") Collection<String> canonicalValues,
      @JsonProperty(value = "caseExact") boolean caseExact,
      @JsonProperty(value = "mutability", required = true) Mutability mutability,
      @JsonProperty(value = "returned", required = true) Returned returned,
      @JsonProperty(value = "uniqueness") Uniqueness uniqueness,
      @JsonProperty(value = "referenceTypes") Collection<String> referenceTypes) {
    this.name = name;
    this.type = type;
    this.subAttributes =
        subAttributes == null ? null : unmodifiableList(new ArrayList<ScimResourceAttribute>(subAttributes));
    this.multiValued = multiValued;
    this.description = description;
    this.required = required;
    this.canonicalValues = canonicalValues == null ? null : unmodifiableList(new ArrayList<String>(canonicalValues));
    this.caseExact = caseExact;
    this.mutability = mutability;
    this.returned = returned;
    this.uniqueness = uniqueness;
    this.referenceTypes = referenceTypes == null ? null : unmodifiableList(new ArrayList<String>(referenceTypes));
  }

  public enum Type {
    STRING("string"),
    BOOLEAN("boolean"),
    INTEGER("integer"),
    REFERENCE("reference"),
    COMPLEX("complex");

    private String scimValue;

    Type(String scimValue) {
      this.scimValue = scimValue;
    }

    @JsonValue
    public String getScimValue() {
      return scimValue;
    }

    @JsonCreator
    public static Type fromName(String scimValue) {
      for (Type type : Type.values()) {
        if (type.getScimValue().equalsIgnoreCase(scimValue)) {
          return type;
        }
      }

      throw new RuntimeException("Unknown SCIM datatype");
    }
  }

  public enum Mutability {
    READ_ONLY("readOnly"),
    READ_WRITE("readWrite"),
    IMMUTABLE("immutable"),
    WRITE_ONLY("writeOnly");

    private String scimValue;

    Mutability(String scimValue) {
      this.scimValue = scimValue;
    }
    @JsonValue
    public String getScimValue() {
      return scimValue;
    }

    @JsonCreator
    public static Mutability fromName(String scimValue) throws BadRequestException {
      for (Mutability mutability : Mutability.values()) {
        if (mutability.getScimValue().equalsIgnoreCase(scimValue)) {
          return mutability;
        }
      }
      throw new BadRequestException("Unknown SCIM mutability constraint");
    }
  }

  /**
   * A single keyword that indicates when an attribute and associated values are returned in response to a GET request
   * or in response to a PUT, POST, or PATCH request.
   */
  public enum Returned {
    /**
     * The attribute is always returned, regardless of the contents of the "attributes" parameter.  For example, "id" is
     * always returned to identify a SCIM resource
     */
    ALWAYS("always"),

    /**
     * The attribute is never returned.  This may occur because the original attribute value (e.g., a hashed value) is
     * not retained by the service provider.  A service provider MAY allow attributes to be used in a search filter.
     */
    NEVER("never"),

    DEFAULT("default"),

    /**
     * Indicates that the attribute is only returned if requested.
     */
    REQUEST("request");

    /**
     * The SCIM name for this enum.
     */
    private String scimValue;

    Returned(String scimValue) {
      this.scimValue = scimValue;
    }

    @JsonValue
    public String getScimValue() {
      return scimValue;
    }

    @JsonCreator
    public static Returned fromName(String name) throws BadRequestException {
      for (Returned returned : Returned.values()) {
        if (returned.getScimValue().equalsIgnoreCase(name)) {
          return returned;
        }
      }
      throw new BadRequestException("Unknown SCIM return constraint");
    }
  }

  public enum Uniqueness {
    /**
     * The values are not intended to be unique in any way. DEFAULT
     */

    NONE("none"),

    /**
     * The value SHOULD be unique within the context of the current SCIM endpoint (or tenancy) and MAY be globally
     * unique (e.g., a "username", email address, or other server-generated key or counter).  No two resources on the
     * same server SHOULD possess the same value.
     */
    SERVER("server"),

    /**
     * The value SHOULD be globally unique (e.g., an email address, a GUID, or other value).  No two resources on any
     * server SHOULD possess the same value
     */
    GLOBAL("global");

    private String scimValue;

    Uniqueness(String scimValue) {
      this.scimValue = scimValue;
    }

    @JsonValue
    public String getScimValue() {
      return scimValue;
    }

    @JsonCreator
    public static Uniqueness fromName(String name) throws BadRequestException {
      for (Uniqueness uniqueness : Uniqueness.values()) {
        if (uniqueness.getScimValue().equalsIgnoreCase(name)) {
          return uniqueness;
        }
      }
      throw new BadRequestException("Unknown SCIM uniquenessConstraint");
    }
  }
}
