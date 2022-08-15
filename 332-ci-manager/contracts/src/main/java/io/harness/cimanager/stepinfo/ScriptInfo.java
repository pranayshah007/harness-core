/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.script;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScriptInfo {
  // TODO Improve it by taking proper script type and input format
  private String scriptString;

  private boolean fromTemplate;
}
