// Booking rules: bookings, slot locks and the 3-place quota. Run with `npm test` in
// tools/rules-tests. Everything sits in one describe() so its hooks (and its own emulator
// project) don't run for the other test file.
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
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  setLogLevel,
  Timestamp,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';

const rulesPath = fileURLToPath(new URL('../../../firestore.rules', import.meta.url));

/** Nepal time is a fixed UTC+05:45, as in the app and the rules. */
const NEPAL_OFFSET_MS = 345 * 60_000;
const DAY_MS = 24 * 60 * 60_000;

const DOCTOR = {
  name: 'Asha Rai',
  specialty: 'cardiology',
  hospital: 'Valley Care Hospital',
  feeNpr: 800,
  slotMinutes: 15,
  active: true,
  // Every day, so the tests don't depend on today's weekday.
  weeklySchedule: Object.fromEntries(
    ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'].map((day) => [day, [
      { start: '09:00', end: '13:00' },
      { start: '14:00', end: '17:00' },
    ]]),
  ),
};

const SNAPSHOT = { name: DOCTOR.name, specialty: DOCTOR.specialty, hospital: DOCTOR.hospital, feeNpr: DOCTOR.feeNpr };

const pad = (n) => String(n).padStart(2, '0');

/**
 * The slot [daysAhead] days from today (Nepal date) at hh:mm Nepal time: its start instant and
 * lock ID, built the way the app builds them.
 */
function slot(daysAhead, hh, mm = 0, doctorId = 'doc-001') {
  const localNow = new Date(Date.now() + NEPAL_OFFSET_MS);
  const localMidnight = Date.UTC(localNow.getUTCFullYear(), localNow.getUTCMonth(), localNow.getUTCDate());
  const localStart = localMidnight + daysAhead * DAY_MS + (hh * 60 + mm) * 60_000;
  const local = new Date(localStart);
  const date = `${local.getUTCFullYear()}${pad(local.getUTCMonth() + 1)}${pad(local.getUTCDate())}`;
  return {
    doctorId,
    startAt: Timestamp.fromMillis(localStart - NEPAL_OFFSET_MS),
    slotId: `${doctorId}_${date}_${pad(hh)}${pad(mm)}`,
  };
}

/** The slot of doctor [doctorId] starting at [millis] (on a minute), with its Nepal-time lock ID. */
function slotAtMillis(millis, doctorId) {
  const local = new Date(millis + NEPAL_OFFSET_MS);
  const date = `${local.getUTCFullYear()}${pad(local.getUTCMonth() + 1)}${pad(local.getUTCDate())}`;
  const time = `${pad(local.getUTCHours())}${pad(local.getUTCMinutes())}`;
  return { doctorId, startAt: Timestamp.fromMillis(millis), slotId: `${doctorId}_${date}_${time}` };
}

/** The first [stepMinutes] boundary at least [minutes] from now. */
function boundaryAfter(minutes, stepMinutes) {
  const step = stepMinutes * 60_000;
  return Math.ceil((Date.now() + minutes * 60_000) / step) * step;
}

function bookingData(uid, s, place, overrides = {}) {
  return {
    patientUid: uid,
    doctorId: s.doctorId,
    doctor: SNAPSHOT,
    startAt: s.startAt,
    slotId: s.slotId,
    quotaPlace: place,
    status: 'booked',
    createdAt: serverTimestamp(),
    ...overrides,
  };
}

let nextId = 0;
const newBookingId = () => `booking${++nextId}x${Date.now()}`;

describe('bookings', () => {
  let env;

  const verified = (uid) => env.authenticatedContext(uid, { email: `${uid}@example.com`, email_verified: true }).firestore();
  const unverified = (uid) => env.authenticatedContext(uid, { email: `${uid}@example.com`, email_verified: false }).firestore();
  const signedOut = () => env.unauthenticatedContext().firestore();

  before(async () => {
    setLogLevel('silent');
    env = await initializeTestEnvironment({
      projectId: 'demo-medhome-bookings',
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
      await setDoc(doc(db, 'doctors/doc-001'), DOCTOR);
      await setDoc(doc(db, 'doctors/doc-002'), { ...DOCTOR, name: 'Old Doctor', active: false });
      // Open all day in 5-minute slots, for lead-time tests at any hour.
      const allDay = [{ start: '00:00', end: '23:59' }];
      await setDoc(doc(db, 'doctors/doc-003'), {
        ...DOCTOR,
        slotMinutes: 5,
        weeklySchedule: Object.fromEntries(['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'].map((d) => [d, allDay])),
      });
      // Malformed schedules: never bookable, never an error that lets a write through.
      const everyDay = (ranges) => Object.fromEntries(['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'].map((d) => [d, ranges]));
      await setDoc(doc(db, 'doctors/doc-004'), { ...DOCTOR, weeklySchedule: 'nope' });
      await setDoc(doc(db, 'doctors/doc-005'), { ...DOCTOR, weeklySchedule: everyDay([1, '09:00']) });
      // The slot is only in a 4th range, which the rules (and the app) ignore.
      await setDoc(doc(db, 'doctors/doc-006'), {
        ...DOCTOR,
        weeklySchedule: everyDay([
          { start: '06:00', end: '06:30' }, { start: '07:00', end: '07:30' },
          { start: '08:00', end: '08:30' }, { start: '10:00', end: '11:00' },
        ]),
      });
    });
  });

  /**
   * Writes a booking the way the app does: booking, slot lock and quota place in one batch.
   * [parts] leaves pieces out; the overrides tamper with them.
   */
  function book(db, uid, s, {
    place = 1,
    bookingId = newBookingId(),
    overrides = {},
    parts = { booking: true, lock: true, quota: true },
    lockOverrides = {},
    quotaOverrides = {},
    quotaUid = uid,
  } = {}) {
    const batch = writeBatch(db);
    if (parts.booking) batch.set(doc(db, `bookings/${bookingId}`), bookingData(uid, s, place, overrides));
    if (parts.lock) {
      batch.set(doc(db, `slotLocks/${s.slotId}`), { doctorId: s.doctorId, startAt: s.startAt, bookingId, ...lockOverrides });
    }
    if (parts.quota) {
      batch.set(doc(db, `users/${quotaUid}/bookingQuota/${place}`), { bookingId, startAt: s.startAt, ...quotaOverrides });
    }
    return { bookingId, commit: batch.commit() };
  }

  /** Cancels the way the app does: status + cancelledAt, lock and quota place deleted together. */
  function cancel(db, uid, bookingId, s, { place = 1, deleteLock = true, deleteQuota = true, changes = {} } = {}) {
    const batch = writeBatch(db);
    batch.update(doc(db, `bookings/${bookingId}`), { status: 'cancelled', cancelledAt: serverTimestamp(), ...changes });
    if (deleteLock) batch.delete(doc(db, `slotLocks/${s.slotId}`));
    if (deleteQuota) batch.delete(doc(db, `users/${uid}/bookingQuota/${place}`));
    return batch.commit();
  }

  /** Seeds a booking with rules off (past bookings can't be created by any client). */
  async function seedBooking(uid, s, { place = 1, status = 'booked' } = {}) {
    const bookingId = newBookingId();
    await env.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      const createdAt = Timestamp.fromMillis(s.startAt.toMillis() - DAY_MS);
      await setDoc(doc(db, `bookings/${bookingId}`), { ...bookingData(uid, s, place), createdAt, status });
      if (status === 'booked') {
        await setDoc(doc(db, `slotLocks/${s.slotId}`), { doctorId: s.doctorId, startAt: s.startAt, bookingId });
        await setDoc(doc(db, `users/${uid}/bookingQuota/${place}`), { bookingId, startAt: s.startAt });
      }
    });
    return bookingId;
  }

  describe('booking a slot', () => {
    it('a verified patient can book a free slot', async () => {
      await assertSucceeds(book(verified('alice'), 'alice', slot(1, 10)).commit);
    });

    it('the same slot cannot be booked twice, by anyone', async () => {
      const s = slot(1, 10);
      await assertSucceeds(book(verified('alice'), 'alice', s).commit);
      await assertFails(book(verified('bob'), 'bob', s).commit);
      await assertFails(book(verified('alice'), 'alice', s, { place: 2 }).commit);
    });

    it('an unverified email cannot book', async () => {
      await assertFails(book(unverified('alice'), 'alice', slot(1, 10)).commit);
    });

    it('signed-out users cannot book', async () => {
      await assertFails(book(signedOut(), 'alice', slot(1, 10)).commit);
    });

    it('a slot in the past cannot be booked', async () => {
      await assertFails(book(verified('alice'), 'alice', slot(-1, 10)).commit);
    });

    it('a slot starting within 30 minutes cannot be booked', async () => {
      const tooSoon = slotAtMillis(boundaryAfter(10, 5), 'doc-003');
      await assertFails(book(verified('alice'), 'alice', tooSoon).commit);
      const soonEnough = slotAtMillis(boundaryAfter(40, 5), 'doc-003');
      await assertSucceeds(book(verified('alice'), 'alice', soonEnough).commit);
    });

    it('startAt must be exactly on the minute', async () => {
      const s = slot(1, 10);
      const millis = s.startAt.toMillis();
      await assertFails(book(verified('alice'), 'alice', { ...s, startAt: Timestamp.fromMillis(millis + 30_000) }).commit);
      await assertFails(book(verified('alice'), 'alice', { ...s, startAt: new Timestamp(millis / 1000, 500_000) }).commit);
    });

    it('a malformed schedule, or a 4th range, is never bookable', async () => {
      await assertFails(book(verified('alice'), 'alice', slot(1, 10, 0, 'doc-004')).commit);
      await assertFails(book(verified('alice'), 'alice', slot(1, 9, 0, 'doc-005')).commit);
      await assertFails(book(verified('alice'), 'alice', slot(1, 10, 0, 'doc-006')).commit);
      await assertSucceeds(book(verified('alice'), 'alice', slot(1, 6, 0, 'doc-006')).commit);
    });

    it('only auto IDs name a booking, and the quota place must be a number', async () => {
      await assertFails(book(verified('alice'), 'alice', slot(1, 10), { bookingId: 'not-auto' }).commit);
      await assertFails(book(verified('alice'), 'alice', slot(1, 10), { overrides: { quotaPlace: '1' } }).commit);
      await assertFails(book(verified('alice'), 'alice', slot(1, 10), { quotaOverrides: { note: 'x' } }).commit);
    });

    it('a lock cannot point at an existing booking', async () => {
      const bobs = await seedBooking('bob', slot(1, 10));
      const s = slot(1, 11);
      const db = verified('alice');
      const batch = writeBatch(db);
      batch.set(doc(db, `slotLocks/${s.slotId}`), { doctorId: s.doctorId, startAt: s.startAt, bookingId: bobs });
      await assertFails(batch.commit());
    });

    it("a taken slot can't be freed and re-taken in one write", async () => {
      const s = slot(1, 10);
      await seedBooking('alice', s);
      const db = verified('bob');
      const bookingId = newBookingId();
      const batch = writeBatch(db);
      batch.delete(doc(db, `slotLocks/${s.slotId}`));
      batch.set(doc(db, `bookings/${bookingId}`), bookingData('bob', s, 1));
      batch.set(doc(db, `slotLocks/${s.slotId}`), { doctorId: s.doctorId, startAt: s.startAt, bookingId });
      batch.set(doc(db, 'users/bob/bookingQuota/1'), { bookingId, startAt: s.startAt });
      await assertFails(batch.commit());
    });

    it('a slot more than 15 days ahead cannot be booked', async () => {
      await assertFails(book(verified('alice'), 'alice', slot(16, 10)).commit);
    });

    it('a patient cannot book for someone else', async () => {
      await assertFails(book(verified('alice'), 'bob', slot(1, 10)).commit);
      await assertFails(book(verified('alice'), 'alice', slot(1, 10), { overrides: { patientUid: 'bob' } }).commit);
    });

    it("the time must be one of the doctor's slots", async () => {
      await assertFails(book(verified('alice'), 'alice', slot(1, 10, 7)).commit); // off the 15-minute grid
      await assertFails(book(verified('alice'), 'alice', slot(1, 13, 0)).commit); // lunch break
      await assertFails(book(verified('alice'), 'alice', slot(1, 16, 50)).commit); // would end after 17:00
      await assertFails(book(verified('alice'), 'alice', slot(1, 20, 0)).commit); // closed
      await assertSucceeds(book(verified('alice'), 'alice', slot(1, 16, 45)).commit); // last slot of the day
    });

    it('the slot ID must name the same doctor, date and time as startAt', async () => {
      const s = slot(1, 10);
      const otherDay = slot(2, 10);
      const [, date, time] = s.slotId.split('_');
      await assertFails(book(verified('alice'), 'alice', { ...s, slotId: otherDay.slotId }).commit);
      await assertFails(book(verified('alice'), 'alice', { ...s, slotId: `doc-001_${date}_1015` }).commit);
      await assertFails(book(verified('alice'), 'alice', { ...s, slotId: `doc-002_${date}_${time}` }).commit);
      await assertFails(book(verified('alice'), 'alice', { ...s, slotId: `doc-001_${date}_${time}_x` }).commit);
    });

    it('an inactive or missing doctor cannot be booked', async () => {
      await assertFails(book(verified('alice'), 'alice', slot(1, 10, 0, 'doc-002')).commit);
      await assertFails(book(verified('alice'), 'alice', slot(1, 10, 0, 'doc-404')).commit);
    });

    it('the doctor snapshot must match the doctor', async () => {
      const s = slot(1, 10);
      await assertFails(book(verified('alice'), 'alice', s, { overrides: { doctor: { ...SNAPSHOT, feeNpr: 1 } } }).commit);
      await assertFails(book(verified('alice'), 'alice', s, { overrides: { doctor: { ...SNAPSHOT, name: 'Someone' } } }).commit);
    });

    it('a booking starts as booked, at the server time, with no other fields', async () => {
      const s = slot(1, 10);
      const past = Timestamp.fromMillis(Date.now() - DAY_MS);
      await assertFails(book(verified('alice'), 'alice', s, { overrides: { status: 'cancelled' } }).commit);
      await assertFails(book(verified('alice'), 'alice', s, { overrides: { createdAt: past } }).commit);
      await assertFails(book(verified('alice'), 'alice', s, { overrides: { cancelledAt: serverTimestamp() } }).commit);
      await assertFails(book(verified('alice'), 'alice', s, { overrides: { paid: true } }).commit);
      await assertFails(book(verified('alice'), 'alice', s, { place: 4 }).commit);
    });

    it('the booking, the lock and the quota place can only be written together', async () => {
      const s = slot(1, 10);
      await assertFails(book(verified('alice'), 'alice', s, { parts: { booking: true, lock: false, quota: true } }).commit);
      await assertFails(book(verified('alice'), 'alice', s, { parts: { booking: true, lock: true, quota: false } }).commit);
      await assertFails(book(verified('alice'), 'alice', s, { parts: { booking: false, lock: true, quota: false } }).commit);
      await assertFails(book(verified('alice'), 'alice', s, { parts: { booking: false, lock: false, quota: true } }).commit);
    });

    it('the lock and the quota place must point at the new booking', async () => {
      const s = slot(1, 10);
      await assertFails(book(verified('alice'), 'alice', s, { lockOverrides: { bookingId: 'someoneElse' } }).commit);
      await assertFails(book(verified('alice'), 'alice', s, { lockOverrides: { patientUid: 'alice' } }).commit);
      await assertFails(book(verified('alice'), 'alice', s, { quotaOverrides: { bookingId: 'someoneElse' } }).commit);
      await assertFails(book(verified('alice'), 'alice', s, { quotaUid: 'bob' }).commit);
    });
  });

  describe('at most 3 upcoming', () => {
    it('a 4th upcoming booking is refused', async () => {
      const db = verified('alice');
      await assertSucceeds(book(db, 'alice', slot(1, 9), { place: 1 }).commit);
      await assertSucceeds(book(db, 'alice', slot(1, 10), { place: 2 }).commit);
      await assertSucceeds(book(db, 'alice', slot(1, 11), { place: 3 }).commit);
      for (const place of [1, 2, 3, 4]) {
        await assertFails(book(db, 'alice', slot(2, 9), { place }).commit);
      }
    });

    it('a place frees up once its appointment has passed', async () => {
      await seedBooking('alice', slot(-1, 10), { place: 1 });
      await assertSucceeds(book(verified('alice'), 'alice', slot(1, 10), { place: 1 }).commit);
    });

    it('a place frees up when its booking is cancelled', async () => {
      const db = verified('alice');
      const first = slot(1, 9);
      const { bookingId, commit } = book(db, 'alice', first, { place: 1 });
      await assertSucceeds(commit);
      await assertSucceeds(cancel(db, 'alice', bookingId, first));
      await assertSucceeds(book(db, 'alice', slot(2, 9), { place: 1 }).commit);
    });

    it('a held place cannot be deleted while its booking is upcoming', async () => {
      await seedBooking('alice', slot(1, 10), { place: 1 });
      await assertFails(deleteDoc(doc(verified('alice'), 'users/alice/bookingQuota/1')));
    });

    it("quota places are private and can't be touched by someone else", async () => {
      await seedBooking('bob', slot(1, 10), { place: 1 });
      await assertFails(getDoc(doc(verified('alice'), 'users/bob/bookingQuota/1')));
      await assertFails(deleteDoc(doc(verified('alice'), 'users/bob/bookingQuota/1')));
      await assertSucceeds(getDoc(doc(verified('bob'), 'users/bob/bookingQuota/1')));
    });
  });

  describe('reading', () => {
    it('a patient can read and list their own bookings', async () => {
      const id = await seedBooking('alice', slot(1, 10));
      await assertSucceeds(getDoc(doc(verified('alice'), `bookings/${id}`)));
      await assertSucceeds(getDocs(query(collection(verified('alice'), 'bookings'), where('patientUid', '==', 'alice'))));
    });

    it("a patient cannot read or list someone else's bookings", async () => {
      const id = await seedBooking('bob', slot(1, 10));
      await assertFails(getDoc(doc(verified('alice'), `bookings/${id}`)));
      await assertFails(getDocs(query(collection(verified('alice'), 'bookings'), where('patientUid', '==', 'bob'))));
      await assertFails(getDocs(collection(verified('alice'), 'bookings')));
    });

    it('signed-out users cannot read bookings', async () => {
      const id = await seedBooking('alice', slot(1, 10));
      await assertFails(getDoc(doc(signedOut(), `bookings/${id}`)));
    });

    it('signed-in users can see taken slots, signed-out users cannot', async () => {
      const s = slot(1, 10);
      await seedBooking('bob', s);
      await assertSucceeds(getDoc(doc(verified('alice'), `slotLocks/${s.slotId}`)));
      await assertSucceeds(getDocs(query(collection(verified('alice'), 'slotLocks'), where('doctorId', '==', 'doc-001'))));
      await assertFails(getDoc(doc(signedOut(), `slotLocks/${s.slotId}`)));
    });
  });

  describe('cancelling', () => {
    it('a patient can cancel their own upcoming booking, and the slot frees up', async () => {
      const s = slot(1, 10);
      const id = await seedBooking('alice', s);
      await assertSucceeds(cancel(verified('alice'), 'alice', id, s));
      await assertSucceeds(book(verified('bob'), 'bob', s).commit);
    });

    it("cancelling your own booking can't free someone else's slot", async () => {
      const mine = slot(1, 10);
      const theirs = slot(1, 11);
      const id = await seedBooking('alice', mine);
      await seedBooking('bob', theirs);
      const db = verified('alice');
      const batch = writeBatch(db);
      batch.update(doc(db, `bookings/${id}`), { status: 'cancelled', cancelledAt: serverTimestamp() });
      batch.delete(doc(db, `slotLocks/${mine.slotId}`));
      batch.delete(doc(db, `slotLocks/${theirs.slotId}`));
      await assertFails(batch.commit());
    });

    it('a cancelled booking cannot be cancelled again', async () => {
      const id = await seedBooking('alice', slot(1, 10), { status: 'cancelled' });
      await assertFails(updateDoc(doc(verified('alice'), `bookings/${id}`), { status: 'cancelled', cancelledAt: serverTimestamp() }));
    });

    it('cancelling does not need a verified email (only booking does)', async () => {
      const s = slot(1, 10);
      const id = await seedBooking('alice', s);
      await assertSucceeds(cancel(unverified('alice'), 'alice', id, s));
    });

    it("a patient cannot cancel someone else's booking", async () => {
      const s = slot(1, 10);
      const id = await seedBooking('bob', s);
      await assertFails(cancel(verified('alice'), 'alice', id, s, { deleteQuota: false }));
      await assertFails(cancel(verified('alice'), 'bob', id, s));
    });

    it('a booking that has started cannot be cancelled', async () => {
      const s = slot(-1, 10);
      const id = await seedBooking('alice', s);
      await assertFails(cancel(verified('alice'), 'alice', id, s, { deleteQuota: false }));
    });

    it('cancelling must free the slot in the same write', async () => {
      const s = slot(1, 10);
      const id = await seedBooking('alice', s);
      await assertFails(cancel(verified('alice'), 'alice', id, s, { deleteLock: false }));
    });

    it('cancelling changes only the status and the cancel time', async () => {
      const s = slot(1, 10);
      const id = await seedBooking('alice', s);
      const past = Timestamp.fromMillis(Date.now() - DAY_MS);
      await assertFails(cancel(verified('alice'), 'alice', id, s, { changes: { startAt: slot(2, 10).startAt } }));
      await assertFails(cancel(verified('alice'), 'alice', id, s, { changes: { cancelledAt: past } }));
      await assertFails(cancel(verified('alice'), 'alice', id, s, { changes: { status: 'done' } }));
    });

    it('bookings are never otherwise edited, deleted or un-cancelled', async () => {
      const s = slot(1, 10);
      const id = await seedBooking('alice', s);
      await assertFails(updateDoc(doc(verified('alice'), `bookings/${id}`), { doctor: { ...SNAPSHOT, feeNpr: 1 } }));
      await assertFails(deleteDoc(doc(verified('alice'), `bookings/${id}`)));

      const cancelledId = await seedBooking('alice', slot(2, 10), { status: 'cancelled' });
      await assertFails(updateDoc(doc(verified('alice'), `bookings/${cancelledId}`), { status: 'booked', cancelledAt: deleteField() }));
    });

    it('a slot lock cannot be deleted or changed on its own', async () => {
      const s = slot(1, 10);
      await seedBooking('alice', s);
      await assertFails(deleteDoc(doc(verified('alice'), `slotLocks/${s.slotId}`)));
      await assertFails(deleteDoc(doc(verified('bob'), `slotLocks/${s.slotId}`)));
      await assertFails(updateDoc(doc(verified('alice'), `slotLocks/${s.slotId}`), { bookingId: 'other' }));
    });
  });
});
