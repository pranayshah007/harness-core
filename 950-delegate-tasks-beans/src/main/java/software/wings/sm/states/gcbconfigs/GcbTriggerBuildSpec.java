/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.gcbconfigs;

import software.wings.beans.NameValuePair;

import java.util.List;
import lombok.Data;

@Data
public class GcbTriggerBuildSpec {
  public enum GcbTriggerSource { TAG, BRANCH, COMMIT }

  private String name;
  private String sourceId;
  private GcbTriggerSource source;
  private List<NameValuePair> substitutions;
}
