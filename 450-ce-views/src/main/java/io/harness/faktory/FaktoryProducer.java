/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.faktory;

import java.io.IOException;

public class FaktoryProducer {
  public static String push(String jobType, String jobQueue, String jsonPayload) throws IOException {
    FaktoryClient client = new FaktoryClient();
    FaktoryJob job = new FaktoryJob(JobType.of(jobType), JobQueue.of(jobQueue), jsonPayload);
    client.push(job);
    return job.getJid();
  }
}