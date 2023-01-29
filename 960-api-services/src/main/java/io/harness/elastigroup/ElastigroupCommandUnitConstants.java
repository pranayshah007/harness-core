/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.elastigroup;

public enum ElastigroupCommandUnitConstants {
  FETCH_STARTUP_SCRIPT {
    @Override
    public String toString() {
      return "Fetch Startup Script";
    }
  },
  FETCH_ELASTIGROUP_CONFIGURATION {
    @Override
    public String toString() {
      return "Fetch Elastigroup Configuration";
    }
  },
  CREATE_ELASTIGROUP {
    @Override
    public String toString() {
      return "Create Elastigroup";
    }
  },
  UPSCALE {
    @Override
    public String toString() {
      return "Upscale Elastigroup";
    }
  },
  UPSCALE_STEADY_STATE {
    @Override
    public String toString() {
      return "Upscale Steady State";
    }
  },
  DOWNSCALE {
    @Override
    public String toString() {
      return "Downscale Elastigroup";
    }
  },
  DOWNSCALE_STEADY_STATE {
    @Override
    public String toString() {
      return "Downscale Steady State";
    }
  },
  SWAP_TARGET_GROUP {
    @Override
    public String toString() {
      return "Swap Target Group";
    }
  }
}
