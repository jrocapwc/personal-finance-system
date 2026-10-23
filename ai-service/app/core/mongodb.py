"""One lazy, pooled client per application. Call database operations in worker threads."""
from pymongo import MongoClient


class MongoDatabase:
    def __init__(self, settings):
        self.client = MongoClient(settings.mongodb_uri, connect=False, tz_aware=True,
                                  serverSelectionTimeoutMS=1500, connectTimeoutMS=1500,
                                  socketTimeoutMS=2000, timeoutMS=4000, waitQueueTimeoutMS=1000, w=1)
        self.database = self.client[settings.mongodb_database]

    def close(self):
        self.client.close()
