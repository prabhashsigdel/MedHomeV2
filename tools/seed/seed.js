#!/usr/bin/env node
// Writes the fictional doctors catalogue (doctors.js) to Firestore with the Admin SDK, which
// bypasses security rules (clients can't write doctors). Idempotent: each doctor has a fixed
// document ID and its catalogue fields are replaced in full, so running it again gives the same
// result. `active` is set only when a doctor is first created: a doctor you deactivated in the
// console stays deactivated. Doctors not in doctors.js are left alone.
//
// Credentials, never inside this repository:
//   node seed.js --key /path/outside/repo/service-account.json
//   GOOGLE_APPLICATION_CREDENTIALS=/path/outside/repo/service-account.json node seed.js
// Emulator (no key): FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node seed.js --project demo-medhome
// Check the data without writing: node seed.js --dry-run
import { existsSync, readFileSync, realpathSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { cert, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { doctors } from './doctors.js';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

const SPECIALTIES = new Set([
  'general_physician', 'cardiology', 'dermatology', 'pediatrics', 'gynecology', 'orthopedics',
  'ent', 'neurology', 'psychiatry', 'ophthalmology', 'dentistry', 'gastroenterology',
]);
const WEEKDAYS = new Set(['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat']);
const HH_MM = /^([01][0-9]|2[0-3]):[0-5][0-9]$/;

function fail(message) {
  console.error(`seed: ${message}`);
  process.exit(1);
}

/** The same limits the app's DoctorMapper enforces, so nothing seeded is silently hidden. */
function validate(doctor) {
  const problems = [];
  const text = (value, max) => typeof value === 'string' && value.trim().length > 0 && value.length <= max;
  const whole = (value, min, max) => Number.isInteger(value) && value >= min && value <= max;
  // Same pattern as Doctor.isValidId in the app (no "_": booking IDs are "{doctorId}_{date}_{time}").
  if (!/^[A-Za-z0-9-]{1,64}$/.test(doctor.id ?? '')) problems.push('id');
  if (!text(doctor.name, 100)) problems.push('name');
  if (!SPECIALTIES.has(doctor.specialty)) problems.push('specialty');
  if (!text(doctor.hospital, 120)) problems.push('hospital');
  if (!whole(doctor.feeNpr, 0, 100000)) problems.push('feeNpr');
  if (!whole(doctor.experienceYears, 0, 70)) problems.push('experienceYears');
  if (!whole(doctor.slotMinutes, 5, 240)) problems.push('slotMinutes');
  if (!text(doctor.bio, 2000)) problems.push('bio');
  for (const [day, ranges] of Object.entries(doctor.weeklySchedule ?? {})) {
    if (!WEEKDAYS.has(day) || !Array.isArray(ranges)) {
      problems.push(`weeklySchedule.${day}`);
      continue;
    }
    const isTime = (value) => typeof value === 'string' && HH_MM.test(value);
    if (ranges.some((range) => !isTime(range?.start) || !isTime(range?.end))) {
      problems.push(`weeklySchedule.${day}`);
      continue;
    }
    const sorted = [...ranges].sort((a, b) => a.start.localeCompare(b.start));
    sorted.forEach((range, i) => {
      const ok = range.start < range.end;
      const overlaps = i > 0 && range.start < sorted[i - 1].end;
      if (!ok || overlaps) problems.push(`weeklySchedule.${day}[${i}]`);
    });
  }
  return problems;
}

function isInside(child, parent) {
  const relative = path.relative(parent, child);
  return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative));
}

/** Refuses a key file inside the project folder: keys must never sit where git could pick them up. */
function loadKey(keyPath) {
  if (!existsSync(keyPath)) fail(`no key file at ${keyPath}`);
  // .native also expands Windows 8.3 short names (D:\PROJEC~1), which plain realpath keeps.
  const real = realpathSync.native(keyPath);
  const root = realpathSync.native(REPO_ROOT);
  const inside = process.platform === 'win32'
    ? isInside(real.toLowerCase(), root.toLowerCase())
    : isInside(real, root);
  if (inside) fail('the service account key is inside the project folder. Move it outside the repository.');
  try {
    return JSON.parse(readFileSync(real, 'utf8'));
  } catch {
    fail('the key file is not valid JSON');
  }
}

const { values: options } = parseArgs({
  options: {
    key: { type: 'string' },
    project: { type: 'string' },
    'dry-run': { type: 'boolean', default: false },
  },
});

const invalid = doctors.map((d) => [d.id, validate(d)]).filter(([, problems]) => problems.length > 0);
if (invalid.length > 0) {
  fail(`invalid doctors.js entries: ${invalid.map(([id, p]) => `${id} (${p.join(', ')})`).join('; ')}`);
}
if (new Set(doctors.map((d) => d.id)).size !== doctors.length) fail('duplicate doctor ids in doctors.js');

if (options['dry-run']) {
  console.log(`seed: ${doctors.length} doctors are valid. Nothing written (dry run).`);
  process.exit(0);
}

const emulator = process.env.FIRESTORE_EMULATOR_HOST;
const keyPath = options.key ?? process.env.GOOGLE_APPLICATION_CREDENTIALS;
let app;
if (emulator) {
  if (!options.project) fail('with the emulator, pass --project (for example demo-medhome)');
  app = initializeApp({ projectId: options.project });
} else {
  if (!keyPath) fail('pass --key <path> or set GOOGLE_APPLICATION_CREDENTIALS (see tools/README.md)');
  const key = loadKey(keyPath);
  if (options.project && options.project !== key.project_id) {
    fail(`--project ${options.project} doesn't match the key's project ${key.project_id}`);
  }
  app = initializeApp({ credential: cert(key), projectId: key.project_id });
}

const db = getFirestore(app);
const refs = doctors.map((d) => db.collection('doctors').doc(d.id));
const existing = new Set((await db.getAll(...refs)).filter((snap) => snap.exists).map((snap) => snap.id));
// Batches hold at most 500 writes.
const BATCH_SIZE = 400;
for (let start = 0; start < doctors.length; start += BATCH_SIZE) {
  const batch = db.batch();
  for (const { id, ...fields } of doctors.slice(start, start + BATCH_SIZE)) {
    const ref = db.collection('doctors').doc(id);
    if (existing.has(id)) {
      // Replace each catalogue field whole (maps too, so a removed weekday goes), keep `active`.
      batch.set(ref, fields, { mergeFields: Object.keys(fields) });
    } else {
      batch.set(ref, { ...fields, active: true });
    }
  }
  await batch.commit();
}
console.log(`seed: wrote ${doctors.length} doctors to ${emulator ? `the emulator (${emulator})` : `project ${app.options.projectId}`}.`);
