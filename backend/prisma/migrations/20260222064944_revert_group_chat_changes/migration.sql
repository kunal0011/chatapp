/*
  Warnings:

  - The values [GROUP] on the enum `ConversationType` will be removed. If these variants are still used in the database, this will fail.
  - You are about to drop the column `avatarUrl` on the `Conversation` table. All the data in the column will be lost.
  - You are about to drop the column `description` on the `Conversation` table. All the data in the column will be lost.
  - You are about to drop the column `name` on the `Conversation` table. All the data in the column will be lost.
  - You are about to drop the column `lastReadMessageId` on the `ConversationMember` table. All the data in the column will be lost.
  - You are about to drop the column `role` on the `ConversationMember` table. All the data in the column will be lost.

*/
-- AlterEnum
BEGIN;
CREATE TYPE "ConversationType_new" AS ENUM ('DIRECT');
ALTER TABLE "public"."Conversation" ALTER COLUMN "type" DROP DEFAULT;
ALTER TABLE "Conversation" ALTER COLUMN "type" TYPE "ConversationType_new" USING ("type"::text::"ConversationType_new");
ALTER TYPE "ConversationType" RENAME TO "ConversationType_old";
ALTER TYPE "ConversationType_new" RENAME TO "ConversationType";
DROP TYPE "public"."ConversationType_old";
ALTER TABLE "Conversation" ALTER COLUMN "type" SET DEFAULT 'DIRECT';
COMMIT;

-- DropForeignKey
ALTER TABLE "ConversationMember" DROP CONSTRAINT "ConversationMember_lastReadMessageId_fkey";

-- AlterTable
ALTER TABLE "Conversation" DROP COLUMN "avatarUrl",
DROP COLUMN "description",
DROP COLUMN "name";

-- AlterTable
ALTER TABLE "ConversationMember" DROP COLUMN "lastReadMessageId",
DROP COLUMN "role";

-- DropEnum
DROP TYPE "MemberRole";
