-- AlterTable
ALTER TABLE "ConversationMember" ADD COLUMN     "lastDeliveredMessageId" TEXT;

-- AddForeignKey
ALTER TABLE "ConversationMember" ADD CONSTRAINT "ConversationMember_lastDeliveredMessageId_fkey" FOREIGN KEY ("lastDeliveredMessageId") REFERENCES "Message"("id") ON DELETE SET NULL ON UPDATE CASCADE;
