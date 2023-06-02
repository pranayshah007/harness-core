/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class DownloadWinRmScript {
  public static final String AUTHORIZATION = "${AUTHORIZATION}";
  public static final String URI = "${URI}";
  public static final String OUT_FILE = "${OUT_FILE}";
  public static final String X_AMZ_CONTENT_SHA256 = "${X_AMZ_CONTENT_SHA256}";
  public static final String X_AMZ_DATE = "${X_AMZ_DATE}";
  public static final String X_AMZ_SECURITY_TOKEN = "${X_AMZ_SECURITY_TOKEN}";

  public static final String DOWNLOAD_ARTIFACT_BY_PROXY_PS = ""
      + "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\n"
      + "$ProgressPreference = 'SilentlyContinue'\n"
      + "$var1 = $env:HARNESS_ENV_PROXY\n"
      + "$var2 = $env:HTTP_PROXY\n"
      + "if ( ([string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
      + " Write-Host \"HTTP_PROXY environment variable not found or empty\"\n"
      + "}\n"
      + "if ( (-not [string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
      + " Write-Host \"Using HTTP_PROXY environment variable\"\n"
      + " Invoke-WebRequest -Uri \"${URI}\" -OutFile \"${OUT_FILE}\" -Proxy \"$env:HTTP_PROXY\"\n"
      + "} else {\n"
      + " Invoke-WebRequest -Uri \"${URI}\" -OutFile \"${OUT_FILE}\"\n"
      + "}";

  public static final String DOWNLOAD_ARTIFACT_USING_CREDENTIALS_BY_PROXY_PS = ""
      + "$Headers = @{\n"
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

  public static final String DOWNLOAD_ARTIFACT_USING_CREDENTIALS_AND_SECURITY_TOKEN_BY_PROXY_PS = ""
      + "$Headers = @{\n"
      + "    Authorization = \"${AUTHORIZATION}\"\n"
      + "    \"x-amz-content-sha256\" = \"${X_AMZ_CONTENT_SHA256}\"\n"
      + "    \"x-amz-date\" = \"${X_AMZ_DATE}\"\n"
      + "    ${X_AMZ_SECURITY_TOKEN}\n"
      + "}\n"
      + "$ProgressPreference = 'SilentlyContinue'\n"
      + "$var1 = $env:HARNESS_ENV_PROXY\n"
      + "$var2 = $env:HTTP_PROXY\n"
      + "if ( ([string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
      + " Write-Host \"HTTP_PROXY environment variable not found or empty\"\n"
      + "}\n"
      + "if ( (-not [string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
      + " Write-Host \"Using HTTP_PROXY environment variable\"\n"
      + " Invoke-WebRequest -Uri \"${URI}\" -Headers $Headers -OutFile (New-Item -Path \"${OUT_FILE}\" -Force) -Proxy \"$env:HTTP_PROXY\"\n"
      + "} else {\n"
      + " Invoke-WebRequest -Uri \"${URI}\" -Headers $Headers -OutFile (New-Item -Path \"${OUT_FILE}\" -Force)\n"
      + "}";

  private static final String BASE_JENKINS_DOWNLOAD_SCRIPT = ""
      + "$var1 = $env:HARNESS_ENV_PROXY\n"
      + "$var2 = $env:HTTP_PROXY\n"
      + "$webClient = New-Object System.Net.WebClient\n"
      + "if ( ([string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
      + " Write-Host \"HTTP_PROXY environment variable not found or empty\"\n"
      + "}\n"
      + "if ( (-not [string]::IsNullOrEmpty($var2)) -and  (\"true\" -eq $var1) ) {\n"
      + " Write-Host \"Using HTTP_PROXY environment variable\"\n"
      + " $webProxy = New-Object System.Net.WebProxy(\"$env:HTTP_PROXY\",$true)\n"
      + " $webClient.Proxy = $webProxy\n"
      + "}\n"
      + "$url = \"${URI}\"\n"
      + "$localfilename = \"${OUT_FILE}\"";

  public static final String JENKINS_DOWNLOAD_ARTIFACT_PS = BASE_JENKINS_DOWNLOAD_SCRIPT + "\n"
      + "$webClient.DownloadFile($url, $localfilename)\n";

  public static final String JENKINS_DOWNLOAD_ARTIFACT_USING_CREDENTIALS_PS = BASE_JENKINS_DOWNLOAD_SCRIPT + "\n"
      + "$webClient.Headers[[System.Net.HttpRequestHeader]::Authorization] = \"${AUTHORIZATION}\"\n"
      + "$webClient.DownloadFile($url, $localfilename)\n";
}
