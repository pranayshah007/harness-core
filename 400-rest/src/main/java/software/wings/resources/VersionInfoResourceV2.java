/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

@PublicApi
@Path("/version")
@Produces(MediaType.APPLICATION_JSON)
public class VersionInfoResourceV2 {
  private final Map<String, String> versionInfo;

  public VersionInfoResource(Map<String, String> versionInfo) {
    this.versionInfo = versionInfo;
  }

  @GET
  public Map<String, String> getVersionInfo() {
    return versionInfo;
  }
}