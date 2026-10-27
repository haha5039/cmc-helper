#!/usr/bin/env python3
"""Exercise pure-Java command boundaries, process failure and the CMC challenge."""
from pathlib import Path
import os,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
JDK=Path(os.environ.get('JAVA_HOME') or os.environ["JAVA_HOME"])
with tempfile.TemporaryDirectory(prefix='cmc-checks-') as directory:
    sources=[ROOT/'app/src/com/haha5039/cmc'/name for name in ['CommandSpec.java','BoundedCommand.java','OtpCalculator.java']]
    sources += [ROOT/'app-preparation/com/haha5039/cmc/CmcOtp.java',ROOT/'tests/OtpChecks.java',ROOT/'tests/BridgeChecks.java',ROOT/'tests/CalculatorChecks.java']
    subprocess.run([str(JDK/'bin/javac'),'--release','8','-encoding','UTF-8','-d',directory,*map(str,sources)],check=True)
    for name in ['OtpChecks','BridgeChecks','CalculatorChecks']:
        subprocess.run([str(JDK/'bin/java'),'-cp',directory,'com.haha5039.cmc.'+name],check=True)
