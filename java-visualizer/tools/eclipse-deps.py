#!/usr/bin/env python3
"""מוריד מ-Maven Central את ספריות Eclipse שנדרשות רק לקומפילציה של התוסף.
הגרסאות נבחרו כך שכולן קומפלו ל-Java 11 (class major 55), ולכן התוסף שנבנה מולן
עובד ב-Eclipse 2020 ואילך (ה-API שבשימוש יציב מאוד)."""
import os, sys, time, urllib.request

REPO = 'https://repo1.maven.org/maven2/'
PINNED = {
    'org.eclipse.platform': {
        'org.eclipse.ui.workbench': '3.128.0', 'org.eclipse.jface': '3.29.0',
        'org.eclipse.swt.gtk.linux.x86_64': '3.123.0', 'org.eclipse.core.runtime': '3.26.100',
        'org.eclipse.equinox.common': '3.18.200', 'org.eclipse.core.resources': '3.18.200',
        'org.eclipse.core.commands': '3.10.400', 'org.eclipse.core.jobs': '3.13.300',
        'org.eclipse.equinox.registry': '3.11.400', 'org.eclipse.osgi': '3.24.300',
        'org.eclipse.debug.core': '3.20.100', 'org.eclipse.debug.ui': '3.17.200',
        'org.eclipse.ui.ide': '3.20.100', 'org.eclipse.ui.workbench.texteditor': '3.16.700',
        'org.eclipse.text': '3.12.300', 'org.eclipse.jface.text': '3.23.0',
        'org.eclipse.core.filesystem': '1.9.500', 'org.eclipse.core.expressions': '3.8.200',
        'org.eclipse.equinox.preferences': '3.10.300',
    },
    'org.eclipse.jdt': {'org.eclipse.jdt.core': '3.33.0', 'org.eclipse.jdt.launching': '3.19.900'},
    'org.osgi': {'org.osgi.service.prefs': '1.1.2'},
}

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
for group, arts in PINNED.items():
    for a, v in arts.items():
        target = os.path.join(out, a + '.jar')
        if os.path.exists(target):
            continue
        url = f"{REPO}{group.replace('.', '/')}/{a}/{v}/{a}-{v}.jar"
        for attempt in range(5):
            try:
                with urllib.request.urlopen(url) as r:
                    open(target, 'wb').write(r.read())
                break
            except Exception as e:
                if attempt == 4:
                    sys.exit(f'download failed: {url}: {e}')
                time.sleep(2 ** attempt)
        print(a, v)
