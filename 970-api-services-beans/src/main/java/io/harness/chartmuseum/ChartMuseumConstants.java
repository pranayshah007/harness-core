/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.chartmuseum;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class ChartMuseumConstants {
  public final int CHART_MUSEUM_SERVER_START_RETRIES = 5;
  public final int PORTS_START_POINT = 35000;
  public final int PORTS_BOUND = 5000;
  public final int SERVER_HEALTH_CHECK_RETRIES = 24;
  public final int HEALTH_CHECK_TIME_GAP_SECONDS = 5; // 24*5 = 120 seconds is the timeout for health check
  public final Pattern VERSION_PATTERN = Pattern.compile("v?(\\d+\\.\\d+\\.\\d+)");

  public final int CHART_MUSEUM_SERVICE_START_TIMEOUT = 60 * 1000;
  public final String CHART_MUSEUM_SERVER_PROTOCOL = "http";
  public final String CHART_MUSEUM_SERVER_HOST = "127.0.0.1";
  public final String CHART_MUSEUM_SERVER_PORT = "${PORT}";
  public final String CHART_MUSEUM_SERVER_URL =
      format("%s://%s:%s", CHART_MUSEUM_SERVER_PROTOCOL, CHART_MUSEUM_SERVER_HOST, CHART_MUSEUM_SERVER_PORT);

  public final String NO_SUCH_BBUCKET_ERROR_CODE = "NoSuchBucket";
  public final String NO_SUCH_BBUCKET_ERROR = "NoSuchBucket: The specified bucket does not exist";

  public final String INVALID_ACCESS_KEY_ID_ERROR_CODE = "InvalidAccessKeyId";
  public final String INVALID_ACCESS_KEY_ID_ERROR =
      "InvalidAccessKeyId: The AWS Access Key Id you provided does not exist in our records.";

  public final String SIGNATURE_DOES_NOT_MATCH_ERROR_CODE = "SignatureDoesNotMatch";
  public final String SIGNATURE_DOES_NOT_MATCH_ERROR =
      "SignatureDoesNotMatch: The request signature we calculated does not match the signature you provided. Check your key and signing method";

  public final String BUCKET_REGION_ERROR_CODE = "BucketRegionError";

  public final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
  public final String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";

  public final String AMAZON_S3_COMMAND_TEMPLATE =
      " --port=${PORT} --storage=amazon --storage-amazon-bucket=${BUCKET_NAME} --storage-amazon-prefix=${FOLDER_PATH} --storage-amazon-region=${REGION}";

  public final String DEBUG = " --debug";

  public final String AMAZON_S3_COMMAND_TEMPLATE_DEBUG = AMAZON_S3_COMMAND_TEMPLATE + DEBUG;

  public final String GOOGLE_APPLICATION_CREDENTIALS = "GOOGLE_APPLICATION_CREDENTIALS";

  public final String GCS_COMMAND_TEMPLATE =
      " --port=${PORT} --storage=google --storage-google-bucket=${BUCKET_NAME} --storage-google-prefix=${FOLDER_PATH}";

  public final String GCS_COMMAND_TEMPLATE_DEBUG = GCS_COMMAND_TEMPLATE + DEBUG;

  public final String DISABLE_STATEFILES = "--disable-statefiles --index-limit 100";

  public final String VERSION = "--version";

  public final String ADDRESS_BIND_ERROR = "Reason: Chartmuseum tried to start on port [%s] which is already in use.";
  public final String ADDRESS_BIND_CODE = "bind: address already in use";
}
