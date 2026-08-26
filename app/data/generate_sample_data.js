/*
 * Generates app/data/sample_data.json - a full demo dataset for the JSON importer.
 * Deterministic: a fixed PRNG seed, so re-running produces the same file.
 */
const fs = require('fs');

// --- deterministic PRNG -----------------------------------------------------
let seed = 20260826;
function rnd() {
  seed = (seed * 1664525 + 1013904223) % 4294967296;
  return seed / 4294967296;
}
const pick = (arr) => arr[Math.floor(rnd() * arr.length)];
const rint = (lo, hi) => lo + Math.floor(rnd() * (hi - lo + 1));

const pad = (n) => String(n).padStart(2, '0');
const fmt = (d) => `${pad(d.getMonth() + 1)}-${pad(d.getDate())}-${d.getFullYear()}`;
const dateOf = (y, m, d) => new Date(y, m - 1, d);

// --- classrooms -------------------------------------------------------------
// Section names follow the usual Philippine public-school convention.
const CLASSROOMS = [
  { name: 'Grade 7 - Sampaguita', start: '07:30 AM', end: '12:00 PM' },
  { name: 'Grade 7 - Ilang-Ilang', start: '07:30 AM', end: '12:00 PM' },
  { name: 'Grade 8 - Rizal', start: '08:00 AM', end: '01:00 PM' },
  { name: 'Grade 8 - Mabini', start: '10:00 AM', end: '03:00 PM' },
  { name: 'Grade 9 - Narra', start: '12:30 PM', end: '05:30 PM' },
  { name: 'Grade 10 - Molave', start: '01:00 PM', end: '06:00 PM' },
];

// --- real, mappable Metro Manila addresses ----------------------------------
const STREETS = [
  ['Maginhawa Street', 'Teachers Village East'],
  ['Scout Borromeo Street', 'South Triangle'],
  ['Kamias Road', 'East Kamias'],
  ['Anonas Street', 'Project 3'],
  ['Katipunan Avenue', 'Loyola Heights'],
  ['Tomas Morato Avenue', 'Laging Handa'],
  ['Timog Avenue', 'Sacred Heart'],
  ['V. Luna Avenue', 'Sikatuna Village'],
  ['Mother Ignacia Avenue', 'Paligsahan'],
  ['Banawe Street', 'Santa Mesa Heights'],
  ['Del Monte Avenue', 'Talayan'],
  ['N.S. Amoranto Street', 'La Loma'],
  ['E. Rodriguez Sr. Avenue', 'New Manila'],
  ['Boni Serrano Avenue', 'Murphy'],
  ['P. Tuazon Boulevard', 'Cubao'],
  ['15th Avenue', 'Cubao'],
  ['Congressional Avenue', 'Bahay Toro'],
  ['Visayas Avenue', 'Vasra'],
  ['Mindanao Avenue', 'Talipapa'],
  ['Quirino Highway', 'Novaliches Proper'],
  ['Regalado Avenue', 'Fairview'],
  ['Commonwealth Avenue', 'Batasan Hills'],
  ['Don Antonio Drive', 'Holy Spirit'],
  ['Tandang Sora Avenue', 'Culiat'],
  ['Luzon Avenue', 'Old Balara'],
  ['Roces Avenue', 'Paligsahan'],
  ['Kalayaan Avenue', 'Diliman'],
  ['Malakas Street', 'Pinyahan'],
  ['Masikap Street', 'Pinyahan'],
  ['Panay Avenue', 'South Triangle'],
];

// --- names ------------------------------------------------------------------
const M_FIRST = ['Miguel', 'Jose', 'Angelo', 'Rafael', 'Gabriel', 'Nathaniel', 'Emmanuel', 'Carlo',
  'Lorenzo', 'Andres', 'Paolo', 'Kristian', 'Dominic', 'Elijah', 'Marco', 'Sebastian', 'Julius', 'Rodel'];
const F_FIRST = ['Maria', 'Andrea', 'Sofia', 'Isabella', 'Althea', 'Camille', 'Beatriz', 'Kyla',
  'Danica', 'Patricia', 'Nicole', 'Trisha', 'Jasmine', 'Alyssa', 'Bianca', 'Chloe', 'Erika', 'Mikaela'];
const LAST = ['Dela Cruz', 'Santos', 'Reyes', 'Bautista', 'Ocampo', 'Garcia', 'Mendoza', 'Torres',
  'Ramos', 'Aquino', 'Villanueva', 'Castillo', 'Navarro', 'Gonzales', 'Fernandez', 'Domingo',
  'Salazar', 'Pascual', 'Bernardo', 'Alvarez', 'Marquez', 'Rosales', 'Tolentino', 'Padilla',
  'Escobar', 'Lagman', 'Manalo', 'Cabrera', 'Yumul', 'Sarmiento', 'Bituin', 'Panganiban',
  'Magbanua', 'Suarez', 'Delfin', 'Batungbakal', 'Espiritu', 'Guevarra', 'Hidalgo', 'Ignacio'];

const GUARDIAN_M = ['Ricardo', 'Eduardo', 'Ferdinand', 'Benjamin', 'Arnel', 'Rolando', 'Danilo', 'Efren', 'Nestor', 'Alfredo'];
const GUARDIAN_F = ['Corazon', 'Imelda', 'Luzviminda', 'Remedios', 'Josefina', 'Marilou', 'Evelyn', 'Rosalinda', 'Teresita', 'Gloria'];

let mobileSeq = 0;
function mobile() {
  const prefixes = ['0917', '0918', '0919', '0905', '0926', '0936', '0995', '0977'];
  mobileSeq++;
  return `${prefixes[mobileSeq % prefixes.length]} ${String(200 + mobileSeq).padStart(3, '0')} ${String(1000 + mobileSeq * 7).slice(-4)}`;
}

// --- students ---------------------------------------------------------------
// Grade level drives both the section and a plausible birth year.
const GRADE_OF_CLASS = {
  'Grade 7 - Sampaguita': 7,
  'Grade 7 - Ilang-Ilang': 7,
  'Grade 8 - Rizal': 8,
  'Grade 8 - Mabini': 8,
  'Grade 9 - Narra': 9,
  'Grade 10 - Molave': 10,
};
const BIRTH_YEAR_OF_GRADE = { 7: 2013, 8: 2012, 9: 2011, 10: 2010 };

const CLUBS = ['Science Club', 'Journalism', 'Sports - Basketball', 'Sports - Volleyball',
  'Drama Club', 'Math Circle', 'Rondalla', 'None'];
const LRN_BASE = 136420110000;

const usedNames = new Set();
const students = [];
let lrnSeq = 0;

const ROSTER_PLAN = [
  ['Grade 7 - Sampaguita', 8],
  ['Grade 7 - Ilang-Ilang', 7],
  ['Grade 8 - Rizal', 8],
  ['Grade 8 - Mabini', 7],
  ['Grade 9 - Narra', 7],
  ['Grade 10 - Molave', 7],
];

for (const [className, count] of ROSTER_PLAN) {
  const grade = GRADE_OF_CLASS[className];
  for (let i = 0; i < count; i++) {
    let first, last, gender, key;
    do {
      gender = rnd() < 0.5 ? 'M' : 'F';
      first = gender === 'M' ? pick(M_FIRST) : pick(F_FIRST);
      last = pick(LAST);
      key = `${first} ${last}`;
    } while (usedNames.has(key));
    usedNames.add(key);

    const birthYear = BIRTH_YEAR_OF_GRADE[grade] + (rnd() < 0.2 ? -1 : 0);
    const birthday = dateOf(birthYear, rint(1, 12), rint(1, 28));

    const [street, barangay] = pick(STREETS);
    const address = `${rint(2, 480)} ${street}, Barangay ${barangay}, Quezon City, Metro Manila`;

    // A guardian shares the student's surname more often than not, but not always.
    const guardianIsMother = rnd() < 0.62;
    const guardianLast = rnd() < 0.8 ? last : pick(LAST);
    const guardians = [{
      name: `${guardianIsMother ? pick(GUARDIAN_F) : pick(GUARDIAN_M)} ${guardianLast}`,
      relationship: guardianIsMother ? 'Mother' : 'Father',
      phones: [mobile()],
    }];
    // Roughly a third of the roster has a second contact on file.
    if (rnd() < 0.35) {
      guardians.push({
        name: `${guardianIsMother ? pick(GUARDIAN_M) : pick(GUARDIAN_F)} ${guardianLast}`,
        relationship: guardianIsMother ? 'Father' : 'Mother',
        phones: [mobile()],
      });
    } else if (rnd() < 0.12) {
      guardians.push({
        name: `${pick(GUARDIAN_F)} ${pick(LAST)}`,
        relationship: 'Guardian',
        phones: [mobile()],
      });
    }

    lrnSeq++;
    students.push({
      firstName: first,
      lastName: last,
      gender,
      birthday: fmt(birthday),
      address,
      contactNumber: mobile(),
      guardians,
      classes: [className],
      custom: {
        'LRN': String(LRN_BASE + lrnSeq * 37),
        'Club': pick(CLUBS),
        'Learning Modality': rnd() < 0.85 ? 'Face-to-face' : 'Blended',
        'Rides a bicycle to school': rnd() < 0.3 ? 'Yes' : 'No',
      },
      behavior: [],
    });
  }
}

// A handful of students sit in a second section - the app supports multi-class
// membership and the demo should exercise it.
const EXTRA_CLASS_PICKS = [2, 5, 11, 19, 26, 33, 38];
for (const idx of EXTRA_CLASS_PICKS) {
  if (idx >= students.length) continue;
  const s = students[idx];
  const other = CLASSROOMS.map((c) => c.name).filter((n) => !s.classes.includes(n));
  s.classes.push(pick(other));
}

// --- seating ----------------------------------------------------------------
// Coordinates are 0..1 fractions of the chart. Laid out as a tidy grid per class,
// with a few desks deliberately left unplaced so the "unplaced" tray is not empty.
const seatCursor = {};
for (const s of students) {
  s.seating = {};
  for (const className of s.classes) {
    seatCursor[className] = (seatCursor[className] || 0);
    const n = seatCursor[className]++;
    if (n >= 6 && rnd() < 0.35) continue; // left unseated on purpose
    const cols = 4;
    const col = n % cols;
    const row = Math.floor(n / cols);
    s.seating[className] = {
      x: Number(((col + 0.5) / cols).toFixed(4)),
      y: Number(((row + 0.5) / 5).toFixed(4)),
    };
  }
}

// --- behaviour incidents ----------------------------------------------------
const POSITIVE = [
  ['Outstanding recitation', 'Explained the water cycle to the class without notes and answered follow-up questions.'],
  ['Helped a classmate', 'Stayed after class to walk a classmate through long division.'],
  ['Leadership', 'Organised the group so the laboratory activity finished on time.'],
  ['Improved output', 'Second draft of the essay addressed every comment on the first.'],
  ['Perfect attendance for the month', 'Present and on time every school day this month.'],
  ['Care for the classroom', 'Volunteered to reorganise the reading corner during break.'],
];
const NEGATIVE = [
  ['Late submission', 'Turned in the reaction paper two days after the deadline.'],
  ['Disruptive during discussion', 'Repeatedly talked over classmates during the group reporting.'],
  ['Incomplete homework', 'Came to class without the assigned problem set for the second time this week.'],
  ['Tardy', 'Arrived twenty minutes into the first period.'],
  ['Phone use in class', 'Using a phone during the quiz; device surrendered until dismissal.'],
];
const NEUTRAL = [
  ['Parent conference', 'Met with the guardian to discuss study habits at home. Follow-up agreed for next month.'],
  ['Seat moved', 'Moved to the front row to help with focus during discussions.'],
  ['Excused for competition', 'Out of class to represent the school in the division science quiz.'],
  ['Health note', 'School clinic advised light activity for one week after a sprained ankle.'],
];

function incidentDate() {
  // Spread across the current school year, weighted toward recent months.
  const months = [[2025, 9], [2025, 10], [2025, 11], [2025, 12], [2026, 1], [2026, 2], [2026, 2], [2026, 3]];
  const [y, m] = pick(months);
  return fmt(dateOf(y, m, rint(1, 28)));
}

for (const s of students) {
  const n = rint(0, 4);
  for (let i = 0; i < n; i++) {
    const roll = rnd();
    let category, source;
    if (roll < 0.55) { category = 'Positive'; source = pick(POSITIVE); }
    else if (roll < 0.85) { category = 'Negative'; source = pick(NEGATIVE); }
    else { category = 'Neutral'; source = pick(NEUTRAL); }
    s.behavior.push({
      title: source[0],
      category,
      description: source[1],
      date: incidentDate(),
    });
  }
}

// --- identifiers ------------------------------------------------------------
// Must mirror JsonSyncEngine.participantKey: lastName_firstName_<first class>.
const idOf = (s) => `${s.lastName}_${s.firstName}_${s.classes[0]}`;

// --- grading terms ----------------------------------------------------------
const TERMS = [
  { name: 'Quarter 1', startDate: '08-18-2025', endDate: '10-24-2025', isActive: false },
  { name: 'Quarter 2', startDate: '10-27-2025', endDate: '01-16-2026', isActive: false },
  { name: 'Quarter 3', startDate: '01-19-2026', endDate: '03-27-2026', isActive: true },
  { name: 'Quarter 4', startDate: '03-30-2026', endDate: '06-12-2026', isActive: false },
];

// DepEd's actual weighting for a junior-high science/maths class.
const CATEGORIES = [
  { name: 'Written Work', weight: 30, term: '' },
  { name: 'Performance Task', weight: 50, term: '' },
  { name: 'Quarterly Assessment', weight: 20, term: '' },
];

// --- rubrics ----------------------------------------------------------------
const RUBRICS = [
  {
    name: 'Essay rubric (20 pts)',
    levels: [
      { label: 'Exemplary', points: 20, descriptor: 'Clear thesis, evidence throughout, no mechanical errors that impede meaning.', order: 0 },
      { label: 'Proficient', points: 16, descriptor: 'Clear thesis with mostly relevant support; minor mechanical errors.', order: 1 },
      { label: 'Developing', points: 12, descriptor: 'Thesis present but thinly supported; errors distract in places.', order: 2 },
      { label: 'Beginning', points: 8, descriptor: 'No clear thesis; support is missing or off-topic.', order: 3 },
    ],
  },
  {
    name: 'Oral presentation (25 pts)',
    levels: [
      { label: 'Excellent', points: 25, descriptor: 'Audible, well paced, holds eye contact, answers questions confidently.', order: 0 },
      { label: 'Very good', points: 20, descriptor: 'Clear delivery with occasional reliance on notes.', order: 1 },
      { label: 'Satisfactory', points: 15, descriptor: 'Understandable but read largely from notes.', order: 2 },
      { label: 'Needs practice', points: 10, descriptor: 'Hard to hear; content out of order.', order: 3 },
    ],
  },
  {
    name: 'Laboratory work (15 pts)',
    levels: [
      { label: 'Independent', points: 15, descriptor: 'Follows the procedure and records observations without prompting.', order: 0 },
      { label: 'Guided', points: 11, descriptor: 'Completes the procedure with occasional reminders.', order: 1 },
      { label: 'Assisted', points: 7, descriptor: 'Needs step-by-step supervision throughout.', order: 2 },
    ],
  },
];

// --- gradebook --------------------------------------------------------------
// Every student carries a latent ability, so their marks correlate across
// assessments the way a real class does, instead of looking like noise.
// Centred so the class reads like a real one: most between 78 and 92, a genuine top few, and a
// handful below the 75 passing mark for the Early Warning screen to pick up. A demo where the
// whole section is failing looks like the app is broken rather than like a class.
const ability = new Map();
for (const s of students) ability.set(idOf(s), 0.76 + rnd() * 0.24);

function scoreFor(s, maxPoints, difficulty) {
  // Plan difficulties run 0.81..0.94; rescale to a mild 0.92..1.00 modifier so a hard paper
  // pulls the whole class down a little rather than off a cliff.
  const modifier = 0.92 + (difficulty - 0.81) * (0.08 / 0.13);
  const jitter = (rnd() - 0.5) * 0.09;
  const pct = Math.max(0.55, Math.min(1, ability.get(idOf(s)) * modifier + jitter));
  return String(Math.round(pct * maxPoints));
}

const ASSESSMENT_PLAN = [
  // Quarter 1
  ['Q1 Quiz 1 - Scientific Method', 20, 'Written Work', 'Quarter 1', '', '08-29-2025', '09-01-2025', 0.94],
  ['Q1 Quiz 2 - Matter and Its Properties', 25, 'Written Work', 'Quarter 1', '', '09-19-2025', '09-22-2025', 0.88],
  ['Q1 Laboratory - Separating Mixtures', 15, 'Performance Task', 'Quarter 1', 'Laboratory work (15 pts)', '09-26-2025', '09-29-2025', 0.92],
  ['Q1 Essay - Why Science Matters', 20, 'Performance Task', 'Quarter 1', 'Essay rubric (20 pts)', '10-10-2025', '10-15-2025', 0.86],
  ['Q1 Periodical Exam', 50, 'Quarterly Assessment', 'Quarter 1', '', '10-22-2025', '10-24-2025', 0.83],
  // Quarter 2
  ['Q2 Quiz 1 - Living Things', 20, 'Written Work', 'Quarter 2', '', '11-07-2025', '11-10-2025', 0.9],
  ['Q2 Quiz 2 - Cells and Tissues', 25, 'Written Work', 'Quarter 2', '', '11-28-2025', '12-01-2025', 0.85],
  ['Q2 Group Report - Ecosystems', 25, 'Performance Task', 'Quarter 2', 'Oral presentation (25 pts)', '12-05-2025', '12-09-2025', 0.9],
  ['Q2 Laboratory - Using the Microscope', 15, 'Performance Task', 'Quarter 2', 'Laboratory work (15 pts)', '01-09-2026', '01-12-2026', 0.93],
  ['Q2 Periodical Exam', 50, 'Quarterly Assessment', 'Quarter 2', '', '01-14-2026', '01-16-2026', 0.81],
  // Quarter 3 - the active period, so it opens populated
  ['Q3 Quiz 1 - Force and Motion', 20, 'Written Work', 'Quarter 3', '', '01-30-2026', '02-02-2026', 0.89],
  ['Q3 Quiz 2 - Work and Energy', 25, 'Written Work', 'Quarter 3', '', '02-20-2026', '02-23-2026', 0.84],
  ['Q3 Performance Task - Rube Goldberg Machine', 40, 'Performance Task', 'Quarter 3', '', '03-06-2026', '03-11-2026', 0.91],
  ['Q3 Oral Defense - Investigatory Project', 25, 'Performance Task', 'Quarter 3', 'Oral presentation (25 pts)', '03-18-2026', '03-20-2026', 0.87],
  ['Q3 Periodical Exam', 50, 'Quarterly Assessment', 'Quarter 3', '', '03-25-2026', '03-27-2026', 0.82],
  // Quarter 4 - deliberately only partly marked, which is what a live term looks like
  ['Q4 Quiz 1 - The Solar System', 20, 'Written Work', 'Quarter 4', '', '04-17-2026', '04-20-2026', 0.9],
];

const gradeBook = ASSESSMENT_PLAN.map(([name, maxPoints, category, term, rubric, examDate, checkDate, difficulty]) => {
  const isFuture = term === 'Quarter 4';
  const grades = students.map((s) => {
    // A few blanks in the live quarter; earlier quarters are fully marked.
    if (isFuture && rnd() < 0.25) return { studentIdentifier: idOf(s), score: '' };
    return { studentIdentifier: idOf(s), score: scoreFor(s, maxPoints, difficulty) };
  });
  return { name, maxPoints, examDate, checkDate, term, category, rubric, grades };
});

// --- attendance -------------------------------------------------------------
function schoolDays(from, to) {
  const days = [];
  const cur = new Date(from);
  while (cur <= to) {
    const dow = cur.getDay();
    if (dow !== 0 && dow !== 6) days.push(new Date(cur));
    cur.setDate(cur.getDate() + 1);
  }
  return days;
}

// Per-student attendance disposition, so the same few students are the ones
// with a pattern of absences rather than everyone being equally random.
const reliability = new Map();
for (const s of students) reliability.set(idOf(s), 0.86 + rnd() * 0.13);

function buildRecord(name, from, to, roster, { leaveTail = 0 } = {}) {
  const days = schoolDays(from, to);
  const marked = days.length - leaveTail;
  return {
    name,
    startDate: fmt(from),
    endDate: fmt(to),
    participants: roster.map((s) => {
      const attendance = {};
      days.forEach((d, i) => {
        if (i >= marked) {
          attendance[fmt(d)] = 'NOT_SET'; // not yet taken, as of "today"
          return;
        }
        const roll = rnd();
        const r = reliability.get(idOf(s));
        if (roll < r) attendance[fmt(d)] = 'PRESENT';
        else if (roll < r + 0.06) attendance[fmt(d)] = 'ABSENT';
        else if (roll < r + 0.11) attendance[fmt(d)] = 'EXCUSED';
        else attendance[fmt(d)] = 'ABSENT';
      });
      return { studentIdentifier: idOf(s), attendance };
    }),
  };
}

const byClass = (className) => students.filter((s) => s.classes.includes(className));

const attendanceRecord = [
  buildRecord('Quarter 3 - Grade 7 Sampaguita', dateOf(2026, 1, 19), dateOf(2026, 2, 27), byClass('Grade 7 - Sampaguita')),
  buildRecord('Quarter 3 - Grade 8 Rizal', dateOf(2026, 1, 19), dateOf(2026, 2, 27), byClass('Grade 8 - Rizal')),
  buildRecord('March 2026 - Grade 9 Narra', dateOf(2026, 3, 2), dateOf(2026, 3, 27), byClass('Grade 9 - Narra'), { leaveTail: 5 }),
  buildRecord('March 2026 - Grade 10 Molave', dateOf(2026, 3, 2), dateOf(2026, 3, 27), byClass('Grade 10 - Molave'), { leaveTail: 5 }),
];

// --- saved filters ----------------------------------------------------------
const savedFilters = [
  { name: 'Grade 7 - Sampaguita', field: 'Classroom', comparison: 'member of', value1: 'Grade 7 - Sampaguita', value2: '', displayOrder: 0 },
  { name: 'Grade 8 - Rizal', field: 'Classroom', comparison: 'member of', value1: 'Grade 8 - Rizal', value2: '', displayOrder: 1 },
  { name: 'Grade 9 - Narra', field: 'Classroom', comparison: 'member of', value1: 'Grade 9 - Narra', value2: '', displayOrder: 2 },
  { name: 'Grade 10 - Molave', field: 'Classroom', comparison: 'member of', value1: 'Grade 10 - Molave', value2: '', displayOrder: 3 },
  { name: 'March birthdays', field: 'Birthday', comparison: 'birth_month', value1: '3', value2: '', displayOrder: 4 },
  { name: 'Aged 13 to 15', field: 'Age', comparison: 'In between', value1: '13', value2: '15', displayOrder: 5 },
  { name: 'Science Club members', field: 'Club', comparison: 'equal', value1: 'Science Club', value2: '', displayOrder: 6 },
  { name: 'Blended learners', field: 'Learning Modality', comparison: 'equal', value1: 'Blended', value2: '', displayOrder: 7 },
  { name: 'Cyclists', field: 'Rides a bicycle to school', comparison: 'equal', value1: 'Yes', value2: '', displayOrder: 8 },
];

// --- form templates ---------------------------------------------------------
const formTemplates = [
  { fieldName: 'LRN', fieldType: 'NUMBER', isRequired: true, options: [] },
  { fieldName: 'Club', fieldType: 'DROPDOWN', isRequired: false, options: CLUBS },
  { fieldName: 'Learning Modality', fieldType: 'DROPDOWN', isRequired: false, options: ['Face-to-face', 'Blended', 'Modular'] },
  { fieldName: 'Rides a bicycle to school', fieldType: 'DROPDOWN', isRequired: false, options: ['Yes', 'No'] },
];

// --- message templates ------------------------------------------------------
const messageTemplates = [
  { name: 'Absence follow-up', text: 'Good day, {guardian}. This is to inform you that {first_name} was marked absent today. Kindly let us know if there is anything we should be aware of. Thank you.' },
  { name: 'Positive note home', text: 'Good day, {guardian}! I wanted to share that {first_name} did excellent work in class today. Thank you for your support at home.' },
  { name: 'Missing requirement', text: 'Good day, {guardian}. {first_name} has an outstanding requirement in Science. May we ask for your help in following this up? Thank you.' },
  { name: 'Parent conference invite', text: 'Good day, {guardian}. May we invite you for a short conference regarding {first_name}. Please let us know a convenient schedule this week.' },
  { name: 'Grade update', text: 'Good day, {guardian}. {first_name} currently has a grade of {grade} in Science for this quarter. Please feel free to reach out with any questions.' },
];

// --- participation ----------------------------------------------------------
// Deliberately uneven, which is the point of the equity view.
const participation = [];
for (const s of students) {
  const className = s.classes[0];
  const bias = rnd();
  const times = bias < 0.25 ? rint(0, 1) : bias < 0.7 ? rint(2, 5) : rint(6, 12);
  if (times === 0) continue;
  participation.push({
    studentIdentifier: idOf(s),
    className,
    timesCalled: times,
    lastCalled: dateOf(2026, 3, rint(2, 27)).getTime(),
  });
}

// --- assemble ---------------------------------------------------------------
const payload = {
  classrooms: CLASSROOMS,
  students: students.map((s) => ({
    firstName: s.firstName,
    lastName: s.lastName,
    gender: s.gender,
    birthday: s.birthday,
    address: s.address,
    contactNumber: s.contactNumber,
    classRoom: s.classes[0],
    classNamesJson: s.classes,
    seatingJson: s.seating,
    guardiansJson: s.guardians,
    customDataJson: s.custom,
    behaviorIncidents: s.behavior,
  })),
  formTemplates,
  savedFilters,
  gradingTerms: TERMS,
  assessmentCategories: CATEGORIES,
  rubrics: RUBRICS,
  messageTemplates,
  participation,
  attendanceRecord,
  gradeBook,
};

fs.writeFileSync(process.argv[2], JSON.stringify(payload, null, 2) + '\n', 'utf8');

const logDays = attendanceRecord.reduce((sum, r) => sum + r.participants.reduce((n, p) => n + Object.keys(p.attendance).length, 0), 0);
console.log(`students        ${students.length}`);
console.log(`classrooms      ${CLASSROOMS.length}`);
console.log(`incidents       ${students.reduce((n, s) => n + s.behavior.length, 0)}`);
console.log(`assessments     ${gradeBook.length}`);
console.log(`scores          ${gradeBook.reduce((n, g) => n + g.grades.filter((x) => x.score !== '').length, 0)}`);
console.log(`attendance rows ${attendanceRecord.length} records / ${logDays} logs`);
console.log(`participation   ${participation.length}`);
