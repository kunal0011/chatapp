-- CreateTable
CREATE TABLE "SignalIdentity" (
    "userId" TEXT NOT NULL,
    "registrationId" INTEGER NOT NULL,
    "identityKey" TEXT NOT NULL,

    CONSTRAINT "SignalIdentity_pkey" PRIMARY KEY ("userId")
);

-- CreateTable
CREATE TABLE "SignalSignedPreKey" (
    "userId" TEXT NOT NULL,
    "keyId" INTEGER NOT NULL,
    "publicKey" TEXT NOT NULL,
    "signature" TEXT NOT NULL,

    CONSTRAINT "SignalSignedPreKey_pkey" PRIMARY KEY ("userId")
);

-- CreateTable
CREATE TABLE "SignalOneTimePreKey" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "keyId" INTEGER NOT NULL,
    "publicKey" TEXT NOT NULL,

    CONSTRAINT "SignalOneTimePreKey_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "SignalOneTimePreKey_userId_idx" ON "SignalOneTimePreKey"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "SignalOneTimePreKey_userId_keyId_key" ON "SignalOneTimePreKey"("userId", "keyId");

-- AddForeignKey
ALTER TABLE "SignalIdentity" ADD CONSTRAINT "SignalIdentity_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SignalSignedPreKey" ADD CONSTRAINT "SignalSignedPreKey_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SignalOneTimePreKey" ADD CONSTRAINT "SignalOneTimePreKey_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
