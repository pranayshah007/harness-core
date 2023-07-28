/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.spec.server.ssca.v1.model.SbomProcessRequestBody;

import java.text.ParseException;

public interface ProcessSbomWorkflowService {
  String processSBOM(String accountId, String orgIdentifier, String projectIdentifier,
      SbomProcessRequestBody sbomProcessRequestBody) throws ParseException;
}
