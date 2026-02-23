-- AlterTable: add senderPlaintext column for encrypted self-history recovery
ALTER TABLE "Message" ADD COLUMN "senderPlaintext" TEXT;
