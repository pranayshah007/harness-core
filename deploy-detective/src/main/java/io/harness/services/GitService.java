/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.services;

import io.harness.deploydetective.beans.CommitDetails;
import io.harness.deploydetective.beans.CommitDetailsRequest;

import java.io.IOException;
import java.util.List;

public interface GitService {
  List<CommitDetails> getCommitList(CommitDetailsRequest commitDetailsRequest) throws IOException;
}
