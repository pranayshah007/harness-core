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
import static io.harness.scim.system.ServiceProviderConfigResource.SCHEMA_SERVICE_PROVIDER_CONFIG;

import io.harness.annotations.dev.OwnedBy;
import io.harness.scim.ScimListResponse;
import io.harness.scim.service.ScimSystemService;
import io.harness.scim.system.SchemaResource;
import io.harness.scim.system.ScimAttributeResource;
import io.harness.scim.system.ScimAuthenticationScheme;
import io.harness.scim.system.ScimBulkConfigResource;
import io.harness.scim.system.ScimChangePasswordConfig;
import io.harness.scim.system.ScimETagConfig;
import io.harness.scim.system.ScimFilterConfig;
import io.harness.scim.system.ScimPatchConfig;
import io.harness.scim.system.ScimResourceType;
import io.harness.scim.system.ScimSortConfig;
import io.harness.scim.system.ServiceProviderConfigResource;

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
  public ServiceProviderConfigResource getServiceProviderConfig(String accountIdentifier) {
    return new ServiceProviderConfigResource(DOCUMENTATION_URI, new ScimPatchConfig(true),
        new ScimBulkConfigResource(false, 1, 0), new ScimFilterConfig(true, 1000), new ScimChangePasswordConfig(false),
        new ScimSortConfig(false), new ScimETagConfig(false),
        Collections.singletonList(ScimAuthenticationScheme.getBearerTokenAuth(true)));
  }

  @Override
  public ScimListResponse<ScimResourceType> getResourceTypes(String accountIdentifier) {
    ScimListResponse<ScimResourceType> resourceTypeScimListResponse = new ScimListResponse<>();

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

  private ScimResourceType getUserResourceType() {
    String user = "User";
    return new ScimResourceType(
        Sets.newHashSet(USER_SCHEMA), user, DOCUMENTATION_URI, user, "/Users", "User Account", USER_SCHEMA);
  }

  private ScimResourceType getGroupResourceType() {
    String group = "Group";
    return new ScimResourceType(
        Sets.newHashSet(GROUP_SCHEMA), group, DOCUMENTATION_URI, group, "/Group", "Groups", GROUP_SCHEMA);
  }

  private SchemaResource getServiceProviderConfigSchemaResource() {
    List<ScimAttributeResource> scimAttributeResourceList = new ArrayList<>();
    ScimAttributeResource supportedAttribute = getSupportedAttribute();
    scimAttributeResourceList.add(getPatchAttribute(supportedAttribute));
    scimAttributeResourceList.add(
        getBulkAttribute(supportedAttribute, getMaxOperationsAttribute(), getMaxPayloadSizeAttribute()));
    scimAttributeResourceList.add(getFilterAttribute(supportedAttribute, getMaxResultsAttribute()));
    scimAttributeResourceList.add(getChangePasswordAttribute(supportedAttribute));
    scimAttributeResourceList.add(getSortAttribute(supportedAttribute));
    scimAttributeResourceList.add(getAuthenticationSchemesAttribute());
    return new SchemaResource(SCHEMA_SERVICE_PROVIDER_CONFIG, "Service Provider Configuration",
        DESCRIPTION_SERVICE_PROVIDER_CONFIG, scimAttributeResourceList);
  }

  private SchemaResource getResourceTypesSchemaResource() {
    List<ScimAttributeResource> scimAttributeResourceList = new ArrayList<>();
    ScimAttributeResource nameAttribute = getNameAttribute(
        "The resource type name. When applicable, service providers MUST specify the name, e.g., 'User'.", false);
    scimAttributeResourceList.add(nameAttribute);
    scimAttributeResourceList.add(getEndPointAttribute());
    scimAttributeResourceList.add(getSchemaAttribute());
    return new SchemaResource(
        SCHEMA_RESOURCE_TYPE, "ResourceType", DESCRIPTION_RESOURCE_TYPE, scimAttributeResourceList);
  }

  private SchemaResource getSchemasSchemaResource() {
    List<ScimAttributeResource> scimAttributeResourceList = new ArrayList<>();
    scimAttributeResourceList.add(getIdAttributeForSchema());
    ScimAttributeResource nameAttributeForSchema = getNameAttribute(
        "The schema's human-readable name. When applicable, service providers MUST specify the name, e.g., 'User'.",
        false);
    scimAttributeResourceList.add(nameAttributeForSchema);
    scimAttributeResourceList.add(getSubAttributesAttributeForSchema());
    return new SchemaResource(ID_SCHEMA, "Schema", DESCRIPTION_SCHEMA, scimAttributeResourceList);
  }

  private SchemaResource getUserSchemaResource() {
    List<ScimAttributeResource> scimAttributeResourceList = new ArrayList<>();
    scimAttributeResourceList.add(getUserNameAttribute());
    scimAttributeResourceList.add(getExternalIdAttribute());
    scimAttributeResourceList.add(getNameAttributeOfUser());
    scimAttributeResourceList.add(getDisplayNameAttribute());
    scimAttributeResourceList.add(getActiveAttribute());
    scimAttributeResourceList.add(getEmailsAttribute());
    return new SchemaResource(USER_SCHEMA, "User", "User Account", scimAttributeResourceList);
  }

  private SchemaResource getGroupSchemaResource() {
    List<ScimAttributeResource> scimAttributeResourceList = new ArrayList<>();
    scimAttributeResourceList.add(getDisplayNameAttributeForGroup());
    scimAttributeResourceList.add(getMembersAttribute());
    return new SchemaResource(GROUP_SCHEMA, "Group", "Group", scimAttributeResourceList);
  }
}