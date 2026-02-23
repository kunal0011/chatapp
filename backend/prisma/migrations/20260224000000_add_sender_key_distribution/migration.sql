-- SenderKeyDistribution: stores encrypted SenderKey blobs for group E2EE
-- Each entry = one sender's key encrypted for one recipient via 1:1 E2EE
CREATE TABLE "SenderKeyDistribution" (
    "id"               TEXT NOT NULL DEFAULT gen_random_uuid(),
    "groupId"          TEXT NOT NULL,
    "senderUserId"     TEXT NOT NULL,
    "recipientUserId"  TEXT NOT NULL,
    "encryptedKey"     TEXT NOT NULL,
    "createdAt"        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SenderKeyDistribution_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "fk_skd_group" FOREIGN KEY ("groupId") REFERENCES "Conversation"("id") ON DELETE CASCADE,
    CONSTRAINT "fk_skd_sender" FOREIGN KEY ("senderUserId") REFERENCES "User"("id") ON DELETE CASCADE,
    CONSTRAINT "fk_skd_recipient" FOREIGN KEY ("recipientUserId") REFERENCES "User"("id") ON DELETE CASCADE
);

-- One sender key per (group, sender, recipient) triple
CREATE UNIQUE INDEX "skd_unique" ON "SenderKeyDistribution"("groupId", "senderUserId", "recipientUserId");
-- Fast lookup: "give me all pending sender keys for this recipient in this group"
CREATE INDEX "skd_recipient_group" ON "SenderKeyDistribution"("recipientUserId", "groupId");
