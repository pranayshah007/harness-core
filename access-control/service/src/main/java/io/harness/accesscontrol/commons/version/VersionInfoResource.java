package io.harness.accesscontrol.commons.version;

import io.harness.rest.RestResponse;
import io.harness.security.annotations.PublicApi;
import io.harness.version.VersionInfoManager;
import io.harness.version.VersionPackage;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api("version")
@Path("/version")
@Produces(MediaType.APPLICATION_JSON)
@PublicApi
public class VersionInfoResource {
  private final VersionInfoManager versionInfoManager;

  @Inject
  public VersionInfoResource(VersionInfoManager versionInfoManager) {
    this.versionInfoManager = versionInfoManager;
  }

  @GET
  @ApiOperation(value = "get version for access control service", nickname = "getAccessControlServiceVersion")
  public RestResponse<VersionPackage> get() {
    return new RestResponse<>(VersionPackage.builder().versionInfo(versionInfoManager.getVersionInfo()).build());
  }
}
