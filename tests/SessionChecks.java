package com.haha5039.cmc;

import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import rikka.shizuku.Shizuku;

/** Runs the actual ShizukuSession with deterministic callback/timer adapters. */
public final class SessionChecks {
    private static int passed;
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); passed++; }
    public static void main(String[] args) throws Exception {
        Handler.reset(); Shizuku.reset();
        try (HostContext context=new HostContext()) {
            ShizukuSession session=new ShizukuSession(context,() -> {});
            session.start(); check(!session.busy());
            session.authorize(); int first=Shizuku.lastRequest;
            check(first>=0 && session.busy());
            Shizuku.permission.onRequestPermissionResult(first,PackageManager.PERMISSION_DENIED); Handler.drain();
            check(!session.busy()); check(Shizuku.binds==0);
            Handler.advance(15000);
            session.authorize(); int second=Shizuku.lastRequest;
            check(second!=first && session.busy());
            Handler.advance(15000); // Old request's timeout must not cancel this request.
            check(session.busy());
            Shizuku.permission.onRequestPermissionResult(first,PackageManager.PERMISSION_GRANTED); Handler.drain();
            check(session.busy()); check(Shizuku.binds==0);
            Handler.advance(15000); check(!session.busy());
            Shizuku.permission.onRequestPermissionResult(second,PackageManager.PERMISSION_GRANTED); Handler.drain();
            check(Shizuku.binds==0); // Timed-out result cannot resurrect a stale operation.
            session.authorize(); int third=Shizuku.lastRequest; check(session.busy());
            Shizuku.alive=false; Shizuku.dead.onBinderDead(); Handler.drain();
            check(!session.busy()); check(!session.available());
            Shizuku.permission.onRequestPermissionResult(third,PackageManager.PERMISSION_GRANTED); Handler.drain();
            check(Shizuku.binds==0);
            Shizuku.alive=true; Shizuku.received.onBinderReceived(); Handler.drain();
            session.authorize(); int fourth=Shizuku.lastRequest;
            check(fourth!=third && session.busy());
            Shizuku.granted=true;
            Shizuku.permission.onRequestPermissionResult(fourth,PackageManager.PERMISSION_GRANTED); Handler.drain();
            check(Shizuku.binds==1); check(session.busy());
            ServiceConnection old=Shizuku.connection;
            Handler.advance(15000); check(!session.busy()); check(Shizuku.unbinds==1);
            session.authorize(); check(Shizuku.binds==2); check(session.busy());
            old.onServiceDisconnected(null); Handler.drain();
            check(session.busy()); // Stale connection callback cannot break the retry.
            Shizuku.granted=false; session.refresh();
            check(!session.busy()); check(!session.available()); check(Shizuku.unbinds==2);
        }
        Handler.reset(); Shizuku.reset();
        System.out.println("Shizuku session adapter checks passed: "+passed);
    }
}
