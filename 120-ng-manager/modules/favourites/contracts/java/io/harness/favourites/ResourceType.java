package io.harness.favourites;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PL) public enum ResourceType { PROJECT, CONNECTOR, PIPELINE, DELEGATE, SECRET }
