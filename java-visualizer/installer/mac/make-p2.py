#!/usr/bin/env python3
"""מאגר p2 קטן (update site) לתוסף של Eclipse — בלי להריץ Eclipse.
ב-Mac ההתקנה (installer/mac/install.sh) לא כותבת בעצמה לתוך Eclipse.app (macOS חוסם: App Management),
אלא מבקשת מ-Eclipse עצמו להתקין את התוסף (p2 director) מהמאגר הזה. אותו פלט כמו של
org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher (נבדק מולו), לבאנדל אחד.

שימוש: make-p2.py <plugin.jar> <תיקיית המאגר> [qualifier]
הגרסה, השם והתלויות נקראים מה-MANIFEST.MF שבתוך ה-jar (Require-Bundle / Import-Package / BREE).
qualifier: Bundle-Version הופך ל-1.0.1.<qualifier> — כדי ש-p2 יזהה תוסף ששונה כגרסה חדשה (בלי זה "1.0.1" כבר
מותקן, ו-p2 לא מחליף אותו). auto = ה-qualifier שכבר ב-jar (v<מספר בנייה> מ-build-all.sh), ואם אין — לפי התוכן (c + 12 תווים של SHA-256 של הקבצים שבתוך ה-jar, בלי תאריכים ובלי
Bundle-Version; jar שבתוך jar — לפי התוכן שלו): אותו קוד = אותה גרסה, כך שעדכון שבו התוסף לא השתנה לא נוגע ב-Eclipse."""
import hashlib, io, os, re, shutil, sys, time, zipfile
from xml.sax.saxutils import quoteattr

jar, out = sys.argv[1], sys.argv[2]
qualifier = sys.argv[3] if len(sys.argv) > 3 else None


def manifest(path):
    raw = zipfile.ZipFile(path).read('META-INF/MANIFEST.MF').decode('utf-8').replace('\r\n', '\n')
    raw = re.sub(r'\n ', '', raw)   # שורות המשך
    m = {}
    for line in raw.split('\n'):
        if ':' in line:
            k, v = line.split(':', 1)
            m[k.strip()] = v.strip()
    return m


def clauses(value):
    """'a;x=1,b;y="[1,2)"' → [('a', {'x': '1'}), ('b', {'y': '[1,2)'})] — פסיקים בתוך מרכאות לא מפרידים"""
    res, cur, q = [], '', False
    for ch in value or '':
        if ch == '"':
            q = not q
        if ch == ',' and not q:
            res.append(cur)
            cur = ''
        else:
            cur += ch
    if cur.strip():
        res.append(cur)
    parsed = []
    for c in res:
        parts = [p.strip() for p in c.split(';')]
        attrs = {}
        for p in parts[1:]:
            mm = re.match(r'([\w.-]+)\s*:?=\s*"?([^"]*)"?', p)
            if mm:
                attrs[mm.group(1)] = mm.group(2)
        parsed.append((parts[0], attrs))
    return parsed


def content_hash(data):
    h = hashlib.sha256()
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for name in sorted(z.namelist()):
            if name.endswith('/'):
                continue
            d = z.read(name)
            if name == 'META-INF/MANIFEST.MF':
                d = re.sub(rb'(?m)^Bundle-Version:.*$', b'', d.replace(b'\r\n', b'\n'))
            elif name.lower().endswith(('.jar', '.zip')):
                try:
                    d = content_hash(d).encode()
                except zipfile.BadZipFile:
                    pass
            h.update(name.encode('utf-8') + b'\0' + hashlib.sha256(d).digest())
    return h.hexdigest()


m = manifest(jar)
if qualifier == 'auto':
    # ה-jar כבר עם qualifier של הבנייה (build-all.sh: 1.0.1.v<מספר בנייה>) — משאירים אותו: הוא עולה בכל בנייה, כך שהגרסה
    # החדשה תמיד גבוהה מכל עותק ישן (גם מ-1.0.1.c<hash> של התקנות קודמות: 'v' > 'c'). בלי qualifier — לפי התוכן, כמו קודם
    parts = m['Bundle-Version'].split('.')
    qualifier = parts[3] if len(parts) > 3 and parts[3] else 'c' + content_hash(open(jar, 'rb').read())[:12]

bsn = m['Bundle-SymbolicName'].split(';')[0].strip()
ver = m['Bundle-Version']
if qualifier:
    if not re.fullmatch(r'[\w-]+', qualifier):
        sys.exit('bad qualifier: ' + qualifier)
    ver = '.'.join(ver.split('.')[:3]) + '.' + qualifier
name = m.get('Bundle-Name', bsn)
vendor = m.get('Bundle-Vendor', '')

provides = [('org.eclipse.equinox.p2.iu', bsn, ver), ('osgi.bundle', bsn, ver)]
for pkg, a in clauses(m.get('Export-Package')):
    provides.append(('java.package', pkg, a.get('version', '0.0.0')))
requires = []
for b, a in clauses(m.get('Require-Bundle')):
    requires.append(('osgi.bundle', b, a.get('bundle-version', '0.0.0'), a.get('resolution') == 'optional'))
for p, a in clauses(m.get('Import-Package')):
    requires.append(('java.package', p, a.get('version', '0.0.0'), a.get('resolution') == 'optional'))
ee = re.match(r'JavaSE-([\d.]+)', m.get('Bundle-RequiredExecutionEnvironment', ''))

if os.path.isdir(out):
    shutil.rmtree(out)
os.makedirs(os.path.join(out, 'plugins'))
target = os.path.join(out, 'plugins', f'{bsn}_{ver}.jar')
if qualifier:
    # אותו jar, רק עם Bundle-Version חדש ב-MANIFEST.MF (שאר הקבצים — בדיוק כמו שהם)
    with zipfile.ZipFile(jar) as zin, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            data = zin.read(info.filename)
            if info.filename == 'META-INF/MANIFEST.MF':
                text = data.decode('utf-8').replace('\r\n', '\n')
                text = re.sub(r'(?m)^Bundle-Version:.*$', 'Bundle-Version: ' + ver, text)
                data = text.replace('\n', '\r\n').encode('utf-8')
            zout.writestr(info, data)
else:
    shutil.copyfile(jar, target)
data = open(target, 'rb').read()
stamp = str(int(time.time() * 1000))
A = quoteattr

req_xml = []
for ns, n, rng, opt in requires:
    req_xml.append(f"        <required namespace={A(ns)} name={A(n)} range={A(rng)}" + (" optional='true'" if opt else '') + '/>')
if ee:
    req_xml.append(f"""        <requiredProperties namespace='osgi.ee' match='(&amp;(osgi.ee=JavaSE)(version={ee.group(1)}))'>
          <filter>
            (!(org.eclipse.equinox.p2.disable.require.capability.osgi.ee=true))
          </filter>
          <description>
            {bsn}
          </description>
        </requiredProperties>""")
prov_xml = []
for ns, n, v in provides:
    prov_xml.append(f"        <provided namespace={A(ns)} name={A(n)} version={A(v)}/>")
prov_xml.append(f"""        <provided namespace='osgi.identity' name={A(bsn)} version={A(ver)}>
          <properties size='1'>
            <property name='type' value='osgi.bundle'/>
          </properties>
        </provided>""")
prov_xml.append("        <provided namespace='org.eclipse.equinox.p2.eclipse.type' name='bundle' version='1.0.0'/>")
props = [('org.eclipse.equinox.p2.name', name)] + ([('org.eclipse.equinox.p2.provider', vendor)] if vendor else [])

content = f"""<?xml version='1.0' encoding='UTF-8'?>
<?metadataRepository version='1.2.0'?>
<repository name='DSViz' type='org.eclipse.equinox.internal.p2.metadata.repository.LocalMetadataRepository' version='1'>
  <properties size='2'>
    <property name='p2.timestamp' value='{stamp}'/>
    <property name='p2.compressed' value='false'/>
  </properties>
  <units size='1'>
    <unit id={A(bsn)} version={A(ver)} generation='2'>
      <update id={A(bsn)} range={A('[0.0.0,' + ver + ')')} severity='0'/>
      <properties size='{len(props)}'>
{chr(10).join(f'        <property name={A(k)} value={A(v)}/>' for k, v in props)}
      </properties>
      <provides size='{len(prov_xml)}'>
{chr(10).join(prov_xml)}
      </provides>
      <requires size='{len(req_xml)}'>
{chr(10).join(req_xml)}
      </requires>
      <artifacts size='1'>
        <artifact classifier='osgi.bundle' id={A(bsn)} version={A(ver)}/>
      </artifacts>
      <touchpoint id='org.eclipse.equinox.p2.osgi' version='1.0.0'/>
      <touchpointData size='1'>
        <instructions size='1'>
          <instruction key='manifest'>
            Bundle-SymbolicName: {bsn};singleton:=true&#xA;Bundle-Version: {ver}&#xA;
          </instruction>
        </instructions>
      </touchpointData>
    </unit>
  </units>
</repository>
"""
artifacts = f"""<?xml version='1.0' encoding='UTF-8'?>
<?artifactRepository version='1.1.0'?>
<repository name='DSViz' type='org.eclipse.equinox.p2.artifact.repository.simpleRepository' version='1'>
  <properties size='2'>
    <property name='p2.timestamp' value='{stamp}'/>
    <property name='p2.compressed' value='false'/>
  </properties>
  <mappings size='3'>
    <rule filter='(&amp; (classifier=osgi.bundle))' output='${{repoUrl}}/plugins/${{id}}_${{version}}.jar'/>
    <rule filter='(&amp; (classifier=binary))' output='${{repoUrl}}/binary/${{id}}_${{version}}'/>
    <rule filter='(&amp; (classifier=org.eclipse.update.feature))' output='${{repoUrl}}/features/${{id}}_${{version}}.jar'/>
  </mappings>
  <artifacts size='1'>
    <artifact classifier='osgi.bundle' id={A(bsn)} version={A(ver)}>
      <properties size='4'>
        <property name='artifact.size' value='{len(data)}'/>
        <property name='download.size' value='{len(data)}'/>
        <property name='download.checksum.sha-512' value='{hashlib.sha512(data).hexdigest()}'/>
        <property name='download.checksum.sha-256' value='{hashlib.sha256(data).hexdigest()}'/>
      </properties>
    </artifact>
  </artifacts>
</repository>
"""
open(os.path.join(out, 'content.xml'), 'w', encoding='utf-8').write(content)
open(os.path.join(out, 'artifacts.xml'), 'w', encoding='utf-8').write(artifacts)
print(f'{out}: {bsn} {ver}')
