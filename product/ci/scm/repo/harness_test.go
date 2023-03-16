// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package repo

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

func TestCreateListAndDeleteWebhookHarness(t *testing.T) {
	if harnessToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.CreateWebhookRequest{
		Slug:   harnessSlug,
		Name:   "drone",
		Target: "https://example.com",
		Secret: "topsecret",
		NativeEvents: &pb.NativeEvents{
			NativeEvents: &pb.NativeEvents_Harness{
				Harness: &pb.HarnessWebhookEvents{
					Events: []pb.HarnessWebhookEvent{pb.HarnessWebhookEvent_HARNESS_PULLREQ_CREATED, pb.HarnessWebhookEvent_HARNESS_PULLREQ_BRANCH_UPDATED},
				},
			},
		},
		SkipVerify: true,
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
	got, err := CreateWebhook(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, int32(200), got.Status, "Correct http response")
	assert.Equal(t, 2, len(got.GetWebhook().GetNativeEvents().GetHarness().GetEvents()), "created a webhook with 2 events")

	list := &pb.ListWebhooksRequest{
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
	got2, err2 := ListWebhooks(context.Background(), list, log.Sugar())

	assert.Nil(t, err2, "no errors")
	assert.LessOrEqual(t, 1, len(got2.Webhooks), "there is 1 webhook")

	del := &pb.DeleteWebhookRequest{
		Slug: harnessSlug,
		Id:   got.Webhook.Id,
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
	got3, err3 := DeleteWebhook(context.Background(), del, log.Sugar())

	assert.Nil(t, err3, "no errors")
	assert.Equal(t, int32(204), got3.Status, "Correct http response")
}
