// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package redis

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"os"
	"reflect"
	"time"

	"github.com/go-redis/redis/v8"
	"github.com/harness/harness-core/queue-service/hsqs/store"
	"github.com/harness/harness-core/queue-service/hsqs/utils"
	"github.com/rs/zerolog"
)

// Store Redis type store used for enqueuing and dequeuing
type Store struct {
	Client *redis.Client
	Logger *zerolog.Logger
}

func newTlSConfig(certPathForTLS string) (*tls.Config, error) {
	// Create TLS config using cert PEM
	rootPem, err := ioutil.ReadFile(certPathForTLS)
	if err != nil {
		return nil, fmt.Errorf("could not read certificate file (%s), error: %s", certPathForTLS, err.Error())
	}

	roots := x509.NewCertPool()
	ok := roots.AppendCertsFromPEM(rootPem)
	if !ok {
		return nil, fmt.Errorf("error adding cert (%s) to pool, error: %s", certPathForTLS, err.Error())
	}
	return &tls.Config{RootCAs: roots}, nil
}

// NewRedisStore returns a new instance of RedisStore.
func NewRedisStoreWithTLS(endpoint, password string, useTLS bool, certPathForTLS string) *Store {
	opt := &redis.Options{
		Addr:     endpoint,
		Password: password,
	}
	if useTLS {
		newTlSConfig, err := newTlSConfig(certPathForTLS)
		if err != nil {
			fmt.Errorf("could not get TLS config: %s", err)
			return nil
		}
		opt.TLSConfig = newTlSConfig
	}
	c := redis.NewClient(opt)
	l := zerolog.New(os.Stderr).With().Timestamp().Logger()
	return &Store{Client: c, Logger: &l}
}

// NewRedisStore returns a new instance of RedisStore.
func NewRedisStore(addr string) *Store {
	c := redis.NewClient(&redis.Options{Addr: addr})
	l := zerolog.New(os.Stderr).With().Timestamp().Logger()

	return &Store{Client: c, Logger: &l}
}

type InvalidTypeError struct {
	tp reflect.Type
}

func (i InvalidTypeError) Error() string {
	return fmt.Sprintf("Invalid type %q, must be a pointer type", i.tp)
}

// Close the client
func (s *Store) Close() error {
	return s.Client.Close()
}

// Enqueue enqueues a given task message in there respective topic and subtopic
func (s *Store) Enqueue(ctx context.Context, request store.EnqueueRequest) (*store.EnqueueResponse, error) {

	err := ValidateEnqueueRequest(&request)
	if err != nil {
		return &store.EnqueueResponse{}, err
	}

	allSubTopicsKey := utils.GetAllSubTopicsFromTopicKey(request.Topic)
	subTopicQueueKey := utils.GetSubTopicStreamQueueKey(request.Topic, request.SubTopic)

	// add subtopic in subtopics set
	sAddResult, err := s.Client.SAdd(ctx, allSubTopicsKey, request.SubTopic).Result()
	if err != nil {
		return nil, &store.EnqueueErrorResponse{ErrorMessage: err.Error()}
	}

	// add message in stream
	xAddArgs := &redis.XAddArgs{
		Stream: subTopicQueueKey,
		ID:     "*",
		Values: []interface{}{"payload", request.Payload, "producer", request.ProducerName},
	}
	val, err := s.Client.XAdd(ctx, xAddArgs).Result()

	if err != nil {
		return nil, &store.EnqueueErrorResponse{ErrorMessage: err.Error()}
	}

	// if subtopic does not exist, new queue has been created and consumer group needs to be registered
	if sAddResult == int64(1) {
		err = s.RegisterQueue(ctx, request.Topic, request.SubTopic, request.ProducerName)
		if err != nil {
			return nil, &store.EnqueueErrorResponse{ErrorMessage: err.Error()}
		}
	}

	return &store.EnqueueResponse{ItemID: val}, nil
}

// Dequeue dequeues a message for processing randomly from the queues for all the subTopics
func (s *Store) Dequeue(ctx context.Context, request store.DequeueRequest) ([]*store.DequeueResponse, error) {

	err := ValidateDequeueRequest(&request)
	if err != nil {
		return nil, err
	}
	// Get all subtopics for given topic request
	subtopics, err := s.AllSubTopicsForGivenTopic(ctx, request.Topic)
	// if no subtopics for given topic, then return empty result
	if err == redis.Nil {
		return nil, nil
	}

	// TODO Exclude subtopics which are blacklisted (due to unack)

	// Select a random subtopic to get items from the subtopic
	index := utils.RandInt(len(subtopics))

	selectedTopic := subtopics[index]
	s.Logger.Debug().Msgf("selected subTopic is %s", selectedTopic)

	return s.ReadFromStream(ctx, utils.GetSubTopicStreamQueueKey(request.Topic, selectedTopic), request.BatchSize, utils.GetConsumerGroupKeyForTopic(request.Topic), request.ConsumerName, request.MaxWaitDuration)
}

// ReadFromStream helper method to read from subTopic Streams
func (s *Store) ReadFromStream(ctx context.Context, streamKey string, batchSize int, groupName string, consumerName string, maxWaitDuration time.Duration) ([]*store.DequeueResponse, error) {

	// Claim entries for pending items more than retry interval duration for given topic
	// else return new Messages

	s.Logger.Debug().Msgf("Reading from stream %s for batchSize of %d from groupName %s and Consumer %s", streamKey, batchSize, groupName, consumerName)

	pendingRequest := &PendingEntriesRequest{
		Stream:   streamKey,
		Group:    groupName,
		Consumer: consumerName,
		Count:    batchSize,
	}
	pendingEntries, err := s.GetPendingEntries(ctx, pendingRequest)

	if err != nil {
		s.Logger.Debug().Msgf("Pending Entries Error received for stream %s with error %s", streamKey, err)
	}
	if pendingEntries == nil || len(pendingEntries) == 0 {
		readRequest := &ReadNewMessagesRequest{
			Stream:          streamKey,
			Group:           groupName,
			Consumer:        consumerName,
			Count:           batchSize,
			MaxWaitDuration: maxWaitDuration,
		}
		return s.ReadNewMessages(ctx, readRequest)
	}

	claimRequest := &ClaimRequest{
		Stream:   streamKey,
		Group:    groupName,
		Consumer: consumerName,
	}
	claimResponse, err := s.ClaimEntries(ctx, claimRequest, pendingEntries)
	if err != nil {
		s.Logger.Debug().Msgf("Claim Entries Error received for stream %s with error %s", streamKey, err)
	}
	// If claim entries are errored out or empty result then fetch new messages
	if claimResponse.Messages == nil || len(claimResponse.Messages) == 0 {
		readRequest := &ReadNewMessagesRequest{
			Stream:          streamKey,
			Group:           groupName,
			Consumer:        consumerName,
			Count:           batchSize,
			MaxWaitDuration: maxWaitDuration,
		}
		return s.ReadNewMessages(ctx, readRequest)
	}

	// else return claimed messages
	return claimResponse.Messages, nil
}

// ClaimResponse Response Object for claiming Redis Stream
type ClaimResponse struct {
	Stream   string
	Messages []*store.DequeueResponse
}

// ClaimRequest Request Object for claiming Redis Stream
type ClaimRequest struct {
	Stream   string
	Group    string
	Consumer string
}

// PendingEntriesRequest Request Object for checking pending entries in Redis Stream
type PendingEntriesRequest struct {
	Stream   string
	Group    string
	Consumer string
	Count    int
}

// ReadNewMessagesRequest Request Object for getting new entries from Redis Stream
type ReadNewMessagesRequest struct {
	Stream          string
	Group           string
	Consumer        string
	Count           int
	MaxWaitDuration time.Duration
}

// ReadNewMessages reads new messages from the stream
func (s *Store) ReadNewMessages(ctx context.Context, r *ReadNewMessagesRequest) ([]*store.DequeueResponse, error) {
	if len(r.Stream) == 0 {
		return []*store.DequeueResponse{}, nil
	}

	s.Logger.Debug().Msgf("Consumer reading new messages from stream %v", r.Stream)
	xReadGroupArgs := &redis.XReadGroupArgs{
		Group:    r.Group,
		Consumer: r.Consumer,
		Streams:  []string{r.Stream, ">"},
		Count:    int64(r.Count),
		Block:    r.MaxWaitDuration,
	}
	result, err := s.Client.XReadGroup(ctx, xReadGroupArgs).Result()

	if err == redis.Nil {
		return []*store.DequeueResponse{}, nil
	}

	if err != nil {
		return nil, &store.DequeueErrorResponse{ErrorMessage: err.Error()}
	}
	messages := MapXStreamToResponse(r.Stream, result)
	s.Logger.Debug().Msgf("Result for new messages %v", len(messages))
	return messages, nil
}

func (s *Store) GetPendingEntries(ctx context.Context, request *PendingEntriesRequest) ([]string, error) {
	xPendingArgs := &redis.XPendingExtArgs{
		Stream: request.Stream,
		Group:  request.Group,
		//Idle:     1000000000, // TODO Handle idle time passing
		Count:    int64(request.Count),
		Start:    "-",
		End:      "+",
		Consumer: request.Consumer,
	}

	pending, err := s.Client.XPendingExt(ctx, xPendingArgs).Result()

	if err != nil {
		return nil, err
	}

	//TODO handle retry count and move to dead letter queue
	messageIds := fetchPendingMessageIds(pending)

	nLogger := s.Logger.With().Str("StreamName", request.Stream).
		Str("ConsumerName", request.Consumer).
		Str("GroupName", request.Group).Logger()

	nLogger.Debug().Msgf("Length of pending entries : %d", len(messageIds))
	return messageIds, nil
}

// Method to gather messageIds from Pending messages response
func fetchPendingMessageIds(msgs []redis.XPendingExt) []string {
	messages := make([]string, 0)
	for _, m := range msgs {
		messages = append(messages, m.ID)
	}
	return messages
}

// ClaimEntries helper method to claim redis stream entries
func (s *Store) ClaimEntries(ctx context.Context, request *ClaimRequest, ids []string) (*ClaimResponse, error) {
	result, err := s.Client.XClaim(ctx, &redis.XClaimArgs{
		Stream:   request.Stream,
		Group:    request.Group,
		Consumer: request.Consumer,
		Messages: ids,
	}).Result()

	if err != nil {
		return &ClaimResponse{
			Stream:   request.Stream,
			Messages: nil,
		}, err
	}

	nLogger := s.Logger.With().Str("StreamName", request.Stream).
		Str("ConsumerName", request.Consumer).
		Str("GroupName", request.Group).Logger()

	nLogger.Info().Msgf("Claimed %d Messages", len(result))
	return &ClaimResponse{
		Stream:   request.Stream,
		Messages: MapXMessageToResponse(request.Stream, result),
	}, nil
}

func MapXStreamToResponse(queueKey string, result []redis.XStream) []*store.DequeueResponse {
	messages := make([]*store.DequeueResponse, 0)
	for _, xstream := range result {
		messages = append(messages, MapXMessageToResponse(queueKey, xstream.Messages)...)
	}
	return messages
}

// MapXMessageToResponse helper method to map x message to response
func MapXMessageToResponse(queueKey string, msgs []redis.XMessage) []*store.DequeueResponse {

	messages := make([]*store.DequeueResponse, 0)
	for _, m := range msgs {

		cm := store.DequeueResponse{
			ItemID:    m.ID,
			Timestamp: time.Now().Unix(),
			QueueKey:  queueKey,
			Payload:   m.Values["payload"].(string),
			ItemMetadata: store.DequeueItemMetadata{
				CurrentRetryCount: 0,
				MaxProcessingTime: 0,
			},
		}
		messages = append(messages, &cm)
	}
	return messages
}

// Ack method is used to acknowledge processing of a pending message
func (s *Store) Ack(ctx context.Context, request store.AckRequest) (*store.AckResponse, error) {

	ids := []string{request.ItemID}
	topicKey := utils.GetSubTopicStreamQueueKey(request.Topic, request.SubTopic)

	// acknowledging the processed method
	if _, err := s.Client.XAck(ctx, topicKey, request.ConsumerName, ids...).Result(); err != nil {
		return &store.AckResponse{}, &store.AckErrorResponse{ErrorMessage: err.Error()}
	}
	//deleting the method from queue
	if _, err := s.Client.XDel(ctx, topicKey, ids...).Result(); err != nil {
		return &store.AckResponse{}, &store.AckErrorResponse{ErrorMessage: err.Error()}
	}
	return &store.AckResponse{ItemID: request.ItemID}, nil

}

// UnAck Method will add a specific topic to blockList processing list
func (s *Store) UnAck(ctx context.Context, request store.UnAckRequest) (*store.UnAckResponse, error) {
	blockedKey := utils.GetAllBlockedSubTopicsFromTopicKey(request.Topic, request.SubTopic)
	result, err := s.Client.Set(ctx, blockedKey, true, request.RetryAfterTimeDuration).Result()
	if err != nil {
		return &store.UnAckResponse{}, &store.UnAckErrorResponse{ErrorMessage: err.Error()}
	}
	return &store.UnAckResponse{
		ItemID:   result,
		Topic:    request.Topic,
		SubTopic: request.SubTopic,
		Type:     store.UnAckTopic,
	}, nil
}

// SetKey helper method to set a key value pair
func (s *Store) SetKey(ctx context.Context, key string, v any) error {
	data, err := json.Marshal(v)
	if err != nil {
		return err
	}
	return s.Client.Set(ctx, key, data, 0).Err()
}

// GetKey helper method to get value for a key
func (s *Store) GetKey(ctx context.Context, key string, v any) error {
	bytes, err := s.Client.Get(ctx, key).Bytes()
	if err != nil {
		return err
	}

	rv := reflect.ValueOf(v)
	if rv.Kind() != reflect.Pointer || rv.IsNil() {
		return InvalidTypeError{tp: rv.Type()}
	}
	err = json.Unmarshal(bytes, v)
	if err != nil {
		return err
	}
	return nil

}

// AllSubTopicsForGivenTopic helper method to fetch all subTopics for a given topic
func (s *Store) AllSubTopicsForGivenTopic(ctx context.Context, topic string) ([]string, error) {
	allQueuesTopicKey := utils.GetAllSubTopicsFromTopicKey(topic)
	allTopicsResult, err := s.Client.SMembers(ctx, allQueuesTopicKey).Result()
	if err != nil || err == redis.Nil {
		return nil, err
	}

	// todo: initialize logger in other place
	//nLogger := s.Logger.With().Str("AllQueuesTopicKey", allQueuesTopicKey).
	//	Str("ConsumerName", request.ConsumerName).
	//	Int("batchSize", request.BatchSize).Logger()

	s.Logger.Debug().Msgf("Length of subtopics is: %d", len(allTopicsResult))
	return allTopicsResult, nil
}

// GetTopicMetadata helper method to get topic metadata details
func (s *Store) GetTopicMetadata(ctx context.Context, topic string) (*store.RegisterTopicMetadata, error) {
	var metadata store.RegisterTopicMetadata
	err := s.GetKey(ctx, utils.GetTopicMetadataKey(topic), &metadata)

	if err == redis.Nil {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &metadata, nil
}

// ValidateDequeueRequest helper method to Validate Dequeue Request
func ValidateDequeueRequest(request *store.DequeueRequest) error {
	if len(request.Topic) == 0 {
		return fmt.Errorf("DequeueRequest TopicName cannot be empty")
	}
	if len(request.ConsumerName) == 0 {
		return fmt.Errorf("DequeueRequest ConsumerName cannot be empty")
	}
	if request.BatchSize <= 0 {
		return fmt.Errorf("DequeueRequest BatchSize should be greater than 0")
	}
	return nil
}

// ValidateEnqueueRequest helper method to Validate Enqueue Request
func ValidateEnqueueRequest(request *store.EnqueueRequest) error {
	if len(request.Topic) == 0 {
		return fmt.Errorf("EnqueueRequest TopicName cannot be empty")
	}

	if len(request.SubTopic) == 0 {
		return fmt.Errorf("EnqueueRequest TopicName cannot be empty")
	}

	if len(request.ProducerName) == 0 {
		return fmt.Errorf("EnqueueRequest ProducerName cannot be empty")
	}

	if len(request.Payload) == 0 {
		return fmt.Errorf("EnqueueRequest Payload cannot be empty")
	}
	return nil

}

// Register method to add a consumer group to stream and add topic Metadata
func (s *Store) Register(ctx context.Context, request store.RegisterTopicMetadata) error {
	subtopics, err := s.AllSubTopicsForGivenTopic(ctx, request.Topic)

	if err != nil || err == redis.Nil {
		return err
	}

	//todo: register stream metadata

	err = s.SetKey(ctx, utils.GetTopicMetadataKey(request.Topic), request)
	if err != nil {
		return err
	}

	for _, subtopic := range subtopics {
		_, err = s.Client.XGroupCreate(
			ctx,
			utils.GetSubTopicStreamQueueKey(request.Topic, subtopic),
			utils.GetConsumerGroupKeyForTopic(request.Topic),
			"0",
		).Result()
		if err != nil {
			return fmt.Errorf("failed to add consumer group for topic %s in the stream %s",
				request.Topic, utils.GetSubTopicStreamQueueKey(request.Topic, subtopic))
		}
	}
	return nil
}

// RegisterQueue method to add consumer group to the queue
func (s *Store) RegisterQueue(ctx context.Context, topic, subtopic, producer string) error {
	_, err := s.Client.XGroupCreate(
		ctx,
		utils.GetSubTopicStreamQueueKey(topic, subtopic),
		utils.GetConsumerGroupKeyForTopic(producer),
		"0",
	).Result()
	if err != nil {
		return fmt.Errorf("failed to add consumer group for topic %s in the stream %s",
			topic, utils.GetSubTopicStreamQueueKey(topic, subtopic))
	}
	return nil
}
