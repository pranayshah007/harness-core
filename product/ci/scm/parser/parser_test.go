// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package parser

import (
	"context"
	"os"
	"testing"

	"github.com/golang/protobuf/jsonpb" //nolint:staticcheck //only used in test
	"github.com/golang/protobuf/proto"  //nolint:staticcheck //only used in test
	"github.com/harness/harness-core/commons/go/lib/logs"
	pb "github.com/harness/harness-core/product/ci/scm/proto"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestParsePRWebhookPRSuccess(t *testing.T) {
	data, _ := os.ReadFile("testdata/pr.json")
	in := &pb.ParseWebhookRequest{
		Body: string(data),
		Header: &pb.Header{
			Fields: []*pb.Header_Pair{
				{
					Key:    "X-Github-Event",
					Values: []string{"pull_request"},
				},
			},
		},
		Secret:   "",
		Provider: pb.GitProvider_GITHUB,
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := ParseWebhook(context.Background(), in, log.Sugar())
	assert.Nil(t, err)

	want := &pb.ParseWebhookResponse{}
	raw, _ := os.ReadFile("testdata/pr.json.golden")
	_ = jsonpb.UnmarshalString(string(raw), want)

	if !proto.Equal(got, want) {
		t.Errorf("Unexpected Results:\n")
		t.Log(got)
		t.Log(want)
	}
}

func TestAzureParsePRWebhookPRSuccess(t *testing.T) {
	data, _ := os.ReadFile("testdata/azure_pr.json")
	in := &pb.ParseWebhookRequest{
		Body: string(data),
		Header: &pb.Header{
			Fields: []*pb.Header_Pair{
				{
					Key:    "X-Github-Event",
					Values: []string{"pull_request"},
				},
			},
		},
		Secret:   "",
		Provider: pb.GitProvider_AZURE,
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := ParseWebhook(context.Background(), in, log.Sugar())
	assert.Nil(t, err)

	want := &pb.ParseWebhookResponse{}
	raw, _ := os.ReadFile("testdata/azure_pr.json.golden")
	jsonErr := jsonpb.UnmarshalString(string(raw), want)
	if jsonErr != nil {
		t.Errorf("JSON parsing error%s", jsonErr)
	}

	if !proto.Equal(got, want) {
		t.Errorf("Unexpected Results:\n")
		t.Log(got)
		t.Log(want)
	}
}

func TestParsePRWebhook_UnknownActionErr(t *testing.T) {
	raw, _ := os.ReadFile("testdata/pr.json")
	in := &pb.ParseWebhookRequest{
		Body: string(raw),
		Header: &pb.Header{
			Fields: []*pb.Header_Pair{
				{
					Key:    "X-Github-Event",
					Values: []string{"test"},
				},
			},
		},
		Secret:   "",
		Provider: pb.GitProvider_GITHUB,
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	_, err := ParseWebhook(context.Background(), in, log.Sugar())
	assert.NotNil(t, err)
}

func TestParsePRWebhook_UnknownErr(t *testing.T) {
	raw, _ := os.ReadFile("testdata/pr.err.json")
	in := &pb.ParseWebhookRequest{
		Body: string(raw),
		Header: &pb.Header{
			Fields: []*pb.Header_Pair{
				{
					Key:    "X-Github-Event",
					Values: []string{"pull_request"},
				},
			},
		},
		Secret:   "",
		Provider: pb.GitProvider_GITHUB,
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	ret, err := ParseWebhook(context.Background(), in, log.Sugar())
	assert.Nil(t, err)
	assert.Equal(t, ret.GetPr().GetAction(), pb.Action_UNKNOWN)
}

func TestParsePushWebhookPRSuccess(t *testing.T) {
	data, _ := os.ReadFile("testdata/push.json")
	in := &pb.ParseWebhookRequest{
		Body: string(data),
		Header: &pb.Header{
			Fields: []*pb.Header_Pair{
				{
					Key:    "X-Github-Event",
					Values: []string{"push"},
				},
			},
		},
		Secret:   "",
		Provider: pb.GitProvider_GITHUB,
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := ParseWebhook(context.Background(), in, log.Sugar())
	assert.Nil(t, err)
	assert.NotNil(t, got.GetPush())

	want := &pb.ParseWebhookResponse{}
	raw, _ := os.ReadFile("testdata/push.json.golden")
	_ = jsonpb.UnmarshalString(string(raw), want)
	if !proto.Equal(got, want) {
		t.Errorf("Unexpected Results")
		t.Log(got)
		t.Log(want)
	}
}

func TestParseCommentWebhookSuccess(t *testing.T) {
	data, _ := os.ReadFile("testdata/comment.json")
	in := &pb.ParseWebhookRequest{
		Body: string(data),
		Header: &pb.Header{
			Fields: []*pb.Header_Pair{
				{
					Key:    "X-Github-Event",
					Values: []string{"issue_comment"},
				},
			},
		},
		Secret:   "",
		Provider: pb.GitProvider_GITHUB,
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := ParseWebhook(context.Background(), in, log.Sugar())
	assert.Nil(t, err)
	assert.NotNil(t, got.GetComment())

	want := &pb.ParseWebhookResponse{}
	raw, _ := os.ReadFile("testdata/comment.json.golden")
	_ = jsonpb.UnmarshalString(string(raw), want)

	if !proto.Equal(got, want) {
		t.Errorf("Unexpected Results")
		t.Log(got)
		t.Log(want)
	}
}

func TestParseCreateBranch(t *testing.T) {
	data, _ := os.ReadFile("testdata/branch_create.json")
	in := &pb.ParseWebhookRequest{
		Body: string(data),
		Header: &pb.Header{
			Fields: []*pb.Header_Pair{
				{
					Key:    "X-Github-Event",
					Values: []string{"create"},
				},
			},
		},
		Secret:   "",
		Provider: pb.GitProvider_GITHUB,
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := ParseWebhook(context.Background(), in, log.Sugar())
	assert.Nil(t, err)
	assert.NotNil(t, got.GetBranch())

	want := &pb.ParseWebhookResponse{}
	raw, _ := os.ReadFile("testdata/branch_create.json.golden")
	err = jsonpb.UnmarshalString(string(raw), want)

	if !proto.Equal(got, want) {
		t.Errorf("Unexpected Results error: %s", err)
		t.Log(got)
		t.Log(want)
	}
}

func TestParseReleaseWebhookSuccess(t *testing.T) {
	data, _ := os.ReadFile("testdata/release.json")
	in := &pb.ParseWebhookRequest{
		Body: string(data),
		Header: &pb.Header{
			Fields: []*pb.Header_Pair{
				{
					Key:    "X-Github-Event",
					Values: []string{"release"},
				},
			},
		},
		Secret:   "",
		Provider: pb.GitProvider_GITHUB,
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := ParseWebhook(context.Background(), in, log.Sugar())
	assert.Nil(t, err)
	assert.NotNil(t, got.GetRelease())

	want := &pb.ParseWebhookResponse{}
	raw, _ := os.ReadFile("testdata/release.json.golden")
	_ = jsonpb.UnmarshalString(string(raw), want)

	if !proto.Equal(got, want) {
		t.Errorf("Unexpected Results")
		t.Log(got)
		t.Log(want)
	}
}
