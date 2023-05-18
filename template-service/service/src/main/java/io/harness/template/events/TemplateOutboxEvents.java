/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.events;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDC)
public class TemplateOutboxEvents {
  public static final String TEMPLATE_VERSION_CREATED = "TemplateVersionCreated";
  public static final String TEMPLATE_VERSION_UPDATED = "TemplateVersionUpdated";
  public static final String TEMPLATE_VERSION_DELETED = "TemplateVersionDeleted";
  public static final String TEMPLATE_VERSION_FORCE_DELETED = "TemplateVersionForceDeleted";
}
