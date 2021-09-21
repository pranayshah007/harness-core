package grpc

import (
	"fmt"
	"io"
	"net"
	"time"

	pb "github.com/wings-software/portal/product/ci/engine/proto"
	"go.uber.org/zap"
	"google.golang.org/grpc"
)

const (
	hardStopWaitTimeout = 10
)

//go:generate mockgen -source server.go -package=grpc -destination mocks/server_mock.go EngineServer

//EngineServer implements a GRPC server that listens to messages from lite engine
type EngineServer interface {
	Start() error
	Stop()
	GetStopChannel() chan bool
}

type engineServer struct {
	port       uint
	listener   net.Listener
	grpcServer *grpc.Server
	log        *zap.SugaredLogger
	procWriter io.Writer
	stopCh     chan bool
}

//NewEngineServer constructs a new EngineServer
func NewEngineServer(port uint, log *zap.SugaredLogger, procWriter io.Writer) (EngineServer, error) {
	listener, err := net.Listen("tcp", fmt.Sprintf(":%d", port))
	if err != nil {
		return nil, err
	}

	stopCh := make(chan bool, 1)
	server := engineServer{
		port:       port,
		log:        log,
		stopCh:     stopCh,
		procWriter: procWriter,
	}
	server.grpcServer = grpc.NewServer()
	server.listener = listener
	return &server, nil
}

//Start signals the GRPC server to begin serving on the configured port
func (s *engineServer) Start() error {
	pb.RegisterLiteEngineServer(s.grpcServer, NewEngineHandler(s.log, s.procWriter))
	pb.RegisterLogProxyServer(s.grpcServer, NewLogProxyHandler(s.log))
	pb.RegisterTiProxyServer(s.grpcServer, NewTiProxyHandler(s.log))
	err := s.grpcServer.Serve(s.listener)
	if err != nil {
		s.log.Errorw("error starting gRPC server", "error_msg", zap.Error(err))
		return err
	}
	return nil
}

//Stop method waits for signal to stop the server and stops GRPC server upon receiving it
func (s *engineServer) Stop() {
	<-s.stopCh
	s.log.Infow("Initiating shutdown of CI engine server")
	if s.grpcServer != nil {
		// Hard stop the GRPC server if it doesn't gracefully shut down within hardStopWaitTimeout.
		go func() {
			time.Sleep(hardStopWaitTimeout * time.Second)
			s.log.Infow("Initiating hard shutdown of CI engine server")
			s.grpcServer.Stop()
		}()

		s.log.Infow("Gracefully shutting down CI engine server")
		s.grpcServer.GracefulStop()
	}
}

func (s *engineServer) GetStopChannel() chan bool {
	return s.stopCh
}
