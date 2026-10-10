// Firestore security rules tests. Run with `npm test` in tools/rules-tests: it starts the
// Firestore emulator, runs these against ../../firestore.rules, and stops it again.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  setLogLevel,
  Timestamp,
  updateDoc,
  where,
} from 'firebase/firestore';

const rulesPath = fileURLToPath(new URL('../../../firestore.rules', import.meta.url));

let env;

// Denied writes are the point of most tests: don't log each one.
setLogLevel('silent');

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-medhome',
    firestore: { rules: readFileSync(rulesPath, 'utf8') },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  // Seed existing data with rules off, as the Admin SDK and the console would.
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const createdAt = Timestamp.fromDate(new Date('2026-01-01T00:00:00Z'));
    await setDoc(doc(db, 'users/alice'), { name: 'Alice', email: 'alice@example.com', role: 'patient', createdAt });
    await setDoc(doc(db, 'users/bob'), { name: 'Bob', email: 'bob@example.com', role: 'patient', createdAt });
    await setDoc(doc(db, 'users/admin'), { name: 'Admin', email: 'admin@example.com', role: 'admin', createdAt });
    await setDoc(doc(db, 'doctors/doc-001'), { name: 'Asha Rai', specialty: 'cardiology', active: true });
    await setDoc(doc(db, 'doctors/doc-002'), { name: 'Old Doctor', specialty: 'ent', active: false });
  });
});

const signedOut = () => env.unauthenticatedContext().firestore();
const as = (uid, email = `${uid}@example.com`) => env.authenticatedContext(uid, { email }).firestore();

const newProfile = (overrides = {}) => ({
  name: 'Carol',
  email: 'carol@example.com',
  role: 'patient',
  createdAt: serverTimestamp(),
  ...overrides,
});

describe('doctors', () => {
  it('signed-in users can read a doctor', async () => {
    await assertSucceeds(getDoc(doc(as('alice'), 'doctors/doc-001')));
  });

  it('signed-in users can list active doctors, and only by asking for active ones', async () => {
    await assertSucceeds(getDocs(query(collection(as('alice'), 'doctors'), where('active', '==', true))));
    await assertFails(getDocs(collection(as('alice'), 'doctors')));
  });

  it('inactive and missing doctors are hidden from users but not from admins', async () => {
    await assertFails(getDoc(doc(as('alice'), 'doctors/doc-002')));
    await assertFails(getDoc(doc(as('alice'), 'doctors/doc-404')));
    await assertSucceeds(getDoc(doc(as('admin'), 'doctors/doc-002')));
    await assertSucceeds(getDocs(collection(as('admin'), 'doctors')));
  });

  it('signed-out users cannot read or list doctors', async () => {
    await assertFails(getDoc(doc(signedOut(), 'doctors/doc-001')));
    await assertFails(getDocs(collection(signedOut(), 'doctors')));
  });

  it('patients and signed-out users cannot create a doctor (admin writes: admin.rules.test.js)', async () => {
    const doctor = { name: 'Fake', specialty: 'cardiology', active: true, feeNpr: 1 };
    await assertFails(setDoc(doc(as('alice'), 'doctors/doc-999'), doctor));
    await assertFails(setDoc(doc(signedOut(), 'doctors/doc-999'), doctor));
  });

  it('patients cannot update a doctor, and nobody can delete one, not even an admin', async () => {
    await assertFails(updateDoc(doc(as('alice'), 'doctors/doc-001'), { feeNpr: 1 }));
    await assertFails(deleteDoc(doc(as('alice'), 'doctors/doc-001')));
    await assertFails(deleteDoc(doc(as('admin'), 'doctors/doc-001')));
  });
});

describe('users: reading', () => {
  it('a user can read their own profile', async () => {
    await assertSucceeds(getDoc(doc(as('alice'), 'users/alice')));
  });

  it("a user cannot read someone else's profile or list users", async () => {
    await assertFails(getDoc(doc(as('alice'), 'users/bob')));
    await assertFails(getDocs(collection(as('alice'), 'users')));
  });

  it('signed-out users cannot read any profile', async () => {
    await assertFails(getDoc(doc(signedOut(), 'users/alice')));
  });

  it("an admin cannot read someone else's profile (bookings carry the name)", async () => {
    await assertFails(getDoc(doc(as('admin'), 'users/bob')));
  });
});

describe('users: signing up', () => {
  it('a user can create their own patient profile', async () => {
    await assertSucceeds(setDoc(doc(as('carol'), 'users/carol'), newProfile()));
  });

  it('the email may differ from the token in letter case only', async () => {
    await assertSucceeds(setDoc(doc(as('carol', 'Carol@Example.com'), 'users/carol'), newProfile()));
    await assertFails(setDoc(doc(as('carol'), 'users/carol'), newProfile({ email: 'someone@example.com' })));
  });

  it('a user cannot sign up as a doctor or an admin', async () => {
    await assertFails(setDoc(doc(as('carol'), 'users/carol'), newProfile({ role: 'doctor' })));
    await assertFails(setDoc(doc(as('carol'), 'users/carol'), newProfile({ role: 'admin' })));
  });

  it('createdAt must be the server time and no other fields are allowed', async () => {
    const past = Timestamp.fromDate(new Date('2020-01-01T00:00:00Z'));
    await assertFails(setDoc(doc(as('carol'), 'users/carol'), newProfile({ createdAt: past })));
    await assertFails(setDoc(doc(as('carol'), 'users/carol'), newProfile({ verified: true })));
  });

  it('a user cannot create a profile for someone else, and signed-out users none', async () => {
    await assertFails(setDoc(doc(as('carol'), 'users/dave'), newProfile({ email: 'dave@example.com' })));
    await assertFails(setDoc(doc(signedOut(), 'users/carol'), newProfile()));
  });

  it('a name must be 1 to 100 characters', async () => {
    await assertFails(setDoc(doc(as('carol'), 'users/carol'), newProfile({ name: '' })));
    await assertFails(setDoc(doc(as('carol'), 'users/carol'), newProfile({ name: 'x'.repeat(101) })));
  });
});

describe('users: editing', () => {
  it('a user can edit their name, phone, date of birth and gender', async () => {
    await assertSucceeds(updateDoc(doc(as('alice'), 'users/alice'), {
      name: 'Alice R',
      phone: '9812345678',
      dateOfBirth: '1990-05-17',
      gender: 'female',
    }));
  });

  it('a user cannot change their own role, email or creation time', async () => {
    await assertFails(updateDoc(doc(as('alice'), 'users/alice'), { role: 'admin' }));
    await assertFails(updateDoc(doc(as('alice'), 'users/alice'), { role: 'doctor' }));
    await assertFails(updateDoc(doc(as('alice'), 'users/alice'), { email: 'new@example.com' }));
    await assertFails(updateDoc(doc(as('alice'), 'users/alice'), { createdAt: serverTimestamp() }));
  });

  it('malformed optional fields are rejected', async () => {
    await assertFails(updateDoc(doc(as('alice'), 'users/alice'), { phone: '12345' }));
    await assertFails(updateDoc(doc(as('alice'), 'users/alice'), { dateOfBirth: '17/05/1990' }));
    await assertFails(updateDoc(doc(as('alice'), 'users/alice'), { gender: 'unknown' }));
  });

  it("a user cannot edit or delete someone else's profile", async () => {
    await assertFails(updateDoc(doc(as('alice'), 'users/bob'), { name: 'Hacked' }));
    await assertFails(updateDoc(doc(as('alice'), 'users/bob'), { role: 'admin' }));
    await assertFails(deleteDoc(doc(as('alice'), 'users/bob')));
  });

  it('a user can delete their own profile', async () => {
    await assertSucceeds(deleteDoc(doc(as('alice'), 'users/alice')));
  });
});

describe('users: admins', () => {
  // Roles are set by hand in the console, never from a client.
  it('an admin cannot change anyone\'s role, even to a known value', async () => {
    const verifiedAdmin = env.authenticatedContext('admin', { email: 'admin@example.com', email_verified: true }).firestore();
    await assertFails(updateDoc(doc(as('admin'), 'users/bob'), { role: 'doctor' }));
    await assertFails(updateDoc(doc(verifiedAdmin, 'users/bob'), { role: 'doctor' }));
    await assertFails(updateDoc(doc(verifiedAdmin, 'users/bob'), { role: 'admin' }));
    await assertFails(updateDoc(doc(verifiedAdmin, 'users/admin'), { role: 'patient' }));
  });

  it('an admin cannot set an unknown role or change any other field of a profile', async () => {
    await assertFails(updateDoc(doc(as('admin'), 'users/bob'), { role: 'superuser' }));
    await assertFails(updateDoc(doc(as('admin'), 'users/bob'), { name: 'Renamed' }));
  });

  it('a patient is not treated as an admin', async () => {
    await assertFails(updateDoc(doc(as('alice'), 'users/bob'), { role: 'doctor' }));
  });
});

describe('everything else', () => {
  it('unknown collections are denied', async () => {
    await assertFails(getDoc(doc(as('alice'), 'bookings/anything')));
    await assertFails(setDoc(doc(as('alice'), 'bookings/anything'), { x: 1 }));
    await assertFails(getDoc(doc(signedOut(), 'secrets/anything')));
  });
});
