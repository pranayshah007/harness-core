/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.envGroup.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class DocumentationConstants {
  public static final String EnvironmentGroupRequestDTO =
      "{\"name\":\"environmentGroup\",\"identifier\":\"environmentGroupId\",\"orgIdentifier\":\"default\",\"projectIdentifier\":\"projectIdentifier\",\"yaml\":\"environmentGroup:\\n  name: environmentGroup\\n  identifier: environmentGroupId\\n  description: \\\"\\\"\\n  tags: {}\\n  orgIdentifier: default\\n  projectIdentifier: projectIdentifier\\n  envIdentifiers:\\n    - EnvironmentId1\\n    - EnvironmentId2\\n\"}";
}