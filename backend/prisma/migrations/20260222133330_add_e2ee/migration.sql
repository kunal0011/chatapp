-- AlterTable
ALTER TABLE "Message" ADD COLUMN     "ephemeralKey" TEXT,
ADD COLUMN     "isEncrypted" BOOLEAN NOT NULL DEFAULT false;

-- CreateTable
CREATE TABLE "UserKeyBundle" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "identityKey" TEXT NOT NULL,
    "signedPreKey" TEXT NOT NULL,
    "oneTimePreKeys" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "UserKeyBundle_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "UserKeyBundle_userId_key" ON "UserKeyBundle"("userId");

-- CreateIndex
CREATE INDEX "UserKeyBundle_userId_idx" ON "UserKeyBundle"("userId");

-- AddForeignKey
ALTER TABLE "UserKeyBundle" ADD CONSTRAINT "UserKeyBundle_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
