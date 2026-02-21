/*
  Warnings:

  - You are about to drop the `SignalIdentity` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `SignalOneTimePreKey` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `SignalSignedPreKey` table. If the table is not empty, all the data it contains will be lost.

*/
-- DropForeignKey
ALTER TABLE "SignalIdentity" DROP CONSTRAINT "SignalIdentity_userId_fkey";

-- DropForeignKey
ALTER TABLE "SignalOneTimePreKey" DROP CONSTRAINT "SignalOneTimePreKey_userId_fkey";

-- DropForeignKey
ALTER TABLE "SignalSignedPreKey" DROP CONSTRAINT "SignalSignedPreKey_userId_fkey";

-- DropTable
DROP TABLE "SignalIdentity";

-- DropTable
DROP TABLE "SignalOneTimePreKey";

-- DropTable
DROP TABLE "SignalSignedPreKey";
