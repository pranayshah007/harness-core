// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package git

import (
	"context"
	"os"
	"testing"

	"github.com/harness/harness-core/commons/go/lib/logs"
	pb "github.com/harness/harness-core/product/ci/scm/proto"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

var (
	harnessToken = os.Getenv("HARNESS_TOKEN")
	// harnessEndpoint = "http://localhost:3000" // local
	// harnessSlug     = "1"                     // local
	harnessEndpoint     = "https://qa.harness.io/gateway/code"
	harnessAccount      = "px7xd_BFRCi-pfWPYXVjvw"
	harnessOrganization = "default"
	harnessProject      = "codeciintegration"
	harnessSlug         = "thomas"
)

func TestRepoListHarness(t *testing.T) {
	if harnessToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.GetUserReposRequest{
		Provider: &pb.Provider{
			Hook: &pb.Provider_Harness{
				Harness: &pb.HarnessProvider{
					Provider: &pb.HarnessProvider_HarnessAccessToken{
						HarnessAccessToken: &pb.HarnessAccessToken{
							AccessToken:  harnessToken,
							Account:      harnessAccount,
							Organization: harnessOrganization,
							Project:      harnessProject,
						},
					},
				},
			},
			Debug:    true,
			Endpoint: harnessEndpoint,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := GetUserRepos(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.GreaterOrEqual(t, len(got.Repos), 1, "There is at least one repo")
}

func TestGetUserRepoHarness(t *testing.T) {
	if harnessToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.GetUserRepoRequest{
		Slug: harnessSlug,
		Provider: &pb.Provider{
			Hook: &pb.Provider_Harness{
				Harness: &pb.HarnessProvider{
					Provider: &pb.HarnessProvider_HarnessAccessToken{
						HarnessAccessToken: &pb.HarnessAccessToken{
							AccessToken:  harnessToken,
							Account:      harnessAccount,
							Organization: harnessOrganization,
							Project:      harnessProject,
						},
					},
				},
			},
			Debug:    true,
			Endpoint: harnessEndpoint,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := GetUserRepo(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, got.Repo.Name, harnessSlug, "The repo slug is correct")
}
