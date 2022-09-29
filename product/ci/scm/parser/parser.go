// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package parser

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/drone/go-scm-codecommit/codecommit"
	"github.com/drone/go-scm/scm"
	"github.com/drone/go-scm/scm/driver/azure"
	"github.com/drone/go-scm/scm/driver/bitbucket"
	"github.com/drone/go-scm/scm/driver/gitea"
	"github.com/drone/go-scm/scm/driver/github"
	"github.com/drone/go-scm/scm/driver/gitlab"
	"github.com/drone/go-scm/scm/driver/gogs"
	"github.com/drone/go-scm/scm/driver/stash"
	"github.com/harness/harness-core/commons/go/lib/utils"
	"github.com/harness/harness-core/product/ci/scm/converter"
	pb "github.com/harness/harness-core/product/ci/scm/proto"
	"go.uber.org/zap"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

const (
	gogsURI       = "api.gogs.com"
	giteaURI      = "api.gitea.com"
	defaultMethod = "POST"
	defaultPath   = "/"
)

// ParseWebhook parses webhook payload into protobuf ParseWebhookResponse format
func ParseWebhook(ctx context.Context, in *pb.ParseWebhookRequest,
	log *zap.SugaredLogger) (*pb.ParseWebhookResponse, error) {
	start := time.Now()
	webhook, err := parseWebhookRequest(in)
	if err != nil {
	    message := "Failed to parse input webhook payload"
	    if errors.Is(err, scm.ErrUnknownEvent) {
	        message = "Unsupported webhook event"
	    }
		log.Errorw(
			message,
			"input", in.String(),
			"elapsed_time_ms", utils.TimeSince(start),
			zap.Error(err),
		)
		return nil, err
	}

	switch event := webhook.(type) {
	case *scm.PullRequestHook:
		pr, prHookErr := converter.ConvertPRHook(event)
		if prHookErr != nil {
			return nil, prHookErr
		}
		log.Infow("Successfully parsed pr webhook", "elapsed_time_ms", utils.TimeSince(start))
		return &pb.ParseWebhookResponse{
			Hook: &pb.ParseWebhookResponse_Pr{
				Pr: pr,
			},
		}, nil
	case *scm.PushHook:
		push, pushHookErr := converter.ConvertPushHook(event)
		if pushHookErr != nil {
			return nil, pushHookErr
		}
		log.Infow("Successfully parsed push webhook", "elapsed_time_ms", utils.TimeSince(start))
		return &pb.ParseWebhookResponse{
			Hook: &pb.ParseWebhookResponse_Push{
				Push: push,
			},
		}, nil
	case *scm.IssueCommentHook:
		comment, issueCommentHookErr := converter.ConvertIssueCommentHook(event)
		if issueCommentHookErr != nil {
			return nil, issueCommentHookErr
		}
		log.Infow("Successfully parsed issue comment", "elapsed_time_ms", utils.TimeSince(start))
		return &pb.ParseWebhookResponse{
			Hook: &pb.ParseWebhookResponse_Comment{
				Comment: comment,
			},
		}, nil

	case *scm.BranchHook:
		branch, branchHookErr := converter.ConvertBranchHook(event)
		if branchHookErr != nil {
			return nil, branchHookErr
		}
		log.Infow("Successfully parsed pr webhook", "elapsed_time_ms", utils.TimeSince(start))
		return &pb.ParseWebhookResponse{
			Hook: &pb.ParseWebhookResponse_Branch{
				Branch: branch,
			},
		}, nil

	default:
		log.Errorw(
			"Unsupported webhook event",
			"event", event,
			"elapsed_time_ms", utils.TimeSince(start),
			"body", in.GetBody(),
			"header", in.GetHeader(),
			"provider", in.GetProvider(),
			zap.Error(err),
		)
		return nil, status.Errorf(codes.InvalidArgument,
			fmt.Sprintf("Unsupported webhook event %v", event))
	}
}

// parseWebhookRequest parses incoming request and convert it to scm.Webhook
func parseWebhookRequest(in *pb.ParseWebhookRequest) (scm.Webhook, error) {
	body := strings.NewReader(in.GetBody())
	r, err := http.NewRequestWithContext(context.Background(), defaultMethod, defaultPath, body)
	if err != nil {
		return nil, err
	}
	for _, field := range in.GetHeader().GetFields() {
		for _, v := range field.GetValues() {
			r.Header.Set(field.GetKey(), v)
		}
	}

	c, err := getClient(in.GetProvider())
	if err != nil {
		return nil, err
	}

	h, err := c.Webhooks.Parse(r, func(scm.Webhook) (string, error) {
		return in.GetSecret(), nil
	})
	if err != nil {
		return nil, err
	}
	return h, nil
}

// getClient returns appropriate scm client.
func getClient(p pb.GitProvider) (*scm.Client, error) {
	switch p {
	case pb.GitProvider_BITBUCKET:
		return bitbucket.NewDefault(), nil
	case pb.GitProvider_GITEA:
		return gitea.New(giteaURI)
	case pb.GitProvider_GITHUB:
		return github.NewDefault(), nil
	case pb.GitProvider_GITLAB:
		return gitlab.NewDefault(), nil
	case pb.GitProvider_GOGS:
		return gogs.New(gogsURI)
	case pb.GitProvider_STASH:
		return stash.NewDefault(), nil
	case pb.GitProvider_CODECOMMIT:
		return codecommit.NewDefault(), nil
	case pb.GitProvider_AZURE:
		return azure.NewDefault("empty", "empty"), nil
	default:
		return nil, status.Errorf(codes.InvalidArgument,
			fmt.Sprintf("Unsupported git provider %s", p.String()))
	}
}
