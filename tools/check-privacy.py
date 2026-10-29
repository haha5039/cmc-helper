#!/usr/bin/env python3
"""Fail closed on private artifacts or personal environment literals; never echo matches."""
from pathlib import Path
import argparse,json,re,subprocess
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--working-tree',action='store_true',help='Inspect local files instead of the staged Git snapshot')
parser.add_argument('--directory',type=Path,help='Inspect an extracted source package')
parser.add_argument('--report-file',type=Path,help='Write local-only details; never upload the report')
args=parser.parse_args()
patterns={
 'personal_home':re.compile(r'/(?:Users|home)/[A-Za-z0-9_.-]+(?:/|\b)'),
 'windows_home':re.compile(r'[A-Za-z]:[\\/](?:Users|Documents and Settings)[\\/][^\\/\s]+'),
 'device_serial':re.compile(r'\bR\d[A-Z0-9]{8,14}\b'),
 'private_lan':re.compile(r'\b(?:192\.168\.\d+\.\d+|172\.(?:1[6-9]|2\d|3[01])\.\d+\.\d+)\b'),
 'machine_name':re.compile(r'\b[A-Za-z0-9][A-Za-z0-9-]{1,62}\.local\b'),
 'device_identifier':re.compile(r'\b(?:[0-9]{15}|(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2})\b'),
 'private_key':re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
 'token':re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{30,})\b'),
}
private_roots={'.local','evidence','reports','screenshots','captures','outputs','private'}
private_suffixes={'.apk','.idsig','.zip','.keystore','.jks','.p12','.pem','.png','.jpg','.jpeg','.webp','.log','.hprof','.dmp'}
failures=[]
if args.directory:
 entries=[(p.relative_to(args.directory).as_posix(),p.read_bytes()) for p in args.directory.rglob('*') if p.is_file() and '.git' not in p.parts]
else:
 paths=filter(None,subprocess.check_output(['git','ls-files','-z'],cwd=ROOT).decode().split('\0'))
 entries=[]
 for name in paths:
  path=ROOT/name
  if args.working_tree:
   if path.is_file():entries.append((name,path.read_bytes()))
  else:entries.append((name,subprocess.check_output(['git','show',':'+name],cwd=ROOT)))
for name,data in entries:
 path=Path(name)
 reasons=[]
 if path.parts[0] in private_roots or path.suffix.lower() in private_suffixes or path.name.startswith('.env'):
  reasons.append('private_artifact')
 if path.parts[0]=='docs' and ('verification' in path.name.lower() or path.suffix.lower() in {'.png','.jpg','.jpeg'}):reasons.append('runtime_record')
 try:text=data.decode('utf-8')
 except UnicodeDecodeError:text=''
 reasons.extend(key for key,pattern in patterns.items() if pattern.search(text))
 if 'LICENSE' not in path.name.upper():
  for address in re.findall(r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b',text):
   domain=address.rsplit('@',1)[1].lower()
   if domain not in {'users.noreply.github.com','noreply.github.com','example.com','example.org'} and not domain.endswith(('.example','.invalid')):
    reasons.append('personal_email');break
 if path.suffix.lower()=='.json':
  try:
   record=json.loads(text)
   if isinstance(record,dict) and set(record).intersection({'device','device_model','device_serial','serial','boot_id','runtime_checks','actual_helper_uid'}):reasons.append('runtime_record')
  except (ValueError,TypeError):pass
 if reasons:failures.append({'file':name,'rules':reasons})
if args.report_file:
 args.report_file.parent.mkdir(parents=True,exist_ok=True)
 args.report_file.write_text(json.dumps({'files':failures},indent=2)+'\n')
if failures:
 print('Privacy gate failed: '+str(len(failures))+' files. Offending content is not printed; request a local-only report for details.')
 raise SystemExit(1)
print('Privacy gate passed.')
