-- AlterTable
ALTER TABLE "RefreshToken" ADD COLUMN     "familyId" TEXT,
ADD COLUMN     "isUsed" BOOLEAN NOT NULL DEFAULT false;

-- CreateIndex
CREATE INDEX "RefreshToken_familyId_idx" ON "RefreshToken"("familyId");
