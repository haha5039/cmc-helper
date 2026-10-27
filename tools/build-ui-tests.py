#!/usr/bin/env python3
"""Build a separate instrumentation APK with the required common certificate."""
import argparse,os,shutil,tempfile,zipfile
from pathlib import Path
import build as app

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    env=os.environ.copy()
    env['JAVA_HOME']=str(app.JDK)
    env['PATH']=str(app.JDK/'bin')+os.pathsep+env.get('PATH','')
    with tempfile.TemporaryDirectory(prefix='cmc-ui-build-') as scratch:
        work=Path(scratch)
        classes=work/'classes';classes.mkdir()
        dex=work/'dex';dex.mkdir()
        resources=work/'resources.apk'
        app.run([app.BUILD_TOOLS/'aapt2','link','-o',resources,'-I',app.ANDROID_JAR,
                 '--manifest',app.ROOT/'tests/runtime/AndroidManifest.xml'],env)
        sources=sorted((app.ROOT/'tests/runtime').rglob('*.java'))
        app.run([app.JDK/'bin/javac','--release','8','-encoding','UTF-8','-cp',app.ANDROID_JAR,'-d',classes,*sources],env)
        jar=work/'classes.jar'
        with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as archive:
            for source in classes.rglob('*.class'): archive.write(source,source.relative_to(classes).as_posix())
        app.run([app.BUILD_TOOLS/'d8','--release','--min-api','33','--lib',app.ANDROID_JAR,'--output',dex,jar],env)
        unaligned=work/'unaligned.apk';shutil.copyfile(resources,unaligned)
        with zipfile.ZipFile(unaligned,'a',zipfile.ZIP_DEFLATED) as archive:
            for source in dex.glob('*.dex'): archive.write(source,source.name)
        unsigned=work/'aligned.apk'
        app.run([app.BUILD_TOOLS/'zipalign','-p','-f','4',unaligned,unsigned],env)
        signed=work/'signed.apk';app.sign_apk(unsigned,signed,env)
        args.output.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(signed,args.output)
        print('Test APK certificate verified: '+app.CERTIFICATE)
        print('Separate test APK: '+str(args.output.resolve()))

if __name__=='__main__': main()
