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
import io.harness.scim.system.SchemaResource.SchemaResourceKeys;
import io.harness.scim.system.ScimAttributeResource;
import io.harness.scim.system.ScimAttributeResource.ScimResourceAttributeKeys;
import io.harness.scim.system.ScimAuthenticationScheme.AuthenticationSchemeKeys;
import io.harness.scim.system.ScimBulkConfigResource.BulkConfigKeys;
import io.harness.scim.system.ScimFilterConfig.FilterConfigKeys;
import io.harness.scim.system.ScimResourceType.ResourceTypeKeys;
import io.harness.scim.system.ServiceProviderConfigResource.ServiceProviderConfigKeys;

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
  static ScimAttributeResource getIdAttributeForSchema() {
    return new ScimAttributeResource(SchemaResourceKeys.id, ScimAttributeResource.Type.STRING, null, false,
        "The unique URI of the schema. When applicable, service providers MUST specify the URI.", true, null, true,
        ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getSubAttributesAttributeForSchema() {
    ScimAttributeResource nameSubAttribute = getNameAttribute("The attribute's name.", true);

    ScimAttributeResource typeSubAttribute = new ScimAttributeResource(ScimResourceAttributeKeys.type,
        ScimAttributeResource.Type.STRING, null, false,
        "The attribute's data type. Valid values include 'string', 'complex', 'boolean', 'decimal', 'integer','dateTime', 'reference'.",
        true, Arrays.asList("string", "complex", "boolean", "decimal", "integer", "dateTime", "reference"), true,
        ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);

    ScimAttributeResource multiValuedSubAttribute = new ScimAttributeResource(ScimResourceAttributeKeys.multiValued,
        ScimAttributeResource.Type.BOOLEAN, null, false, "A Boolean value indicating an attribute's plurality.", true,
        null, true, ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);

    return new ScimAttributeResource(SchemaResourceKeys.attributes, ScimAttributeResource.Type.COMPLEX,
        Arrays.asList(nameSubAttribute, typeSubAttribute, multiValuedSubAttribute), true,
        "A complex attribute that includes the attributes of a schema.", true, null, false,
        ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getMembersAttribute() {
    ScimAttributeResource valueSubAttribute =
        new ScimAttributeResource(MemberKeys.value, ScimAttributeResource.Type.STRING, null, false,
            "Identifier of the member of this Group.", false, null, false, ScimAttributeResource.Mutability.IMMUTABLE,
            ScimAttributeResource.Returned.DEFAULT, ScimAttributeResource.Uniqueness.NONE, null);

    ScimAttributeResource refSubAttribute =
        new ScimAttributeResource(MemberKeys.ref, ScimAttributeResource.Type.REFERENCE, null, false,
            "The URI corresponding to a SCIM resource that is a member of this Group.", false, null, false,
            ScimAttributeResource.Mutability.IMMUTABLE, ScimAttributeResource.Returned.DEFAULT,
            ScimAttributeResource.Uniqueness.NONE, Arrays.asList("User", "Group"));

    return new ScimAttributeResource(ScimGroupKeys.members, ScimAttributeResource.Type.COMPLEX,
        Arrays.asList(valueSubAttribute, refSubAttribute), true, "A list of members of the Group.", true, null, false,
        ScimAttributeResource.Mutability.READ_WRITE, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getDisplayNameAttributeForGroup() {
    return new ScimAttributeResource(ScimGroupKeys.displayName, ScimAttributeResource.Type.STRING, null, false,
        "A human-readable name for the Group. REQUIRED.", true, null, false,
        ScimAttributeResource.Mutability.READ_WRITE, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getEmailsAttribute() {
    ScimAttributeResource valueSubAttribute = new ScimAttributeResource(VALUE, ScimAttributeResource.Type.STRING, null,
        false,
        "Email addresses for the user. The value SHOULD be canonicalized by the service provider, e.g., 'bjensen@example.com' instead of 'bjensen@EXAMPLE.COM'. Canonical type values of 'work', 'home', and 'other",
        false, null, false, ScimAttributeResource.Mutability.READ_WRITE, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);

    ScimAttributeResource primarySubAttribute = new ScimAttributeResource(PRIMARY, ScimAttributeResource.Type.BOOLEAN,
        null, false,
        "A Boolean value indicating the 'primary' or preferred attribute value for this attribute, e.g., the preferred mailing address or primary email address. The primary attribute value 'true' MUST appear no more than once.",
        false, null, false, ScimAttributeResource.Mutability.READ_WRITE, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);

    return new ScimAttributeResource(ScimUserKeys.emails, ScimAttributeResource.Type.COMPLEX,
        Arrays.asList(valueSubAttribute, primarySubAttribute), true,
        "Email addresses for the user. The value SHOULD be canonicalized by the service provider, e.g., 'bjensen@example.com' instead of 'bjensen@EXAMPLE.COM'. Canonical type values of 'work', 'home', and 'other'.",
        true, null, false, ScimAttributeResource.Mutability.READ_WRITE, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getActiveAttribute() {
    return new ScimAttributeResource(ScimUserKeys.active, ScimAttributeResource.Type.BOOLEAN, null, false,
        "A Boolean value indicating the User's administrative status..", true, null, false,
        ScimAttributeResource.Mutability.READ_WRITE, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getDisplayNameAttribute() {
    return new ScimAttributeResource(ScimUserKeys.displayName, ScimAttributeResource.Type.STRING, null, false,
        "The name of the User, suitable for display to endusers. The name SHOULD be the full name of the User being described, if known.",
        false, null, true, ScimAttributeResource.Mutability.READ_WRITE, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getNameAttributeOfUser() {
    ScimAttributeResource familyNameSubAttribute = new ScimAttributeResource(FAMILY_NAME,
        ScimAttributeResource.Type.STRING, null, false,
        "The family name of the User, or last name in most Western languages (e.g., 'Jensen' given the full name 'Ms. Barbara J Jensen, III').",
        false, null, false, ScimAttributeResource.Mutability.READ_WRITE, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);

    ScimAttributeResource givenNameSubAttribute = new ScimAttributeResource(GIVEN_NAME,
        ScimAttributeResource.Type.STRING, null, false,
        "The given name of the User, or first name in most Western languages (e.g., 'Barbara' given the full name 'Ms. Barbara J Jensen, III')",
        false, null, false, ScimAttributeResource.Mutability.READ_WRITE, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);

    return new ScimAttributeResource(ScimUserKeys.name, ScimAttributeResource.Type.COMPLEX,
        Arrays.asList(givenNameSubAttribute, familyNameSubAttribute), false,
        "The components of the user's real name.Providers MAY return just the full name as a single string in theformatted subattribute, or they MAY return just the individual component attributes using the other sub-attributes, or they MAY return both. If both variants are returned, they SHOULD bedescribing the same name, with the formatted name indicating how the component attributes should be combined.",
        false, null, true, ScimAttributeResource.Mutability.READ_WRITE, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getExternalIdAttribute() {
    return new ScimAttributeResource(ScimBaseResourceKeys.externalId, ScimAttributeResource.Type.STRING, null, false,
        "Unique identifier for the User, typicallyused by the user to directly authenticate to the service provider.Each User MUST include a non-empty userName value. This identifier MUST be unique across the service provider's entire set of Users.REQUIRED.",
        true, null, true, ScimAttributeResource.Mutability.READ_WRITE, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.SERVER, null);
  }

  @NotNull
  static ScimAttributeResource getUserNameAttribute() {
    return new ScimAttributeResource(ScimUserKeys.userName, ScimAttributeResource.Type.STRING, null, false,
        "Unique identifier for the User, typically used bythe user to directly authenticate to the service provider.Each User MUST include a non-empty userName value. This identifier MUST be unique across the service provider's entire set of Users.REQUIRED",
        true, null, true, ScimAttributeResource.Mutability.READ_WRITE, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.SERVER, null);
  }

  @NotNull
  static ScimAttributeResource getSchemaAttribute() {
    return new ScimAttributeResource(ResourceTypeKeys.schema, ScimAttributeResource.Type.REFERENCE, null, false,
        "The resource type's primary/base schema URI.", true, null, true, ScimAttributeResource.Mutability.READ_ONLY,
        ScimAttributeResource.Returned.DEFAULT, ScimAttributeResource.Uniqueness.NONE, Arrays.asList("uri"));
  }

  @NotNull
  static ScimAttributeResource getEndPointAttribute() {
    return new ScimAttributeResource(ResourceTypeKeys.endpoint, ScimAttributeResource.Type.REFERENCE, null, false,
        "The resource type's HTTP-addressable endpoint relative to the Base URL, e.g., '/Users'.", true, null, false,
        ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, Arrays.asList("uri"));
  }

  @NotNull
  static ScimAttributeResource getAuthenticationSchemesAttribute() {
    ScimAttributeResource nameAttribute =
        getNameAttribute("The common authentication scheme name,e.g., HTTP Basic.", false);
    ScimAttributeResource descriptionAttribute = getDescriptionAttribute();
    return new ScimAttributeResource(ServiceProviderConfigKeys.scimAuthenticationSchemes,
        ScimAttributeResource.Type.COMPLEX, Arrays.asList(nameAttribute, descriptionAttribute), true,
        "A complex type that specifies supported authentication scheme properties.", true, null, false,
        ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.SERVER, null);
  }

  @NotNull
  static ScimAttributeResource getSortAttribute(ScimAttributeResource supportedAttribute) {
    return new ScimAttributeResource(ServiceProviderConfigKeys.sort, ScimAttributeResource.Type.COMPLEX,
        Arrays.asList(supportedAttribute), false, "A complex type that specifies sort result options.", true, null,
        false, ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getChangePasswordAttribute(ScimAttributeResource supportedAttribute) {
    return new ScimAttributeResource(ServiceProviderConfigKeys.changePassword, ScimAttributeResource.Type.COMPLEX,
        Arrays.asList(supportedAttribute), false,
        "A complex type that specifies configuration options related to changing a password.", true, null, false,
        ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getFilterAttribute(
      ScimAttributeResource supportedAttribute, ScimAttributeResource maxResultsAttribute) {
    return new ScimAttributeResource(ServiceProviderConfigKeys.filter, ScimAttributeResource.Type.COMPLEX,
        Arrays.asList(supportedAttribute, maxResultsAttribute), false, "A complex type that specifies FILTER options.",
        true, null, false, ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getBulkAttribute(ScimAttributeResource supportedAttribute,
      ScimAttributeResource maxOperationsAttribute, ScimAttributeResource maxPayloadSizeAttribute) {
    return new ScimAttributeResource(ServiceProviderConfigKeys.bulk, ScimAttributeResource.Type.COMPLEX,
        Arrays.asList(supportedAttribute, maxOperationsAttribute, maxPayloadSizeAttribute), false,
        "A complex type that specifies bulk configuration options.", true, null, false,
        ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getPatchAttribute(ScimAttributeResource supportedAttribute) {
    return new ScimAttributeResource(ServiceProviderConfigKeys.patch, ScimAttributeResource.Type.COMPLEX,
        Arrays.asList(supportedAttribute), false, "A complex type that specifies PATCH configuration options.", true,
        null, false, ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  private ScimAttributeResource getDescriptionAttribute() {
    return new ScimAttributeResource(AuthenticationSchemeKeys.description, ScimAttributeResource.Type.STRING, null,
        false, "A description of the authentication scheme.sic.", true, null, false,
        ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getNameAttribute(String description, boolean caseExact) {
    return new ScimAttributeResource(SchemaResourceKeys.name, ScimAttributeResource.Type.STRING, null, caseExact,
        description, true, null, caseExact, ScimAttributeResource.Mutability.READ_ONLY,
        ScimAttributeResource.Returned.DEFAULT, ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getMaxResultsAttribute() {
    return new ScimAttributeResource(FilterConfigKeys.maxResults, ScimAttributeResource.Type.INTEGER, null, false,
        "An integer value specifying the maximum number of resources returned in a response.", true, null, false,
        ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getMaxPayloadSizeAttribute() {
    return new ScimAttributeResource(BulkConfigKeys.maxPayloadSize, ScimAttributeResource.Type.INTEGER, null, false,
        "An integer value specifying the maximum payload size in bytes.", true, null, false,
        ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getMaxOperationsAttribute() {
    return new ScimAttributeResource(BulkConfigKeys.maxOperations, ScimAttributeResource.Type.INTEGER, null, false,
        "An integer value specifying the maximum number of operations.", true, null, false,
        ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }

  @NotNull
  static ScimAttributeResource getSupportedAttribute() {
    return new ScimAttributeResource(BulkConfigKeys.supported, ScimAttributeResource.Type.BOOLEAN, null, false,
        "A Boolean value specifying whether or not the operation is supported.", true, null, false,
        ScimAttributeResource.Mutability.READ_ONLY, ScimAttributeResource.Returned.DEFAULT,
        ScimAttributeResource.Uniqueness.NONE, null);
  }
}