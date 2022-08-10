/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.reconciliation;

public enum DetectionStatus {
  SUCCESS, /*No issues found*/
  MISSING_RECORDS_DETECTED, /*There were some records missing in the timeScaleDB*/
  DUPLICATE_DETECTED, /*There were some records that were found to be duplicate in timeScaleDB*/
  STATUS_MISMATCH_DETECTED, /*There were some mismatched records in mongoDB and timeScaleDB*/
  DELETED_RECORDS_DETECTED, /*There were some records present in the timeScaleDB not fount in mongo*/
  DUPLICATE_DETECTED_MISSING_RECORDS_DETECTED, /*Some records were missing and some duplicates were found*/
  DELETED_RECORDS_DETECTED_MISSING_RECORDS_DETECTED, /*Some records were missing and some deleted records were found*/
  DUPLICATE_DETECTED_STATUS_MISMATCH_DETECTED, /*Some records were mismatched and some duplicates were found*/
  MISSING_RECORDS_DETECTED_STATUS_MISMATCH_DETECTED, /*Some records were missing and some were mismatched*/
  DUPLICATE_DETECTED_MISSING_RECORDS_DETECTED_STATUS_MISMATCH_DETECTED, /*Some records were missing, some were
                                                                           mismatched and some duplicates were found*/
  ERROR /*Detection failed*/
}
