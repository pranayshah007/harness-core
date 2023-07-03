// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package grpc

import (
	"context"
	"google.golang.org/grpc"

	"google.golang.org/grpc/metadata"
)

const (
	taskIdKey = "taskId"
)

func LogContextInterceptor(server *scmServer) grpc.UnaryServerInterceptor {
	return func(
		ctx context.Context,
		req interface{},
		info *grpc.UnaryServerInfo,
		handler grpc.UnaryHandler,
	) (resp interface{}, err error) {
		ctx = context.WithValue(ctx, "logger", server.log.With(taskIdKey, getTaskIdFromMetadata(ctx)))
		// Call the gRPC handler to process the request
		resp, err = handler(ctx, req)
		return resp, err
	}
}

func getTaskIdFromMetadata(ctx context.Context) string {
	md, ok := metadata.FromIncomingContext(ctx)
	if ok {
		taskId := md.Get(taskIdKey)
		if len(taskId) > 0 {
			return taskId[0]
		}
	}
	return ""
}
