import { prisma } from '../../config/prisma.js';

/**
 * Store an encrypted SenderKey distribution blob.
 * Called when a group member distributes their SenderKey to another member.
 */
export async function storeSenderKeyDistribution(
    groupId: string,
    senderUserId: string,
    recipientUserId: string,
    encryptedKey: string
) {
    return prisma.senderKeyDistribution.upsert({
        where: {
            groupId_senderUserId_recipientUserId: {
                groupId,
                senderUserId,
                recipientUserId
            }
        },
        create: { groupId, senderUserId, recipientUserId, encryptedKey },
        update: { encryptedKey, createdAt: new Date() }
    });
}

/**
 * Store multiple distributions at once (sender → all group members).
 */
export async function storeBulkDistributions(
    distributions: Array<{
        groupId: string;
        senderUserId: string;
        recipientUserId: string;
        encryptedKey: string;
    }>
) {
    return prisma.$transaction(
        distributions.map(d =>
            prisma.senderKeyDistribution.upsert({
                where: {
                    groupId_senderUserId_recipientUserId: {
                        groupId: d.groupId,
                        senderUserId: d.senderUserId,
                        recipientUserId: d.recipientUserId
                    }
                },
                create: d,
                update: { encryptedKey: d.encryptedKey, createdAt: new Date() }
            })
        )
    );
}

/**
 * Fetch all pending SenderKey distributions for a recipient in a group.
 * Returns distributions from all other senders.
 */
export async function fetchSenderKeys(recipientUserId: string, groupId: string) {
    return prisma.senderKeyDistribution.findMany({
        where: { recipientUserId, groupId },
        select: {
            senderUserId: true,
            encryptedKey: true,
            createdAt: true
        },
        orderBy: { createdAt: 'desc' }
    });
}

/**
 * Delete own SenderKey distributions (before rotation).
 * Removes all recipient entries for this sender in the group.
 */
export async function revokeSenderKey(groupId: string, senderUserId: string) {
    return prisma.senderKeyDistribution.deleteMany({
        where: { groupId, senderUserId }
    });
}

/**
 * Delete all SenderKey distributions for a specific member (on removal/leave).
 * Removes both keys they sent AND keys sent to them.
 */
export async function deleteMemberSenderKeys(groupId: string, userId: string) {
    return prisma.$transaction([
        prisma.senderKeyDistribution.deleteMany({
            where: { groupId, senderUserId: userId }
        }),
        prisma.senderKeyDistribution.deleteMany({
            where: { groupId, recipientUserId: userId }
        })
    ]);
}

/**
 * Delete ALL SenderKey distributions for a group (on group deletion).
 */
export async function deleteGroupSenderKeys(groupId: string) {
    return prisma.senderKeyDistribution.deleteMany({
        where: { groupId }
    });
}
