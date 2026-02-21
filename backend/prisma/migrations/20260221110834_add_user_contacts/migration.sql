-- CreateTable
CREATE TABLE "UserContact" (
    "id" TEXT NOT NULL,
    "ownerId" TEXT NOT NULL,
    "contactId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "UserContact_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "UserContact_ownerId_idx" ON "UserContact"("ownerId");

-- CreateIndex
CREATE UNIQUE INDEX "UserContact_ownerId_contactId_key" ON "UserContact"("ownerId", "contactId");

-- AddForeignKey
ALTER TABLE "UserContact" ADD CONSTRAINT "UserContact_ownerId_fkey" FOREIGN KEY ("ownerId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "UserContact" ADD CONSTRAINT "UserContact_contactId_fkey" FOREIGN KEY ("contactId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
