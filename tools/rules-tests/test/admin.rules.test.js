// Admin rules: adding and editing doctors, reading any doctor's bookings, and everything
// non-admins (and admins) must not do. Run with `npm test` in tools/rules-tests. Everything sits
// in one describe() so its hooks (and its own emulator project) don't run for the other files.
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
  deleteField,
  doc,
  getCountFromServer,
  getDoc,
  getDocs,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  setLogLevel,
  Timestamp,
  updateDoc,
  where,
} from 'firebase/firestore';

const rulesPath = fileURLToPath(new URL('../../../firestore.rules', import.meta.url));

const DAY_MS = 24 * 60 * 60_000;

/** A doctor as the app's admin form writes it (the stamp says who and when). */
function doctorData(overrides = {}, uid = 'admin') {
  return {
    name: 'Asha Rai',
    specialty: 'cardiology',
    hospital: 'Valley Care Hospital',
    feeNpr: 800,
    experienceYears: 12,
    bio: 'Heart health and blood pressure.',
    slotMinutes: 15,
    weeklySchedule: {
      sun: [{ start: '09:00', end: '13:00' }, { start: '14:00', end: '17:00' }],
      tue: [{ start: '10:00', end: '12:00' }],
    },
    active: true,
    updatedAt: serverTimestamp(),
    updatedBy: uid,
    ...overrides,
  };
}

/** The same doctor as the seed writes it: no stamp, as stored before admins could edit. */
function seededDoctor(overrides = {}) {
  const { updatedAt, updatedBy, ...rest } = doctorData(overrides);
  return { ...rest, weeklySchedule: { sun: [{ start: '09:00', end: '13:00' }] } };
}

describe('admin', () => {
  let env;

  const as = (uid) => env.authenticatedContext(uid, { email: `${uid}@example.com`, email_verified: true }).firestore();
  const signedOut = () => env.unauthenticatedContext().firestore();

  before(async () => {
    setLogLevel('silent');
    env = await initializeTestEnvironment({
      projectId: 'demo-medhome-admin',
      firestore: { rules: readFileSync(rulesPath, 'utf8') },
    });
  });

  after(async () => {
    await env.cleanup();
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await env.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      const createdAt = Timestamp.fromDate(new Date('2026-01-01T00:00:00Z'));
      await setDoc(doc(db, 'users/admin'), { name: 'Admin', email: 'admin@example.com', role: 'admin', createdAt });
      await setDoc(doc(db, 'users/alice'), { name: 'Alice Gurung', email: 'alice@example.com', role: 'patient', createdAt });
      await setDoc(doc(db, 'users/drx'), { name: 'Dr X', email: 'drx@example.com', role: 'doctor', createdAt });
      await setDoc(doc(db, 'doctors/doc-001'), seededDoctor());
      await setDoc(doc(db, 'doctors/doc-002'), seededDoctor({ name: 'Old Doctor', active: false }));
      const later = Timestamp.fromMillis(Date.now() + 2 * DAY_MS);
      await setDoc(doc(db, 'bookings/b1'), {
        patientUid: 'alice', doctorId: 'doc-001', status: 'booked', startAt: later, slotId: 'x', quotaPlace: 1,
      });
      await setDoc(doc(db, 'bookings/b2'), {
        patientUid: 'bob', doctorId: 'doc-002', status: 'booked', startAt: later, slotId: 'y', quotaPlace: 1,
      });
    });
  });

  describe('adding and editing doctors', () => {
    it('an admin can add a well-formed doctor', async () => {
      await assertSucceeds(setDoc(doc(as('admin'), 'doctors/doc-k3f9x2ab7qpz'), doctorData()));
    });

    it('an admin can read a doctor that does not exist yet (the add transaction checks first)', async () => {
      await assertSucceeds(getDoc(doc(as('admin'), 'doctors/doc-404')));
    });

    it('an admin can edit a seeded doctor and show or hide one', async () => {
      const { active, ...details } = doctorData({ feeNpr: 900 });
      await assertSucceeds(updateDoc(doc(as('admin'), 'doctors/doc-001'), details));
      await assertSucceeds(updateDoc(doc(as('admin'), 'doctors/doc-001'), {
        active: false, updatedAt: serverTimestamp(), updatedBy: 'admin',
      }));
      await assertSucceeds(updateDoc(doc(as('admin'), 'doctors/doc-002'), {
        active: true, updatedAt: serverTimestamp(), updatedBy: 'admin',
      }));
    });

    it('every write must carry the server time and the admin\'s own uid', async () => {
      const ref = (db) => doc(db, 'doctors/doc-001');
      await assertFails(updateDoc(ref(as('admin')), { active: false }));
      await assertFails(updateDoc(ref(as('admin')), { active: false, updatedAt: Timestamp.now(), updatedBy: 'admin' }));
      await assertFails(updateDoc(ref(as('admin')), { active: false, updatedAt: serverTimestamp(), updatedBy: 'alice' }));
      await assertFails(setDoc(doc(as('admin'), 'doctors/doc-new'), doctorData({}, 'someone-else')));
    });

    it('an admin can hide a doctor stored in a broken shape, but change nothing else with it', async () => {
      await env.withSecurityRulesDisabled(async (context) => {
        const { experienceYears, ...broken } = seededDoctor({ bio: 'Old\u0007bio' });
        await setDoc(doc(context.firestore(), 'doctors/doc-old'), broken);
      });
      const ref = doc(as('admin'), 'doctors/doc-old');
      await assertSucceeds(updateDoc(ref, { active: false, updatedAt: serverTimestamp(), updatedBy: 'admin' }));
      await assertFails(updateDoc(ref, { active: true, feeNpr: 1, updatedAt: serverTimestamp(), updatedBy: 'admin' }));
      await assertFails(updateDoc(ref, { active: 'no', updatedAt: serverTimestamp(), updatedBy: 'admin' }));
      await assertFails(updateDoc(doc(as('alice'), 'doctors/doc-old'), { active: true, updatedAt: serverTimestamp(), updatedBy: 'alice' }));
    });

    it('nobody can delete a doctor, not even an admin', async () => {
      await assertFails(deleteDoc(doc(as('admin'), 'doctors/doc-001')));
      await assertFails(deleteDoc(doc(as('admin'), 'doctors/doc-002')));
    });

    it('a new doctor\'s ID must be letters, digits and hyphens (no "_")', async () => {
      await assertFails(setDoc(doc(as('admin'), 'doctors/doc_001'), doctorData()));
      await assertFails(setDoc(doc(as('admin'), `doctors/${'a'.repeat(65)}`), doctorData()));
      await assertSucceeds(setDoc(doc(as('admin'), `doctors/${'a'.repeat(64)}`), doctorData()));
    });

    it('accepts the limits themselves and a bio with line breaks', async () => {
      await assertSucceeds(setDoc(doc(as('admin'), 'doctors/doc-max'), doctorData({
        name: 'n'.repeat(100),
        hospital: 'h'.repeat(120),
        bio: `${'b'.repeat(1000)}\n\n${'c'.repeat(998)}`,
        feeNpr: 100000,
        experienceYears: 70,
        slotMinutes: 240,
      })));
      await assertSucceeds(setDoc(doc(as('admin'), 'doctors/doc-min'), doctorData({
        name: 'N', hospital: 'H', bio: 'B', feeNpr: 0, experienceYears: 0, slotMinutes: 5, weeklySchedule: {}, active: false,
      })));
    });

    it('accepts a 100-character devanagari name with zero-width joiners', async () => {
      const name = 'क्‍ष'.repeat(25);
      await assertSucceeds(setDoc(doc(as('admin'), 'doctors/doc-ne'), doctorData({ name, bio: 'एक\n\nदुई' })));
      await assertFails(setDoc(doc(as('admin'), 'doctors/doc-ne2'), doctorData({ name: `${name}क` })));
    });

    it('an admin whose email is not verified cannot write doctors', async () => {
      const unverified = env.authenticatedContext('admin', { email: 'admin@example.com', email_verified: false }).firestore();
      await assertFails(setDoc(doc(unverified, 'doctors/doc-new'), doctorData()));
      await assertFails(updateDoc(doc(unverified, 'doctors/doc-001'), { active: false, updatedAt: serverTimestamp(), updatedBy: 'admin' }));
    });

    it('accepts 3 ranges a day, touching ranges and devanagari text', async () => {
      await assertSucceeds(setDoc(doc(as('admin'), 'doctors/doc-ok'), doctorData({
        name: 'आशा राई',
        weeklySchedule: {
          mon: [{ start: '14:00', end: '17:00' }, { start: '09:00', end: '13:00' }, { start: '13:00', end: '14:00' }],
          sat: [{ start: '00:00', end: '23:59' }],
        },
      })));
    });

    const invalid = {
      'an empty name': { name: '' },
      'a name of spaces': { name: '   ' },
      'a name over 100 characters': { name: 'n'.repeat(101) },
      'a name with a line break': { name: 'Asha\nRai' },
      'a name with a control character': { name: 'Asha\u0007Rai' },
      'a name with a space before it': { name: ' Asha Rai' },
      'a name with a space after it': { name: 'Asha Rai ' },
      'a name with a double space': { name: 'Asha  Rai' },
      'a name with a no-break space': { name: 'Asha Rai' },
      'a name with a zero-width space': { name: 'Asha​Rai' },
      'a name with a bidi override': { name: 'Asha‮Rai' },
      'a hospital with a tab': { hospital: 'Valley\tCare' },
      'a bio with two blank lines in a row': { bio: 'One\n\n\nTwo' },
      'a bio line ending in a space': { bio: 'One \nTwo' },
      'a bio starting with a line break': { bio: '\nOne' },
      'a name that is not text': { name: 42 },
      'an unknown specialty': { specialty: 'astrology' },
      'an empty hospital': { hospital: '' },
      'a hospital over 120 characters': { hospital: 'h'.repeat(121) },
      'a negative fee': { feeNpr: -1 },
      'a fee over 100000': { feeNpr: 100001 },
      'a fee with a fraction': { feeNpr: 800.5 },
      'a fee as text': { feeNpr: '800' },
      'experience over 70': { experienceYears: 71 },
      'negative experience': { experienceYears: -1 },
      'an empty bio': { bio: '' },
      'a bio over 2000 characters': { bio: 'b'.repeat(2001) },
      'a bio with a control character': { bio: 'Hello\u0000there' },
      'slots under 5 minutes': { slotMinutes: 4 },
      'slots over 240 minutes': { slotMinutes: 241 },
      'active that is not a boolean': { active: 'yes' },
      'an unknown field': { rating: 5 },
      'a schedule that is not a map': { weeklySchedule: 'nope' },
      'an unknown weekday': { weeklySchedule: { funday: [] } },
      'a day that is not a list': { weeklySchedule: { mon: { start: '09:00', end: '13:00' } } },
      'a range that is not a map': { weeklySchedule: { mon: ['09:00-13:00'] } },
      'a time without a leading zero': { weeklySchedule: { mon: [{ start: '9:00', end: '13:00' }] } },
      'a time with seconds': { weeklySchedule: { mon: [{ start: '09:00:00', end: '13:00' }] } },
      'midnight as 24:00': { weeklySchedule: { mon: [{ start: '20:00', end: '24:00' }] } },
      'a range ending before it starts': { weeklySchedule: { mon: [{ start: '13:00', end: '09:00' }] } },
      'an empty range': { weeklySchedule: { mon: [{ start: '09:00', end: '09:00' }] } },
      'a range with an extra field': { weeklySchedule: { mon: [{ start: '09:00', end: '13:00', note: 'x' }] } },
      'overlapping ranges': {
        weeklySchedule: { mon: [{ start: '09:00', end: '13:00' }, { start: '12:00', end: '15:00' }] },
      },
      'a third range overlapping the first': {
        weeklySchedule: { mon: [{ start: '09:00', end: '11:00' }, { start: '14:00', end: '15:00' }, { start: '10:00', end: '12:00' }] },
      },
      'four ranges on a day': {
        weeklySchedule: {
          mon: [
            { start: '06:00', end: '07:00' }, { start: '08:00', end: '09:00' },
            { start: '10:00', end: '11:00' }, { start: '12:00', end: '13:00' },
          ],
        },
      },
    };

    for (const [what, overrides] of Object.entries(invalid)) {
      it(`an admin cannot save ${what}`, async () => {
        await assertFails(setDoc(doc(as('admin'), 'doctors/doc-bad'), doctorData(overrides)));
        await assertFails(updateDoc(doc(as('admin'), 'doctors/doc-001'), doctorData(overrides)));
      });
    }

    it('an admin cannot leave out a field, on a new doctor or an edit', async () => {
      const { experienceYears, ...withoutExperience } = doctorData();
      await assertFails(setDoc(doc(as('admin'), 'doctors/doc-bad'), withoutExperience));
      await assertFails(updateDoc(doc(as('admin'), 'doctors/doc-001'), {
        bio: deleteField(), updatedAt: serverTimestamp(), updatedBy: 'admin',
      }));
    });
  });

  describe('non-admins and doctors', () => {
    for (const [who, db] of [['a patient', () => as('alice')], ['a doctor', () => as('drx')], ['a signed-out user', signedOut]]) {
      it(`${who} cannot add, edit, hide or delete a doctor`, async () => {
        await assertFails(setDoc(doc(db(), 'doctors/doc-new'), doctorData({}, 'alice')));
        await assertFails(updateDoc(doc(db(), 'doctors/doc-001'), { active: false, updatedAt: serverTimestamp(), updatedBy: 'alice' }));
        await assertFails(setDoc(doc(db(), 'doctors/doc-001'), doctorData({}, 'alice')));
        await assertFails(deleteDoc(doc(db(), 'doctors/doc-001')));
      });
    }

    it('a doctor cannot read inactive doctors or anyone\'s bookings', async () => {
      await assertFails(getDoc(doc(as('drx'), 'doctors/doc-002')));
      await assertFails(getDoc(doc(as('drx'), 'bookings/b1')));
      await assertFails(getDocs(query(collection(as('drx'), 'bookings'), where('doctorId', '==', 'doc-001'))));
    });

    it('a patient cannot list a doctor\'s bookings', async () => {
      await assertFails(getDocs(query(collection(as('alice'), 'bookings'), where('doctorId', '==', 'doc-001'))));
      await assertFails(getDoc(doc(as('alice'), 'bookings/b2')));
    });

    it('patients and doctors cannot make themselves admins', async () => {
      await assertFails(updateDoc(doc(as('alice'), 'users/alice'), { role: 'admin' }));
      await assertFails(updateDoc(doc(as('drx'), 'users/drx'), { role: 'admin' }));
      await assertFails(updateDoc(doc(as('drx'), 'users/alice'), { role: 'admin' }));
    });

    it('an admin can only set a known role, and change nothing else with it', async () => {
      await assertFails(updateDoc(doc(as('admin'), 'users/alice'), { role: 'superadmin' }));
      await assertFails(updateDoc(doc(as('admin'), 'users/alice'), { role: 'admin', email: 'evil@example.com' }));
      await assertFails(updateDoc(doc(as('admin'), 'users/alice'), { name: 'Renamed' }));
    });
  });

  describe("reading a doctor's bookings", () => {
    const upcoming = (db, doctorId) => query(
      collection(db, 'bookings'),
      where('doctorId', '==', doctorId),
      where('status', '==', 'booked'),
      where('startAt', '>', Timestamp.now()),
      orderBy('startAt'),
    );

    it("an admin can list and count any doctor's upcoming bookings and read one", async () => {
      await assertSucceeds(getDocs(upcoming(as('admin'), 'doc-001')));
      await assertSucceeds(getCountFromServer(upcoming(as('admin'), 'doc-002')));
      await assertSucceeds(getDoc(doc(as('admin'), 'bookings/b1')));
    });

    it('an admin can read a patient\'s profile (for the first name)', async () => {
      await assertSucceeds(getDoc(doc(as('admin'), 'users/alice')));
    });

    it('an admin cannot create, change or delete a booking', async () => {
      await assertFails(setDoc(doc(as('admin'), 'bookings/new1'), {
        patientUid: 'alice', doctorId: 'doc-001', status: 'booked', startAt: Timestamp.now(), slotId: 'z', quotaPlace: 1,
        doctor: {}, createdAt: serverTimestamp(),
      }));
      await assertFails(updateDoc(doc(as('admin'), 'bookings/b1'), { status: 'cancelled', cancelledAt: serverTimestamp() }));
      await assertFails(deleteDoc(doc(as('admin'), 'bookings/b1')));
    });

    it("an admin cannot touch slot locks or a patient's booking quota", async () => {
      await assertFails(setDoc(doc(as('admin'), 'slotLocks/doc-001_20300101_0900'), {
        doctorId: 'doc-001', startAt: Timestamp.now(), bookingId: 'b1',
      }));
      await assertFails(setDoc(doc(as('admin'), 'users/alice/bookingQuota/1'), { bookingId: 'b1', startAt: Timestamp.now() }));
      await assertFails(getDoc(doc(as('admin'), 'users/alice/bookingQuota/1')));
    });
  });
});
