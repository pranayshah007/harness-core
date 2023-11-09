/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups.events;

import static io.harness.audit.ResourceTypeConstants.RESOURCE_GROUP;

import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.scopes.AccessControlResourceScope;
import io.harness.event.Event;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;

import java.util.HashMap;
import java.util.Map;

public class ResourceGroupCreateEvent implements Event {
  String scope;
  ResourceGroup newResourceGroup;
  public static final String RESOURCE_GROUP_CREATE_EVENT = "ResourceGroupCreated";

  public ResourceGroupCreateEvent(ResourceGroup newResourceGroup, String scope) {
    this.scope = scope;
    this.newResourceGroup = newResourceGroup;
  }

  @Override
  public ResourceScope getResourceScope() {
    return new AccessControlResourceScope(scope);
  }

  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, newResourceGroup.getName());
    return Resource.builder().identifier(newResourceGroup.getIdentifier()).type(RESOURCE_GROUP).labels(labels).build();
  }

  @Override
  public String getEventType() {
    return RESOURCE_GROUP_CREATE_EVENT;
  }
}
