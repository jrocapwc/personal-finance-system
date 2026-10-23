"""Synchronous PyMongo repository; routes offload every call to a worker thread."""
from datetime import datetime, timezone
from bson import ObjectId
from fastapi import HTTPException
from pymongo import ASCENDING, DESCENDING


def object_id(value):
    if not ObjectId.is_valid(value):
        raise HTTPException(400, detail={"code": "INVALID_REQUEST", "message": "Invalid conversation ID"})
    return ObjectId(value)


def serialize(document):
    return {("id" if key == "_id" else key): str(value) if isinstance(value, ObjectId) else value
            for key, value in document.items() if key != "ownerId"}


class ConversationRepository:
    def __init__(self, database):
        self.conversations = database["conversations"]
        self.messages = database["messages"]

    def indexes(self):
        # Id breaks timestamp ties deterministically and supports the corresponding sorts.
        self.conversations.create_index([("ownerId", ASCENDING), ("updatedAt", DESCENDING), ("_id", DESCENDING)])
        self.messages.create_index([("conversationId", ASCENDING), ("createdAt", ASCENDING), ("_id", ASCENDING)])

    def create(self, owner, body):
        self.indexes()
        now = datetime.now(timezone.utc)
        document = {"_id": ObjectId(), "ownerId": owner, **body.model_dump(exclude_none=True),
                    "createdAt": now, "updatedAt": now}
        self.conversations.insert_one(document)
        return serialize(document)

    def owned(self, owner, conversation_id):
        document = self.conversations.find_one({"_id": object_id(conversation_id), "ownerId": owner})
        if document is None:
            raise HTTPException(404, detail={"code": "CONVERSATION_NOT_FOUND", "message": "Conversation not found"})
        return document

    def listing(self, owner, page, size):
        self.indexes()
        query = {"ownerId": owner}
        return {"items": [serialize(row) for row in self.conversations.find(query)
                .sort([("updatedAt", -1), ("_id", -1)]).skip(page * size).limit(size)],
                "page": page, "size": size}

    def read(self, owner, conversation_id, page, size):
        document = self.owned(owner, conversation_id)
        messages = self.messages.find({"conversationId": document["_id"]}).sort([("createdAt", 1), ("_id", 1)])
        return {**serialize(document), "messages": [serialize(row) for row in messages.skip(page * size).limit(size)],
                "page": page, "size": size}

    def context(self, owner, conversation_id):
        document = self.owned(owner, conversation_id)
        rows = list(self.messages.find({"conversationId": document["_id"]})
                    .sort([("createdAt", -1), ("_id", -1)]).limit(12))
        return document, [{"role": row["role"], "content": row["content"]} for row in reversed(rows)]

    def save_turn(self, owner, conversation_id, question, response):
        document = self.owned(owner, conversation_id)
        now = datetime.now(timezone.utc)
        rows = [{"_id": ObjectId(), "conversationId": document["_id"], "role": role, "content": content,
                 "provider": provider, "createdAt": now}
                for role, content, provider in [
                    ("user", question, "user"), ("assistant", response.answer, response.provider)]]
        # Standalone MongoDB has no multi-document transactions. Never acknowledge a partial turn.
        try:
            self.messages.insert_many(rows, ordered=True)
            result = self.conversations.update_one({"_id": document["_id"], "ownerId": owner},
                                                   {"$max": {"updatedAt": rows[-1]["createdAt"]}})
            if result.matched_count != 1:
                raise HTTPException(404, detail={"code": "CONVERSATION_NOT_FOUND", "message": "Conversation not found"})
        except Exception:
            # Best effort compensation; an outage can leave an ambiguous write, documented to callers.
            try:
                self.messages.delete_many({"_id": {"$in": [row["_id"] for row in rows]}, "conversationId": document["_id"]})
            except Exception:
                pass
            raise
