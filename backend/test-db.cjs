const { PrismaClient } = require('@prisma/client');
const prisma = new PrismaClient();

async function main() {
  const messages = await prisma.message.findMany({
    orderBy: { createdAt: 'desc' },
    take: 5
  });
  console.log(JSON.stringify(messages.map(m => m.content), null, 2));
}

main()
  .catch(console.error)
  .finally(() => prisma.$disconnect());
