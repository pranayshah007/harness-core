// Copyright 2021 Harness Inc. All rights reserved.
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

var azureToken = os.Getenv("AZURE_TOKEN")

const (
	organization = "tphoney"
	project      = "test_project"
	repoID       = "fde2d21f-13b9-4864-a995-83329045289a"
)

func TestCreateListAndDeleteWebhookAzure(t *testing.T) {
	if azureToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.CreateWebhookRequest{
		Slug:   repoID,
		Target: "https://example.com",
		NativeEvents: &pb.NativeEvents{
			NativeEvents: &pb.NativeEvents_Azure{
				Azure: &pb.AzureWebhookEvents{
					Events: pb.AzureWebhookEvent_AZURE_PULLREQUEST_CREATED,
				},
			},
		},
		SkipVerify: true,
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := CreateWebhook(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, int32(200), got.Status, "Correct http response")
	assert.Equal(t, pb.AzureWebhookEvent_AZURE_PULLREQUEST_CREATED, got.GetWebhook().GetNativeEvents().GetAzure().GetEvents(), "created a webhook 'asdas' event")

	list := &pb.ListWebhooksRequest{
		Slug: repoID,
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}
	got2, err2 := ListWebhooks(context.Background(), list, log.Sugar())

	assert.Nil(t, err2, "no errors")
	assert.LessOrEqual(t, 1, len(got2.Webhooks), "there is 1 webhook")

	del := &pb.DeleteWebhookRequest{
		Slug: repoID,
		Id:   got.Webhook.Id,
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}
	got3, err3 := DeleteWebhook(context.Background(), del, log.Sugar())

	assert.Nil(t, err3, "no errors")
	assert.Equal(t, int32(204), got3.Status, "Correct http response")
}
