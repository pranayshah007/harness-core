
/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.remote;

import com.example.annotations.PublicApi;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

@PublicApi
@Path("/version")
@Produces(MediaType.APPLICATION_JSON)
public class VersionInfoResource {
  private final VersionInfoManagerV2 versionInfoManager;

  public VersionInfoResource(VersionInfoManagerV2 versionInfoManager) {
    this.versionInfoManager = versionInfoManager;
  }

  @GET
  public Map<String, String> getVersionInfo() {
    return versionInfoManager.getVersionInfo(); // Assuming this method returns a Map
  }
}
