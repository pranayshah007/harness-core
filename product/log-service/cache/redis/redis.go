package redis

import (
	"context"
	"github.com/go-redis/redis/v7"
	"time"
)

type Redis struct {
	Client redis.Cmdable
}

func New(endpoint, password string, useTLS, disableExpiryWatcher bool, certPathForTLS string) *Redis {
	opt := &redis.Options{
		Addr:     endpoint,
		Password: password,
		DB:       0,
	}

	rdb := redis.NewClient(opt)

	rc := &Redis{
		Client: rdb,
	}

	return rc
}

func (r *Redis) Get(ctx context.Context, key string) (string, error) {
	value, err := r.Client.Get(key).Result()
	if err != nil {
		return "", err
	}
	return value, err
}

func (r *Redis) Create(ctx context.Context, key, value string) error {
	r.Client.Del(key)
	err := r.Client.Set(key, value, time.Hour)
	if err != nil {
		return err.Err()
	}
	return nil
}

func (r *Redis) Ping(ctx context.Context) error {
	_, err := r.Client.Ping().Result()
	if err != nil {
		return err
	}
	return nil
}
