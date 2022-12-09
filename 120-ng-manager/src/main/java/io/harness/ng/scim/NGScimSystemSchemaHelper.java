/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scim;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.scim.Member.MemberKeys;
import io.harness.scim.ScimBaseResource.ScimBaseResourceKeys;
import io.harness.scim.ScimGroup.ScimGroupKeys;
import io.harness.scim.ScimUser.ScimUserKeys;
import io.harness.scim.system.AuthenticationScheme.AuthenticationSchemeKeys;
import io.harness.scim.system.BulkConfig.BulkConfigKeys;
import io.harness.scim.system.FilterConfig.FilterConfigKeys;
import io.harness.scim.system.ResourceType.ResourceTypeKeys;
import io.harness.scim.system.SchemaResource.SchemaResourceKeys;
import io.harness.scim.system.ScimResourceAttribute;
import io.harness.scim.system.ScimResourceAttribute.ScimResourceAttributeKeys;
import io.harness.scim.system.ServiceProviderConfig.ServiceProviderConfigKeys;

import java.util.Arrays;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@UtilityClass
@Slf4j
@OwnedBy(PL)
public class NGScimSystemSchemaHelper {
  public static final String DOCUMENTATION_URI = "https://docs.harness.io/category/fe0577j8ie-access-management";
  public static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
  public static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";
  public static final String SCHEMA_RESOURCE_TYPE = "urn:ietf:params:scim:schemas:core:2.0:ResourceType";
  public static final String DESCRIPTION_RESOURCE_TYPE = "Specifies the schema that describes a SCIM resource type";
  public static final String DESCRIPTION_SERVICE_PROVIDER_CONFIG =
      "Schema for representing the service provider's configuration";
  public static final String DESCRIPTION_SCHEMA = "Specifies the schema that describes a SCIM schema";
  public static final String ID_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Schema";
  public static final String FAMILY_NAME = "familyName";
  public static final String GIVEN_NAME = "givenName";
  public static final String PRIMARY = "primary";
  public static final String VALUE = "value";

  @NotNull
  static ScimResourceAttribute getIdAttributeForSchema() {
    return new ScimResourceAttribute(SchemaResourceKeys.id, ScimResourceAttribute.Type.STRING, null, false,
        "The unique URI of the schema. When applicable, service providers MUST specify the URI.", true, null, true,
        ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getSubAttributesAttributeForSchema() {
    ScimResourceAttribute nameSubAttribute = getNameAttribute("The attribute's name.", true);

    ScimResourceAttribute typeSubAttribute = new ScimResourceAttribute(ScimResourceAttributeKeys.type,
        ScimResourceAttribute.Type.STRING, null, false,
        "The attribute's data type. Valid values include 'string', 'complex', 'boolean', 'decimal', 'integer','dateTime', 'reference'.",
        true, Arrays.asList("string", "complex", "boolean", "decimal", "integer", "dateTime", "reference"), true,
        ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);

    ScimResourceAttribute multiValuedSubAttribute = new ScimResourceAttribute(ScimResourceAttributeKeys.multiValued,
        ScimResourceAttribute.Type.BOOLEAN, null, false, "A Boolean value indicating an attribute's plurality.", true,
        null, true, ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);

    return new ScimResourceAttribute(SchemaResourceKeys.attributes, ScimResourceAttribute.Type.COMPLEX,
        Arrays.asList(nameSubAttribute, typeSubAttribute, multiValuedSubAttribute), true,
        "A complex attribute that includes the attributes of a schema.", true, null, false,
        ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getMembersAttribute() {
    ScimResourceAttribute valueSubAttribute =
        new ScimResourceAttribute(MemberKeys.value, ScimResourceAttribute.Type.STRING, null, false,
            "Identifier of the member of this Group.", false, null, false, ScimResourceAttribute.Mutability.IMMUTABLE,
            ScimResourceAttribute.Returned.DEFAULT, ScimResourceAttribute.Uniqueness.NONE, null);

    ScimResourceAttribute refSubAttribute =
        new ScimResourceAttribute(MemberKeys.ref, ScimResourceAttribute.Type.REFERENCE, null, false,
            "The URI corresponding to a SCIM resource that is a member of this Group.", false, null, false,
            ScimResourceAttribute.Mutability.IMMUTABLE, ScimResourceAttribute.Returned.DEFAULT,
            ScimResourceAttribute.Uniqueness.NONE, Arrays.asList("User", "Group"));

    return new ScimResourceAttribute(ScimGroupKeys.members, ScimResourceAttribute.Type.COMPLEX,
        Arrays.asList(valueSubAttribute, refSubAttribute), true, "A list of members of the Group.", true, null, false,
        ScimResourceAttribute.Mutability.READ_WRITE, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getDisplayNameAttributeForGroup() {
    return new ScimResourceAttribute(ScimGroupKeys.displayName, ScimResourceAttribute.Type.STRING, null, false,
        "A human-readable name for the Group. REQUIRED.", true, null, false,
        ScimResourceAttribute.Mutability.READ_WRITE, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getEmailsAttribute() {
    ScimResourceAttribute valueSubAttribute = new ScimResourceAttribute(VALUE, ScimResourceAttribute.Type.STRING, null,
        false,
        "Email addresses for the user. The value SHOULD be canonicalized by the service provider, e.g., 'bjensen@example.com' instead of 'bjensen@EXAMPLE.COM'. Canonical type values of 'work', 'home', and 'other",
        false, null, false, ScimResourceAttribute.Mutability.READ_WRITE, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);

    ScimResourceAttribute primarySubAttribute = new ScimResourceAttribute(PRIMARY, ScimResourceAttribute.Type.BOOLEAN,
        null, false,
        "A Boolean value indicating the 'primary' or preferred attribute value for this attribute, e.g., the preferred mailing address or primary email address. The primary attribute value 'true' MUST appear no more than once.",
        false, null, false, ScimResourceAttribute.Mutability.READ_WRITE, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);

    return new ScimResourceAttribute(ScimUserKeys.emails, ScimResourceAttribute.Type.COMPLEX,
        Arrays.asList(valueSubAttribute, primarySubAttribute), true,
        "Email addresses for the user. The value SHOULD be canonicalized by the service provider, e.g., 'bjensen@example.com' instead of 'bjensen@EXAMPLE.COM'. Canonical type values of 'work', 'home', and 'other'.",
        true, null, false, ScimResourceAttribute.Mutability.READ_WRITE, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getActiveAttribute() {
    return new ScimResourceAttribute(ScimUserKeys.active, ScimResourceAttribute.Type.BOOLEAN, null, false,
        "A Boolean value indicating the User's administrative status..", true, null, false,
        ScimResourceAttribute.Mutability.READ_WRITE, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getDisplayNameAttribute() {
    return new ScimResourceAttribute(ScimUserKeys.displayName, ScimResourceAttribute.Type.STRING, null, false,
        "The name of the User, suitable for display to endusers. The name SHOULD be the full name of the User being described, if known.",
        false, null, true, ScimResourceAttribute.Mutability.READ_WRITE, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getNameAttributeOfUser() {
    ScimResourceAttribute familyNameSubAttribute = new ScimResourceAttribute(FAMILY_NAME,
        ScimResourceAttribute.Type.STRING, null, false,
        "The family name of the User, or last name in most Western languages (e.g., 'Jensen' given the full name 'Ms. Barbara J Jensen, III').",
        false, null, false, ScimResourceAttribute.Mutability.READ_WRITE, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);

    ScimResourceAttribute givenNameSubAttribute = new ScimResourceAttribute(GIVEN_NAME,
        ScimResourceAttribute.Type.STRING, null, false,
        "The given name of the User, or first name in most Western languages (e.g., 'Barbara' given the full name 'Ms. Barbara J Jensen, III')",
        false, null, false, ScimResourceAttribute.Mutability.READ_WRITE, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);

    return new ScimResourceAttribute(ScimUserKeys.name, ScimResourceAttribute.Type.COMPLEX,
        Arrays.asList(givenNameSubAttribute, familyNameSubAttribute), false,
        "The components of the user's real name.Providers MAY return just the full name as a single string in theformatted subattribute, or they MAY return just the individual component attributes using the other sub-attributes, or they MAY return both. If both variants are returned, they SHOULD bedescribing the same name, with the formatted name indicating how the component attributes should be combined.",
        false, null, true, ScimResourceAttribute.Mutability.READ_WRITE, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getExternalIdAttribute() {
    return new ScimResourceAttribute(ScimBaseResourceKeys.externalId, ScimResourceAttribute.Type.STRING, null, false,
        "Unique identifier for the User, typicallyused by the user to directly authenticate to the service provider.Each User MUST include a non-empty userName value. This identifier MUST be unique across the service provider's entire set of Users.REQUIRED.",
        true, null, true, ScimResourceAttribute.Mutability.READ_WRITE, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.SERVER, null);
  }

  @NotNull
  static ScimResourceAttribute getUserNameAttribute() {
    return new ScimResourceAttribute(ScimUserKeys.userName, ScimResourceAttribute.Type.STRING, null, false,
        "Unique identifier for the User, typically used bythe user to directly authenticate to the service provider.Each User MUST include a non-empty userName value. This identifier MUST be unique across the service provider's entire set of Users.REQUIRED",
        true, null, true, ScimResourceAttribute.Mutability.READ_WRITE, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.SERVER, null);
  }

  @NotNull
  static ScimResourceAttribute getSchemaAttribute() {
    return new ScimResourceAttribute(ResourceTypeKeys.schema, ScimResourceAttribute.Type.REFERENCE, null, false,
        "The resource type's primary/base schema URI.", true, null, true, ScimResourceAttribute.Mutability.READ_ONLY,
        ScimResourceAttribute.Returned.DEFAULT, ScimResourceAttribute.Uniqueness.NONE, Arrays.asList("uri"));
  }

  @NotNull
  static ScimResourceAttribute getEndPointAttribute() {
    return new ScimResourceAttribute(ResourceTypeKeys.endpoint, ScimResourceAttribute.Type.REFERENCE, null, false,
        "The resource type's HTTP-addressable endpoint relative to the Base URL, e.g., '/Users'.", true, null, false,
        ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, Arrays.asList("uri"));
  }

  @NotNull
  static ScimResourceAttribute getAuthenticationSchemesAttribute() {
    ScimResourceAttribute nameAttribute =
        getNameAttribute("The common authentication scheme name,e.g., HTTP Basic.", false);
    ScimResourceAttribute descriptionAttribute = getDescriptionAttribute();
    return new ScimResourceAttribute(ServiceProviderConfigKeys.authenticationSchemes,
        ScimResourceAttribute.Type.COMPLEX, Arrays.asList(nameAttribute, descriptionAttribute), true,
        "A complex type that specifies supported authentication scheme properties.", true, null, false,
        ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.SERVER, null);
  }

  @NotNull
  static ScimResourceAttribute getSortAttribute(ScimResourceAttribute supportedAttribute) {
    return new ScimResourceAttribute(ServiceProviderConfigKeys.sort, ScimResourceAttribute.Type.COMPLEX,
        Arrays.asList(supportedAttribute), false, "A complex type that specifies sort result options.", true, null,
        false, ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getChangePasswordAttribute(ScimResourceAttribute supportedAttribute) {
    return new ScimResourceAttribute(ServiceProviderConfigKeys.changePassword, ScimResourceAttribute.Type.COMPLEX,
        Arrays.asList(supportedAttribute), false,
        "A complex type that specifies configuration options related to changing a password.", true, null, false,
        ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getFilterAttribute(
      ScimResourceAttribute supportedAttribute, ScimResourceAttribute maxResultsAttribute) {
    return new ScimResourceAttribute(ServiceProviderConfigKeys.filter, ScimResourceAttribute.Type.COMPLEX,
        Arrays.asList(supportedAttribute, maxResultsAttribute), false, "A complex type that specifies FILTER options.",
        true, null, false, ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getBulkAttribute(ScimResourceAttribute supportedAttribute,
      ScimResourceAttribute maxOperationsAttribute, ScimResourceAttribute maxPayloadSizeAttribute) {
    return new ScimResourceAttribute(ServiceProviderConfigKeys.bulk, ScimResourceAttribute.Type.COMPLEX,
        Arrays.asList(supportedAttribute, maxOperationsAttribute, maxPayloadSizeAttribute), false,
        "A complex type that specifies bulk configuration options.", true, null, false,
        ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getPatchAttribute(ScimResourceAttribute supportedAttribute) {
    return new ScimResourceAttribute(ServiceProviderConfigKeys.patch, ScimResourceAttribute.Type.COMPLEX,
        Arrays.asList(supportedAttribute), false, "A complex type that specifies PATCH configuration options.", true,
        null, false, ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  private ScimResourceAttribute getDescriptionAttribute() {
    return new ScimResourceAttribute(AuthenticationSchemeKeys.description, ScimResourceAttribute.Type.STRING, null,
        false, "A description of the authentication scheme.sic.", true, null, false,
        ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getNameAttribute(String description, boolean caseExact) {
    return new ScimResourceAttribute(SchemaResourceKeys.name, ScimResourceAttribute.Type.STRING, null, caseExact,
        description, true, null, caseExact, ScimResourceAttribute.Mutability.READ_ONLY,
        ScimResourceAttribute.Returned.DEFAULT, ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getMaxResultsAttribute() {
    return new ScimResourceAttribute(FilterConfigKeys.maxResults, ScimResourceAttribute.Type.INTEGER, null, false,
        "An integer value specifying the maximum number of resources returned in a response.", true, null, false,
        ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getMaxPayloadSizeAttribute() {
    return new ScimResourceAttribute(BulkConfigKeys.maxPayloadSize, ScimResourceAttribute.Type.INTEGER, null, false,
        "An integer value specifying the maximum payload size in bytes.", true, null, false,
        ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getMaxOperationsAttribute() {
    return new ScimResourceAttribute(BulkConfigKeys.maxOperations, ScimResourceAttribute.Type.INTEGER, null, false,
        "An integer value specifying the maximum number of operations.", true, null, false,
        ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimResourceAttribute getSupportedAttribute() {
    return new ScimResourceAttribute(BulkConfigKeys.supported, ScimResourceAttribute.Type.BOOLEAN, null, false,
        "A Boolean value specifying whether or not the operation is supported.", true, null, false,
        ScimResourceAttribute.Mutability.READ_ONLY, ScimResourceAttribute.Returned.DEFAULT,
        ScimResourceAttribute.Uniqueness.NONE, null);
  }
}