package io.harness.migrations.all;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateVersion;
import io.harness.migrations.Migration;
import io.harness.migrations.SeedDataMigration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.DEL)
@Slf4j
public class AddSupportedDelegateImages implements Migration, SeedDataMigration {
  @Inject private HPersistence persistence;

  @Override
  public void migrate() {
    // publish all the delegate versions less than 3 months old to delegateVersion collection.
    List<String> supportedImages = Arrays.asList("harness/delegate:22.11.77610", "harness/delegate:22.11.77436",
        "harness/delegate:22.11.77227", "harness/delegate:22.11.77435", "harness/delegate:22.11.77431",
        "harness/delegate:22.10.77221", "harness/delegate:22.10.77029", "harness/delegate:22.10.77021");

    // Add minimal image to this collection.

    List<DelegateVersion> delegateVersionList = supportedImages.stream().map(image ->
        DelegateVersion.builder().delegateImage(image).validUntil(fetchExpiryFromImageTag(image)).build()).collect(Collectors.toList());
    persistence.save(delegateVersionList);
  }

  private Date fetchExpiryFromImageTag(String imageTag) {
    String[] split = imageTag.split(":");
    String version = split[1];
    Calendar calendar = Calendar.getInstance();
    SimpleDateFormat sdf = new SimpleDateFormat("yy.MM");
    try {
      calendar.setTime(sdf.parse(version));
    } catch (ParseException e) {
      log.error("Unable to parse version {}", version, e);
    }
    calendar.add(Calendar.MONTH, 3);
    return calendar.getTime();
  }
}
