// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package grpcclient

import (
	"fmt"
	"time"

	grpc_retry "github.com/grpc-ecosystem/go-grpc-middleware/retry"
	pb "github.com/harness/harness-core/product/ci/addon/proto"
	"go.uber.org/zap"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
)

//go:generate mockgen -source addon_client.go -package=grpcclient -destination mocks/addon_client_mock.go AddonClient

const (
	backoffTime = 300 * time.Millisecond
	maxRetries  = 30 // Max retry time of 9 seconds
)

// AddonClient implements a GRPC client to communicate with CI addon
type AddonClient interface {
	CloseConn() error
	Client() pb.AddonClient
}

type addonClient struct {
	port       uint
	conn       *grpc.ClientConn
	log        *zap.SugaredLogger
	grpcClient pb.AddonClient
}

// NewAddonClient creates a CI addon client
func NewAddonClient(port uint, log *zap.SugaredLogger) (AddonClient, error) {
	// Default gRPC Call options - can be made configurable if the need arises
	// Retries are ENABLED by default for all RPCs on the below codes. To disable retries, pass in a zero value
	// Example: client.ExecuteStep(ctx, req, grpc_retry.WithMax(0))
	// With default configuration, total retry time is approximately 2 seconds
	opts := []grpc_retry.CallOption{
		grpc_retry.WithBackoff(grpc_retry.BackoffLinear(backoffTime)),
		grpc_retry.WithCodes(codes.ResourceExhausted, codes.Unavailable),
		grpc_retry.WithMax(maxRetries),
	}

	// Create a connection on the port and disable transport security (TLS/SSL)
	// Configure interceptors for stream/unary RPCs to use default gRPC opts
	// TODO: Add TLS (CI-119)
	conn, err := grpc.Dial(
		fmt.Sprintf(":%d", port),
		grpc.WithInsecure(),
		grpc.WithStreamInterceptor(grpc_retry.StreamClientInterceptor(opts...)),
		grpc.WithUnaryInterceptor(grpc_retry.UnaryClientInterceptor(opts...)),
		grpc.WithNoProxy())
	if err != nil {
		log.Errorw("Could not create a client to CI Addon", "error_msg", zap.Error(err))
		return nil, err
	}

	c := pb.NewAddonClient(conn)
	client := addonClient{
		port:       port,
		log:        log,
		conn:       conn,
		grpcClient: c,
	}
	return &client, nil
}

func (c *addonClient) CloseConn() error {
	if err := c.conn.Close(); err != nil {
		return err
	}
	return nil
}

func (c *addonClient) Client() pb.AddonClient {
	return c.grpcClient
}
