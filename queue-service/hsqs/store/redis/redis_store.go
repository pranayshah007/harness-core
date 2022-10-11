// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package redis

import (
	"context"
	"encoding/json"
	"fmt"
	"github.com/go-redis/redis/v8"
	"github.com/harness/harness-core/queue-service/hsqs/store"
	"github.com/harness/harness-core/queue-service/hsqs/utils"
	"github.com/rs/zerolog"
	"os"
	"reflect"
	"time"
)

// Store Redis type store used for enqueuing and dequeuing
type Store struct {
	client *redis.Client
	logger *zerolog.Logger
}

// NewRedisStore returns a new instance of RedisStore.
func NewRedisStore(addr string) *Store {
	c := redis.NewClient(&redis.Options{Addr: addr})
	l := zerolog.New(os.Stderr).With().Timestamp().Logger()

	return &Store{client: c, logger: &l}
}

type InvalidTypeError struct {
	tp reflect.Type
}

func (i InvalidTypeError) Error() string {
	return fmt.Sprintf("Invalid type %q, must be a pointer type", i.tp)
}

// Close the client
func (s *Store) Close() error {
	return s.client.Close()
}

// Enqueue enqueues a given task message in there respective topic and subtopic
func (s *Store) Enqueue(ctx context.Context, request store.EnqueueRequest) (*store.EnqueueResponse, error) {

	err := ValidateEnqueueRequest(&request)
	if err != nil {
		return &store.EnqueueResponse{}, err
	}

	allSubTopicsKey := utils.GetStoreAllSubTopicsFromTopicKey(request.Topic)
	subTopicQueueKey := utils.GetSubTopicStreamQueueKey(request.Topic, request.SubTopic)

	// add subtopic in subtopics set
	_, err = s.client.SAdd(ctx, allSubTopicsKey, request.SubTopic).Result()
	if err != nil {
		return nil, err
	}

	// add message in stream
	xAddArgs := &redis.XAddArgs{
		Stream: subTopicQueueKey,
		ID:     "*",
		Values: map[string]interface{}{
			"payload":  request.Payload,
			"producer": request.ProducerName,
		},
	}
	val, err := s.client.XAdd(ctx, xAddArgs).Result()

	if err != nil {
		return &store.EnqueueResponse{}, err
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
	subtopics, err := s.AllSubTopicsForGivenTopic(ctx, &request)
	// if no subtopics for given topic, then return empty result
	if err == redis.Nil {
		return nil, nil
	}

	// TODO Exclude subtopics which are blacklisted (due to unack)

	// Select a random subtopic to get items from the subtopic
	index := utils.RandInt(len(subtopics))

	selectedTopic := subtopics[index]
	s.logger.Debug().Msgf("selected subTopic is %s", selectedTopic)
	return s.ReadFromStream(ctx, utils.GetSubTopicStreamQueueKey(request.Topic, selectedTopic), request.BatchSize, request.Topic, request.ConsumerName)
}

// ReadFromStream helper method to read from subTopic Streams
func (s *Store) ReadFromStream(ctx context.Context, streamKey string, batchSize int, groupName string, consumerName string) ([]*store.DequeueResponse, error) {

	// Claim entries for pending items more than retry interval duration for given topic
	// else return new Messages
	//todo: More than retry interval logic

	s.logger.Debug().Msgf("Reading from stream %s for batchSize of %d from groupName %s and Consumer %s", streamKey, batchSize, groupName, consumerName)
	xPendingArgs := &redis.XPendingExtArgs{
		Stream:   streamKey,
		Group:    groupName,
		Count:    int64(batchSize),
		Start:    "-",
		End:      "+",
		Consumer: consumerName,
	}

	claimRequest := &ClaimRequest{
		Stream:   streamKey,
		Group:    groupName,
		Consumer: consumerName,
	}
	size := batchSize
	dequeueMessage := make([]*store.DequeueResponse, 0)
	val, err := s.client.XPendingExt(ctx, xPendingArgs).Result()

	if err != nil {
		fmt.Printf("error received : %d", err)
	}
	if val != nil && len(val) != 0 {
		fmt.Printf("Received pending messages %d", len(val))
		if len(val) < batchSize {
			size = len(val)
		} else {
			size = batchSize
		}
		claimResponse, _ := s.claimEntries(ctx, claimRequest, fetchPendingMessageId(val[0:size]))
		if claimResponse != nil {
			dequeueMessage = append(dequeueMessage, claimResponse.Messages...)
		}
	}

	if len(dequeueMessage) < batchSize {

		xReadGroupArgs := &redis.XReadGroupArgs{
			Group:    groupName,
			Consumer: consumerName,
			Streams:  []string{streamKey, ">"},
			Count:    int64(batchSize - len(dequeueMessage)),
		}
		response, err := s.client.XReadGroup(ctx, xReadGroupArgs).Result()

		if err != nil {
			return nil, err
		}
		dequeueMessage = append(dequeueMessage, MapXMessageToResponse(streamKey, response[0].Messages)...)

	}

	return dequeueMessage, nil
}

// AllSubTopicsForGivenTopic helper method to fetch all subTopics for a given topic
func (s *Store) AllSubTopicsForGivenTopic(ctx context.Context, request *store.DequeueRequest) ([]string, error) {
	allQueuesTopicKey := utils.GetStoreAllSubTopicsFromTopicKey(request.Topic)
	allTopicsResult, err := s.client.SMembers(ctx, allQueuesTopicKey).Result()
	if err != nil || err == redis.Nil {
		return nil, err
	}

	nLogger := s.logger.With().Str("AllQueuesTopicKey", allQueuesTopicKey).
		Str("ConsumerName", request.ConsumerName).
		Int("batchSize", request.BatchSize).Logger()

	nLogger.Debug().Msgf("Length of AllQueues list : %d", len(allTopicsResult))
	return allTopicsResult, nil
}

// SetKey helper method to set a key value pair
func (s *Store) SetKey(ctx context.Context, key string, v any) error {
	data, err := json.Marshal(v)
	if err != nil {
		return err
	}
	return s.client.Set(ctx, key, data, 0).Err()
}

// GetKey helper method to get value for a key
func (s *Store) GetKey(ctx context.Context, key string, v any) error {
	bytes, err := s.client.Get(ctx, key).Bytes()
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

	if request.Payload == nil {
		return fmt.Errorf("DequeueRequest BatchSize should be greater than 0")
	}
	return nil

}

// ClaimResponse Response Object for claiming Redis Stream
type ClaimResponse struct {
	err      error
	Stream   string
	Messages []*store.DequeueResponse
}

// ClaimRequest Request Object for claiming Redis Stream
type ClaimRequest struct {
	Stream   string
	Group    string
	Consumer string
}

// ClaimEntries helper method to claim redis stream entries
func (s *Store) claimEntries(ctx context.Context, request *ClaimRequest, ids []string) (*ClaimResponse, error) {
	result, err := s.client.XClaim(ctx, &redis.XClaimArgs{
		Stream:   request.Stream,
		Group:    request.Group,
		Consumer: request.Consumer,
		Messages: ids,
	}).Result()

	if err != nil {
		return &ClaimResponse{
			err:      err,
			Stream:   request.Stream,
			Messages: nil,
		}, err
	}

	nLogger := s.logger.With().Str("StreamName", request.Stream).
		Str("ConsumerName", request.Consumer).
		Str("GroupName", request.Group).Logger()

	nLogger.Info().Msgf("Claimed %d Messages", len(result))
	return &ClaimResponse{
		err:      nil,
		Stream:   request.Stream,
		Messages: MapXMessageToResponse(request.Stream, result),
	}, nil
}

// MapXMessageToResponse helper method to map x message to response
func MapXMessageToResponse(queueKey string, msgs []redis.XMessage) []*store.DequeueResponse {

	messages := make([]*store.DequeueResponse, 0)
	for _, m := range msgs {
		cm := store.DequeueResponse{
			ItemID:    m.ID,
			Timestamp: time.Now().Unix(),
			QueueKey:  queueKey,
			Payload:   []byte(m.Values["payload"].(string)),
			ItemMetadata: store.DequeueItemMetadata{
				CurrentRetryCount: 0,
				MaxProcessingTime: 0,
			},
		}
		messages = append(messages, &cm)
	}
	return messages
}

// Method to gather messageIds from Pending messages response
func fetchPendingMessageId(msgs []redis.XPendingExt) []string {
	messages := make([]string, 0)
	for _, m := range msgs {
		messages = append(messages, m.ID)
	}
	return messages
}

// Ack method is used to acknowledge processing of a pending message
func (s *Store) Ack(ctx context.Context, request store.AckRequest) (*store.AckResponse, error) {

	ids := []string{request.ItemID}
	topickey := utils.GetSubTopicStreamQueueKey(request.Topic, request.SubTopic)
	result, _ := s.client.XAck(ctx, topickey, request.ConsumerName, ids...).Result()

	return &store.AckResponse{ItemID: string(result)}, nil

}

// UnAck Method will add a specific topic to blockList processing list
func (s *Store) UnAck(ctx context.Context, request store.UnAckRequest) (*store.UnAckResponse, error) {
	blockedKey := utils.GetStoreAllBlockedSubTopicsFromTopicKey(request.Topic, request.SubTopic)
	result, err := s.client.Set(ctx, blockedKey, true, request.RetryAfterTimeDuration).Result()
	if err != nil {
		return &store.UnAckResponse{}, err
	}
	return &store.UnAckResponse{
		ItemID:   result,
		Topic:    request.Topic,
		SubTopic: request.SubTopic,
		Type:     store.UnAckTopic,
	}, nil
}
