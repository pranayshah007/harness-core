/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scim;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.DESCRIPTION_RESOURCE_TYPE;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.DESCRIPTION_SCHEMA;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.DESCRIPTION_SERVICE_PROVIDER_CONFIG;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.DOCUMENTATION_URI;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.GROUP_SCHEMA;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.ID_SCHEMA;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.SCHEMA_RESOURCE_TYPE;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.USER_SCHEMA;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getActiveAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getAuthenticationSchemesAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getBulkAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getChangePasswordAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getDisplayNameAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getDisplayNameAttributeForGroup;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getEmailsAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getEndPointAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getExternalIdAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getFilterAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getIdAttributeForSchema;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getMaxOperationsAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getMaxPayloadSizeAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getMaxResultsAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getMembersAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getNameAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getNameAttributeOfUser;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getPatchAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getSchemaAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getSortAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getSubAttributesAttributeForSchema;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getSupportedAttribute;
import static io.harness.ng.scim.NGScimSystemSchemaHelper.getUserNameAttribute;
import static io.harness.scim.system.ServiceProviderConfig.SCHEMA_SERVICE_PROVIDER_CONFIG;

import io.harness.annotations.dev.OwnedBy;
import io.harness.scim.ScimListResponse;
import io.harness.scim.service.ScimSystemService;
import io.harness.scim.system.AuthenticationScheme;
import io.harness.scim.system.BulkConfig;
import io.harness.scim.system.ChangePasswordConfig;
import io.harness.scim.system.ETagConfig;
import io.harness.scim.system.FilterConfig;
import io.harness.scim.system.PatchConfig;
import io.harness.scim.system.ResourceType;
import io.harness.scim.system.SchemaResource;
import io.harness.scim.system.ScimResourceAttribute;
import io.harness.scim.system.ServiceProviderConfig;
import io.harness.scim.system.SortConfig;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@NoArgsConstructor
@Slf4j
@OwnedBy(PL)
public class NGScimSystemServiceImpl implements ScimSystemService {
  @Override
  public ServiceProviderConfig getServiceProviderConfig(String accountIdentifier) {
    return new ServiceProviderConfig(DOCUMENTATION_URI, new PatchConfig(true), new BulkConfig(false, 1, 0),
        new FilterConfig(true, 1000), new ChangePasswordConfig(false), new SortConfig(false), new ETagConfig(false),
        Collections.singletonList(AuthenticationScheme.geBearerTokenAuth(true)));
  }

  @Override
  public ScimListResponse<ResourceType> getResourceTypes(String accountIdentifier) {
    ScimListResponse<ResourceType> resourceTypeScimListResponse = new ScimListResponse<>();

    resourceTypeScimListResponse.resource(getUserResourceType());
    resourceTypeScimListResponse.resource(getGroupResourceType());

    resourceTypeScimListResponse.startIndex(0);
    resourceTypeScimListResponse.itemsPerPage(resourceTypeScimListResponse.getResources().size());
    resourceTypeScimListResponse.totalResults(resourceTypeScimListResponse.getResources().size());
    return resourceTypeScimListResponse;
  }

  @Override
  public ScimListResponse<SchemaResource> getSchemas(String accountIdentifier) {
    ScimListResponse<SchemaResource> schemaResourceScimListResponse = new ScimListResponse<>();

    schemaResourceScimListResponse.resource(getServiceProviderConfigSchemaResource());
    schemaResourceScimListResponse.resource(getResourceTypesSchemaResource());
    schemaResourceScimListResponse.resource(getSchemasSchemaResource());
    schemaResourceScimListResponse.resource(getUserSchemaResource());
    schemaResourceScimListResponse.resource(getGroupSchemaResource());

    schemaResourceScimListResponse.startIndex(0);
    schemaResourceScimListResponse.itemsPerPage(schemaResourceScimListResponse.getResources().size());
    schemaResourceScimListResponse.totalResults(schemaResourceScimListResponse.getResources().size());
    return schemaResourceScimListResponse;
  }

  private ResourceType getUserResourceType() {
    String user = "User";
    return new ResourceType(
        Sets.newHashSet(USER_SCHEMA), user, DOCUMENTATION_URI, user, "/Users", "User Account", USER_SCHEMA);
  }

  private ResourceType getGroupResourceType() {
    String group = "Group";
    return new ResourceType(
        Sets.newHashSet(GROUP_SCHEMA), group, DOCUMENTATION_URI, group, "/Group", "Groups", GROUP_SCHEMA);
  }

  private SchemaResource getServiceProviderConfigSchemaResource() {
    List<ScimResourceAttribute> scimResourceAttributeList = new ArrayList<>();
    ScimResourceAttribute supportedAttribute = getSupportedAttribute();
    scimResourceAttributeList.add(getPatchAttribute(supportedAttribute));
    scimResourceAttributeList.add(
        getBulkAttribute(supportedAttribute, getMaxOperationsAttribute(), getMaxPayloadSizeAttribute()));
    scimResourceAttributeList.add(getFilterAttribute(supportedAttribute, getMaxResultsAttribute()));
    scimResourceAttributeList.add(getChangePasswordAttribute(supportedAttribute));
    scimResourceAttributeList.add(getSortAttribute(supportedAttribute));
    scimResourceAttributeList.add(getAuthenticationSchemesAttribute());
    return new SchemaResource(SCHEMA_SERVICE_PROVIDER_CONFIG, "Service Provider Configuration",
        DESCRIPTION_SERVICE_PROVIDER_CONFIG, scimResourceAttributeList);
  }

  private SchemaResource getResourceTypesSchemaResource() {
    List<ScimResourceAttribute> scimResourceAttributeList = new ArrayList<>();
    ScimResourceAttribute nameAttribute = getNameAttribute(
        "The resource type name. When applicable, service providers MUST specify the name, e.g., 'User'.", false);
    scimResourceAttributeList.add(nameAttribute);
    scimResourceAttributeList.add(getEndPointAttribute());
    scimResourceAttributeList.add(getSchemaAttribute());
    return new SchemaResource(
        SCHEMA_RESOURCE_TYPE, "ResourceType", DESCRIPTION_RESOURCE_TYPE, scimResourceAttributeList);
  }

  private SchemaResource getSchemasSchemaResource() {
    List<ScimResourceAttribute> scimResourceAttributeList = new ArrayList<>();
    scimResourceAttributeList.add(getIdAttributeForSchema());
    ScimResourceAttribute nameAttributeForSchema = getNameAttribute(
        "The schema's human-readable name. When applicable, service providers MUST specify the name, e.g., 'User'.",
        false);
    scimResourceAttributeList.add(nameAttributeForSchema);
    scimResourceAttributeList.add(getSubAttributesAttributeForSchema());
    return new SchemaResource(ID_SCHEMA, "Schema", DESCRIPTION_SCHEMA, scimResourceAttributeList);
  }

  private SchemaResource getUserSchemaResource() {
    List<ScimResourceAttribute> scimResourceAttributeList = new ArrayList<>();
    scimResourceAttributeList.add(getUserNameAttribute());
    scimResourceAttributeList.add(getExternalIdAttribute());
    scimResourceAttributeList.add(getNameAttributeOfUser());
    scimResourceAttributeList.add(getDisplayNameAttribute());
    scimResourceAttributeList.add(getActiveAttribute());
    scimResourceAttributeList.add(getEmailsAttribute());
    return new SchemaResource(USER_SCHEMA, "User", "User Account", scimResourceAttributeList);
  }

  private SchemaResource getGroupSchemaResource() {
    List<ScimResourceAttribute> scimResourceAttributeList = new ArrayList<>();
    scimResourceAttributeList.add(getDisplayNameAttributeForGroup());
    scimResourceAttributeList.add(getMembersAttribute());
    return new SchemaResource(GROUP_SCHEMA, "Group", "Group", scimResourceAttributeList);
  }
}