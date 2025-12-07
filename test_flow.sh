#!/bin/bash

# Endpoint
GATEWAY="http://localhost:8080"
# Use random email to avoid collision if run multiple times
EMAIL1="user1_$(date +%s)_$RANDOM@test.com"
EMAIL2="user2_$(date +%s)_$RANDOM@test.com"

echo "1. Register User 1"
RESP1=$(curl -s -X POST "$GATEWAY/users/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\": \"User One\", \"password\": \"password123\", \"email\": \"$EMAIL1\"}")
echo "Register Response 1: $RESP1"
TOKEN1=$(echo $RESP1 | jq -r '.token')
ID1=$(echo $RESP1 | jq -r '.id')
echo "Token1: $TOKEN1"
echo "ID1: $ID1"

echo "2. Register User 2"
RESP2=$(curl -s -X POST "$GATEWAY/users/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\": \"User Two\", \"password\": \"password123\", \"email\": \"$EMAIL2\"}")
echo "Register Response 2: $RESP2"
ID2=$(echo $RESP2 | jq -r '.id')
echo "ID2: $ID2"

# Wait a bit for propagation if needed (eventually consistent?)
sleep 1

echo "3. Get Chat ID"
# GET /history/chat?userId1=...&userId2=...
CHAT_RESP=$(curl -s -X GET "$GATEWAY/history/chat?userId1=$ID1&userId2=$ID2" \
  -H "Authorization: Bearer $TOKEN1")
echo "Chat Response: $CHAT_RESP"
CHAT_ID=$(echo $CHAT_RESP | tr -d '"')
echo "ChatID: $CHAT_ID"

echo "4. Send Message"
# POST /messages/
# Note: Gateway updated to forward /messages to message-service
MESSAGE_CONTENT="Hello User 2 $(date +%s)"
MSG_RESP=$(curl -s -X POST "$GATEWAY/messages" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN1" \
  -d "{\"chatId\": \"$CHAT_ID\", \"userId\": \"$ID1\", \"messageContent\": \"$MESSAGE_CONTENT\", \"messageSent\": \"User 1\"}")

echo "Message Response: $MSG_RESP"
# Sleep less, consumer timeout handles waiting
sleep 2

echo "5. Verify Kafka Messages (New only)"
docker exec kafka kafka-console-consumer --bootstrap-server localhost:29092 --topic message --from-beginning --timeout-ms 2000 | grep "$MESSAGE_CONTENT"
