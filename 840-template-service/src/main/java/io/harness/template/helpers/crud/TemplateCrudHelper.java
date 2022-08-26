/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers.crud;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.template.entity.TemplateEntity;

import java.util.List;

@OwnedBy(HarnessTeam.CDC)
public interface TemplateCrudHelper {
  boolean supportsReferences();
  List<EntityDetailProtoDTO> getReferences(TemplateEntity templateEntity, String entityYaml);
}
