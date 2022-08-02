/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.reflection.HarnessReflections.CLASSPATH_METADATA_FILE_NAME;

import io.harness.packages.HarnessPackages;

import io.dropwizard.cli.Command;
import io.dropwizard.setup.Bootstrap;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.reflections.Reflections;
import org.reflections.serializers.JsonSerializer;

@Slf4j
public class ScanClasspathMetadataCommand extends Command {
  public ScanClasspathMetadataCommand() {
    super("scan-classpath-metadata", "");
  }

  @Override
  public void configure(Subparser subparser) {}

  @Override
  public void run(Bootstrap<?> bootstrap, Namespace namespace) throws Exception {
    String savePath = Paths.get(System.getProperty("user.dir"), CLASSPATH_METADATA_FILE_NAME).toString();
    new Reflections(HarnessPackages.IO_HARNESS, HarnessPackages.SOFTWARE_WINGS).save(savePath, new JsonSerializer());
  }
}
