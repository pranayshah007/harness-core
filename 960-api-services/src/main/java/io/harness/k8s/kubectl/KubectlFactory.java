/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.kubectl;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.k8s.kubectl.Kubectl.ClientType;

import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class KubectlFactory {
  private static final String DEFAULT_KUBECTL_VERSION = "1.19";
  private static final String DEFAULT_OC_VERSION = "4.2.16";

  private KubectlFactory() {
    throw new IllegalStateException("Utility class");
  }

  public static Kubectl getKubectlClient(String kubectlPath, String configPath, String workingDir) {
    return getClientWithVersion(Kubectl.client(kubectlPath, configPath), workingDir);
  }

  public static Kubectl getOpenShiftClient(String kubectlPath, String configPath, String workingDir) {
    return getClientWithVersion(OcClient.client(kubectlPath, configPath), workingDir);
  }

  private static Kubectl getClientWithVersion(Kubectl client, String workingDir) {
    VersionCommand versionCommand = client.version().clientOnly().jsonVersion();
    Version version;
    try {
      JSONObject versionOutput =
          new JSONObject(versionCommand.execute(workingDir, null, null, false, Collections.emptyMap()).outputUTF8())
              .getJSONObject("clientVersion");
      version = Version.parse(getVersionFromJsonOutput(versionOutput, client.getClientType()));
      log.debug(format("%s client with version %s has been created", client.getClientType(), version));
    } catch (Exception ex) {
      String defaultVersion = getDefaultVersion(client.getClientType());
      log.warn(
          format("Failed to get %s version, defaulting to version: %s", client.getClientType(), defaultVersion), ex);
      version = Version.parse(defaultVersion);
    }
    client.setVersion(version);
    return client;
  }

  private static String getVersionFromJsonOutput(JSONObject versionOutput, ClientType clientType) {
    switch (clientType) {
      case KUBECTL:
        return format("%s.%s", versionOutput.getString("major"), versionOutput.getString("minor"));
      case OC:
        return versionOutput.getString("gitVersion");
      default:
        throw new IllegalStateException("Unexpected value of client type: " + clientType);
    }
  }

  private static String getDefaultVersion(ClientType clientType) {
    switch (clientType) {
      case KUBECTL:
        return DEFAULT_KUBECTL_VERSION;
      case OC:
        return DEFAULT_OC_VERSION;
      default:
        throw new IllegalStateException("Unexpected value for client type: " + clientType);
    }
  }
}
