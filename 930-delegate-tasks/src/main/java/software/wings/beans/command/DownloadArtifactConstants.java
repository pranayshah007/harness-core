/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import lombok.experimental.UtilityClass;

@UtilityClass
class DownloadArtifactConstants {
  public static final String AUTHORIZATION = "${AUTHORIZATION}";
  public static final String URI = "${URI}";
  public static final String OUT_FILE = "${OUT_FILE}";

  public static final String PWSH_ARTIFACTORY_NO_CREDENTIALS =
      "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\n"
      + "$ProgressPreference = 'SilentlyContinue'\n"
      + "$var1 = $env:HARNESS_ENV_PROXY\n"
      + "$var2 = $env:HTTP_PROXY\n"
      + "if ( ([string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
      + " Write-Host \"HTTP_PROXY environment variable not found or empty\"\n"
      + "}\n"
      + "if ( (-not [string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
      + " Write-Host \"Using HTTP_PROXY environment variable\"\n"
      + " Invoke-WebRequest -Uri \"${URI}\" -Headers $Headers -OutFile \"${OUT_FILE}\" -Proxy \"$env:HTTP_PROXY\"\n"
      + "} else {\n"
      + " Invoke-WebRequest -Uri \"${URI}\" -Headers $Headers -OutFile \"${OUT_FILE}\"\n"
      + "}";
  public static final String PWSH_ARTIFACTORY_USING_CREDENTIALS = "$Headers = @{\n"
      + "    Authorization = \"${AUTHORIZATION}\"\n"
      + "}\n"
      + "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\n"
      + "$ProgressPreference = 'SilentlyContinue'\n"
      + "$var1 = $env:HARNESS_ENV_PROXY\n"
      + "$var2 = $env:HTTP_PROXY\n"
      + "if ( ([string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
      + " Write-Host \"HTTP_PROXY environment variable not found or empty\"\n"
      + "}\n"
      + "if ( (-not [string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
      + " Write-Host \"Using HTTP_PROXY environment variable\"\n"
      + " Invoke-WebRequest -Uri \"${URI}\" -Headers $Headers -OutFile \"${OUT_FILE}\" -Proxy \"$env:HTTP_PROXY\"\n"
      + "} else {\n"
      + " Invoke-WebRequest -Uri \"${URI}\" -Headers $Headers -OutFile \"${OUT_FILE}\"\n"
      + "}";
}
