/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.elastigroup;

public enum ElastigroupCommandUnitConstants {
  CREATE_SETUP {
    @Override
    public String toString() {
      return "Create Setup";
    }
  },
  FETCH_STARTUP_SCRIPT {
    @Override
    public String toString() {
      return "Fetch Startup Scripts";
    }
  },
  FETCH_ELASTIGROUP_JSON {
    @Override
    public String toString() {
      return "Fetch Elastigroup Json";
    }
  }
}
