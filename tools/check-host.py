#!/usr/bin/env python3
"""Run pure-Java checks and production stores/sessions with host-only Android adapters."""
from pathlib import Path
import hashlib,json,os,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
JDK=Path(os.environ.get('JAVA_HOME') or os.environ["JAVA_HOME"])
cache=ROOT/'.deps'
cache.mkdir(exist_ok=True)
dependencies=[]
for spec in json.loads((ROOT/'tests/dependencies.json').read_text()):
    jar=cache/spec['file']
    if not jar.exists():
        subprocess.run(['curl','--fail','--location','--silent','--show-error','--max-time','60',spec['url'],'--output',str(jar)],check=True)
    if hashlib.sha256(jar.read_bytes()).hexdigest()!=spec['sha256']:
        raise RuntimeError('Host dependency digest mismatch: '+jar.name)
    dependencies.append(jar)
with tempfile.TemporaryDirectory(prefix='cmc-checks-') as directory:
    sources=[ROOT/'app/src/com/haha5039/cmc'/name for name in ['CommandSpec.java','BoundedCommand.java','OtpCalculator.java','RoleStore.java','ShizukuSession.java','CmcUserService.java']]
    sources += [ROOT/'app-preparation/com/haha5039/cmc/CmcOtp.java']
    sources += sorted((ROOT/'tests').rglob('*.java'))
    classpath=os.pathsep.join(map(str,dependencies))
    subprocess.run([str(JDK/'bin/javac'),'--release','8','-encoding','UTF-8','-cp',classpath,'-d',directory,*map(str,sources)],check=True)
    fixture=Path(directory)/'settings-state'
    fixture.mkdir()
    environment=os.environ.copy()
    environment['PATH']=str(ROOT/'tests/fixtures')+os.pathsep+environment.get('PATH','')
    environment['CMC_TEST_SETTINGS_DIR']=str(fixture)
    for name in ['OtpChecks','BridgeChecks','CalculatorChecks','StoreChecks','SessionChecks','EngineChecks']:
        subprocess.run([str(JDK/'bin/java'),'-cp',directory+os.pathsep+classpath,'com.haha5039.cmc.'+name],check=True,timeout=45,env=environment)
