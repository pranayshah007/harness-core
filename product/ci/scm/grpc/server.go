// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package grpc

import (
	"fmt"
	"net"
	"time"

	"github.com/grpc-ecosystem/go-grpc-middleware"
	grpc_recovery "github.com/grpc-ecosystem/go-grpc-middleware/recovery"
	pb "github.com/harness/harness-core/product/ci/scm/proto"
	"go.uber.org/zap"
	"google.golang.org/grpc"
	"google.golang.org/grpc/health"
	healthpb "google.golang.org/grpc/health/grpc_health_v1"
)

const (
	hardStopWaitTimeout = 10
)

//go:generate mockgen -source server.go -package=grpc -destination mocks/server_mock.go SCMServer

// SCMServer implements a GRPC server
type SCMServer interface {
	Start()
	Stop()
}

type scmServer struct {
	port       uint
	unixSocket string
	listener   net.Listener
	grpcServer *grpc.Server
	log        *zap.SugaredLogger
	stopCh     chan bool
}

// NewSCMServer constructs a new SCMServer
func NewSCMServer(port uint, unixSocket string, log *zap.SugaredLogger) (SCMServer, error) {
	var listener net.Listener
	var err error
	if unixSocket != "" {
		listener, err = net.Listen("unix", unixSocket)
		if err != nil {
			log.Errorw("NewSCMServer failure creating unix socket", "unixSocket", unixSocket, zap.Error(err))
			return nil, err
		}
	} else {
		listener, err = net.Listen("tcp", fmt.Sprintf(":%d", port))
		if err != nil {
			log.Errorw("NewSCMServer failure opening port", "port", port, zap.Error(err))
			return nil, err
		}
	}

	stopCh := make(chan bool, 1)
	server := scmServer{
		unixSocket: unixSocket,
		port:       port,
		log:        log,
		stopCh:     stopCh,
	}
	server.grpcServer = grpc.NewServer(
		grpc.UnaryInterceptor(grpc_middleware.ChainUnaryServer(
			grpc_recovery.UnaryServerInterceptor(), LogContextInterceptor(&server),
		)),
	)
	server.listener = listener
	return &server, nil
}

// Start signals the GRPC server to begin serving on the configured port
func (s *scmServer) Start() {
	healthServer := health.NewServer()
	healthServer.SetServingStatus("", healthpb.HealthCheckResponse_SERVING)

	healthpb.RegisterHealthServer(s.grpcServer, healthServer)
	pb.RegisterSCMServer(s.grpcServer, NewSCMHandler(s.stopCh))
	err := s.grpcServer.Serve(s.listener)
	if err != nil {
		s.log.Fatalw("error starting gRPC server", zap.Error(err))
	}
}

// Stop method waits for signal to stop the server and stops GRPC server upon receiving it
func (s *scmServer) Stop() {
	<-s.stopCh
	s.log.Infow("Initiating shutdown of CI scm server")
	if s.grpcServer != nil {
		// Hard stop the GRPC server if it doesn't gracefully shut down within hardStopWaitTimeout.
		go func() {
			time.Sleep(hardStopWaitTimeout * time.Second)
			s.log.Infow("Initiating hard shutdown of CI scm server")
			s.grpcServer.Stop()
		}()

		s.log.Infow("Gracefully shutting down CI scm server")
		s.grpcServer.GracefulStop()
	}
}
