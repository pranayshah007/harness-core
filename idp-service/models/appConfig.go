package models

// import "go.mongodb.org/mongo-driver/bson/primitive"

type AppConfig struct {
	AccountId       string                `json:"AccountId" validate:"required"`
	Content         string                 `json:"Content" validate:"required"`
}