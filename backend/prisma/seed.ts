import bcrypt from 'bcryptjs';
import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  const rounds = 12;
  const users = [
    { phone: '+15550000001', displayName: 'Alice', password: 'Password123!' },
    { phone: '+15550000002', displayName: 'Bob', password: 'Password123!' },
    { phone: '+15550000003', displayName: 'Charlie', password: 'Password123!' }
  ];

  for (const user of users) {
    const hash = await bcrypt.hash(user.password, rounds);
    await prisma.user.upsert({
      where: { phone: user.phone },
      update: { displayName: user.displayName, passwordHash: hash },
      create: { phone: user.phone, displayName: user.displayName, passwordHash: hash }
    });
  }
}

main()
  .then(async () => {
    await prisma.$disconnect();
  })
  .catch(async (error) => {
    console.error(error);
    await prisma.$disconnect();
    process.exit(1);
  });
