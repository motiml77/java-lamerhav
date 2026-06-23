const { exec } = require('child_process');
const https = require('https');

const PROJECT_ID = 'exams-a93fb';

const GRADE_YA_KEYWORDS = ['משתנ', 'לולא', 'מחרוז', 'פונקצ', 'מתוד', 'מחלק', 'אובייקט', 'עצמים', 'מערכ'];
const gradeFor = (title) => GRADE_YA_KEYWORDS.some(k => title.includes(k)) ? 'יא' : 'יב';

const TOPIC_TITLES = [
  'משתנים, קלט ופלט',
  'תנאים',
  'לולאות',
  'מערכים',
  'מחרוזות',
  'מחלקות ואובייקטים',
  'רשימות מקושרות',
  'תור ומחסנית',
  'רקורסיה',
  'עצים בינאריים',
];

function getToken() {
  return new Promise((resolve, reject) => {
    exec('npx firebase-tools login:ci --no-localhost 2>&1 || true', { timeout: 5000 }, () => {});
    exec('node -e "require(\'firebase-tools\').login.ci().catch(()=>{})"', { timeout: 5000 }, () => {});
    // Use firebase internals to get token
    const configDir = process.env.APPDATA
      ? require('path').join(process.env.APPDATA, 'configstore')
      : require('path').join(require('os').homedir(), '.config', 'configstore');
    const fs = require('fs');

    // Try multiple config locations
    const paths = [
      require('path').join(configDir, 'firebase-tools.json'),
      require('path').join(require('os').homedir(), '.config', 'configstore', 'firebase-tools.json'),
    ];

    for (const p of paths) {
      try {
        const config = JSON.parse(fs.readFileSync(p, 'utf8'));
        if (config.tokens && config.tokens.refresh_token) {
          // Exchange refresh token for access token
          const postData = `grant_type=refresh_token&refresh_token=${encodeURIComponent(config.tokens.refresh_token)}&client_id=563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com&client_secret=j9iVZfS8kkCEFUPaAeJV0sAi`;
          const req = https.request({
            hostname: 'oauth2.googleapis.com',
            path: '/token',
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          }, (res) => {
            let data = '';
            res.on('data', c => data += c);
            res.on('end', () => {
              try {
                resolve(JSON.parse(data).access_token);
              } catch (e) { reject(new Error('Failed to parse token: ' + data)); }
            });
          });
          req.write(postData);
          req.end();
          return;
        }
      } catch (e) { continue; }
    }
    reject(new Error('No firebase token found'));
  });
}

function firestoreRequest(token, method, path, body) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'firestore.googleapis.com',
      path: `/v1/projects/${PROJECT_ID}/databases/(default)/documents/${path}`,
      method,
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    };
    const req = https.request(options, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, data: JSON.parse(data) }); }
        catch (e) { resolve({ status: res.statusCode, data }); }
      });
    });
    req.on('error', reject);
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

function toFirestoreValue(val) {
  if (val === null || val === undefined) return { nullValue: null };
  if (typeof val === 'string') return { stringValue: val };
  if (typeof val === 'number') return Number.isInteger(val) ? { integerValue: String(val) } : { doubleValue: val };
  if (typeof val === 'boolean') return { booleanValue: val };
  if (Array.isArray(val)) return { arrayValue: { values: val.map(toFirestoreValue) } };
  if (typeof val === 'object') {
    const fields = {};
    for (const [k, v] of Object.entries(val)) fields[k] = toFirestoreValue(v);
    return { mapValue: { fields } };
  }
  return { stringValue: String(val) };
}

async function main() {
  console.log('Getting Firebase auth token...');
  const token = await getToken();
  console.log('Token obtained.');

  // List existing classes
  console.log('\nChecking existing classes...');
  const existing = await firestoreRequest(token, 'GET', 'classes');
  const existingTitles = new Set();
  if (existing.data.documents) {
    for (const doc of existing.data.documents) {
      if (doc.fields && doc.fields.title) {
        existingTitles.add(doc.fields.title.stringValue);
      }
    }
  }
  console.log(`Found ${existingTitles.size} existing topics.`);

  let created = 0;
  for (let i = 0; i < TOPIC_TITLES.length; i++) {
    const title = TOPIC_TITLES[i];
    if (existingTitles.has(title)) {
      console.log(`  • "${title}" already exists — skipping`);
      continue;
    }

    const id = 'class_' + Date.now() + '_' + i;
    const docData = {
      id, title, icon: '📚',
      grade: gradeFor(title),
      order: i,
      archived: false,
      exams: [],
    };

    const fields = {};
    for (const [k, v] of Object.entries(docData)) fields[k] = toFirestoreValue(v);

    const result = await firestoreRequest(token, 'PATCH', `classes/${id}`, { fields });
    if (result.status === 200) {
      created++;
      console.log(`  ✓ Created "${title}" (grade ${gradeFor(title)})`);
    } else {
      console.error(`  ✗ Failed "${title}":`, result.data);
    }
  }

  console.log(`\n✔ Done: ${created} new topics created.`);
}

main().catch(e => { console.error('Error:', e.message); process.exit(1); });
