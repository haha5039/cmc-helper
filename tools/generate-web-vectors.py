#!/usr/bin/env python3
"""Regenerate deterministic browser parity vectors from the app's CmcOtp implementation."""
from pathlib import Path
import hashlib,json,os,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
JDK=Path(os.environ['JAVA_HOME']).expanduser()
native=ROOT/'app-preparation/com/haha5039/cmc/CmcOtp.java'
source='''import com.haha5039.cmc.CmcOtp;
import java.time.Instant;
import java.util.Random;
public final class WebOtpReference {
 public static void main(String[] args) {
  String[] dates={"2026-10-27T00:17:00Z","2026-10-27T00:17:59.999Z","2026-10-27T00:18:00Z","2026-12-31T23:59:00Z","2027-01-01T00:00:00Z","2028-02-29T23:59:59Z","2028-03-01T00:00:00Z","2026-01-01T00:00:00Z"};
  for(String date:dates) emit("1abcd",Instant.parse(date).toEpochMilli());
  Random random=new Random(5039);String alphabet="0123456789abcdefghijklmnopqrstuvwxyz";
  for(int i=0;i<128;i++) {
   StringBuilder key=new StringBuilder();for(int j=0;j<5;j++) key.append(alphabet.charAt(random.nextInt(36)));
   long time=Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()+Math.floorMod(random.nextLong(),126230400000L);
   emit(key.toString(),time);
  }
 }
 static void emit(String key,long time) {System.out.println(key+"|"+time+"|"+CmcOtp.response(key,time));}
}'''
with tempfile.TemporaryDirectory(prefix='cmc-web-vectors-') as scratch:
    path=Path(scratch);java=path/'WebOtpReference.java';java.write_text(source)
    subprocess.run([str(JDK/'bin/javac'),'--release','8','-d',scratch,str(native),str(java)],check=True)
    lines=subprocess.check_output([str(JDK/'bin/java'),'-cp',scratch,'WebOtpReference'],text=True).splitlines()
    vectors=[]
    for line in lines:
        key,epoch,code=line.split('|');vectors.append({'key':key,'epochMillis':int(epoch),'code':code})
    output={'source':'app-preparation/com/haha5039/cmc/CmcOtp.java','source_sha256':hashlib.sha256(native.read_bytes()).hexdigest(),'vectors':vectors}
    (ROOT/'tests/web-otp-vectors.json').write_text(json.dumps(output,indent=2)+'\n')
    print(f'Generated {len(vectors)} Java reference vectors')
