-- AlterTable: track the last-seen identity key per contact for Key Change Alert detection
ALTER TABLE "UserContact" ADD COLUMN "knownIdentityKey" TEXT,
ADD COLUMN "ikVerifiedAt" TIMESTAMP(3);
